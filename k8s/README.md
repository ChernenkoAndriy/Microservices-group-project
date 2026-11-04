# Kubernetes

The manifests use the local images `auth-service:latest`, `catalog-service:latest`, `streaming-service:latest`,
`recommendation-service:latest`, `notification-service:latest`, `library-service:latest` and `api-gateway:latest`
(`imagePullPolicy: IfNotPresent`). `docker compose build` in the repository root builds all of them under these names.

## Run

```bash
# 1. Build the images
docker compose build

# 2. Only for kind or minikube: copy the images into the cluster (Docker Desktop's Kubernetes sees them already)
for i in auth-service catalog-service streaming-service recommendation-service notification-service library-service api-gateway; do
  kind load docker-image $i:latest          # or: minikube image load $i:latest
done

# 3. Secrets (k8s/base/secret.yaml is git-ignored)
cp k8s/base/secret.yaml.example k8s/base/secret.yaml

# 4. Everything at once: config, databases, Kafka, services
kubectl apply -k k8s
kubectl get pods -w
```

On a weaker machine use `kubectl apply -k k8s-local` instead: the same manifests with one replica per service.

The gateway is a NodePort service on port `30080` (Docker Desktop: http://localhost:30080).
Without a NodePort mapping (kind): `kubectl port-forward svc/api-gateway 8080:8080`.

Remove everything, data included: `kubectl delete -k k8s`.

## Monitoring

`kubectl apply -k k8s` also starts Prometheus (`k8s/monitoring/prometheus.yaml`). It finds pods with the
`prometheus.io/scrape: "true"` annotation and scrapes their `/actuator/prometheus`, one target per replica.

- UI: http://localhost:30090 (Docker Desktop), or `kubectl port-forward svc/prometheus 9090:9090`.
- Targets: Status -> Targets, job `spotty-services`; every service pod should be `UP`.
- Alert rules go into the `prometheus-rules` ConfigMap; Prometheus sends alerts to `alertmanager:9093`
  (`k8s/monitoring/alertmanager.yaml`), which POSTs them to `webhook-receiver`.
- After changing the config or rules: `kubectl apply -k k8s`, wait about a minute for the ConfigMap to update,
  then `curl -X POST http://localhost:30090/-/reload`.

Custom metrics of streaming-service: `spotty_outbox_pending_events` (Gauge), `spotty_transcoding_active_seconds`
(LongTaskTimer; set `TRANSCODING_SIMULATED_DELAY_MS`, e.g. 15000, to see a transcoding in progress) and
`spotty_upload_master_size_bytes` (DistributionSummary).
auth-service: `spotty_auth_logins_total{outcome="success|invalid_credentials|blocked"}` (Counter).
recommendation-service: `spotty_analytics_event_save_seconds` (Timer, ClickHouse write of a play event).

### Alerts

| Alert | Fires when | Severity |
|---|---|---|
| `ServiceDown` | a service seen in the last hour has no `UP` replica for 1 minute (also when scaled to 0) | critical |
| `OutboxBacklog` | `spotty_outbox_pending_events` stays above 0 for 2 minutes (Kafka unreachable) | warning |

- Prometheus: http://localhost:30090/alerts (inactive -> pending -> firing).
- Alertmanager UI: http://localhost:30093, or `kubectl port-forward svc/alertmanager 9093:9093`.
- Delivered notifications (firing and resolved): `kubectl logs -f deploy/webhook-receiver`.
- After changing `alertmanager-config`: `kubectl apply -k k8s`, wait for the ConfigMap, then
  `curl -X POST http://localhost:30093/-/reload`.

Test `ServiceDown`:

```bash
kubectl scale deploy/library-service --replicas=0
# about 1-2 minutes: the alert is firing in Prometheus and Alertmanager, webhook-receiver logs [FIRING]
kubectl scale deploy/library-service --replicas=2   # 1 with k8s-local
# webhook-receiver logs [RESOLVED]
```

Test `OutboxBacklog` (an outbox event is written when a transcoding finishes, so a master file has to be
uploaded while Kafka is down):

```bash
kubectl scale statefulset/kafka --replicas=0
# upload a master file as the track's artist: POST http://localhost:30080/api/v1/media/{trackId}/master
# spotty_outbox_pending_events > 0; after 2 minutes the alert fires, webhook-receiver logs [FIRING]
kubectl scale statefulset/kafka --replicas=1
# the relay sends the events, the gauge drops to 0 and webhook-receiver logs [RESOLVED]
```

## Security

### Network policies (`k8s/security/`)

`default-deny-ingress` blocks all incoming traffic to every pod in the namespace; the other policies open only what
is needed:

| Pod | Accepts traffic from |
|---|---|
| api-gateway | anyone on 8080 (Ingress controller, NodePort) |
| business services | api-gateway, prometheus; plus catalog -> auth:8081 and streaming -> catalog:8082 (internal APIs) |
| each database / media-storage | only its own service |
| kafka | auth, catalog, streaming, notification |
| otel-collector, tempo, loki, prometheus, alertmanager, grafana, webhook-receiver | the pods that use them, plus the UIs |

The services trust the `X-User-Id` / `X-User-Roles` headers, so without these policies any pod could call them
directly and act as any user.

NetworkPolicy needs a CNI that enforces it. Docker Desktop's Kubernetes accepts the manifests but blocks nothing.
Use `minikube start --cni=calico`, or kind with a kindnet that supports network policies (or Calico).

### Pod security

Every pod sets `seccompProfile: RuntimeDefault`, `allowPrivilegeEscalation: false`, drops all capabilities and
runs with `readOnlyRootFilesystem: true`; writable paths (`/tmp`, Postgres socket dir, Kafka config, ...) are
`emptyDir` volumes. All pods run as non-root (services 10001, Postgres 70, ClickHouse 101, Kafka and SeaweedFS 1000,
Prometheus/Alertmanager 65534, Grafana 472), except `otel-collector-logs`, which needs root to read the node's pod logs.
`automountServiceAccountToken: false` everywhere except Prometheus (pod discovery uses the Kubernetes API).

Postgres keeps its data in `pgdata/` inside the volume (`PGDATA`), because initdb running as non-root cannot change the
permissions of the volume root. A volume created by an older version of the manifests starts as an empty database;
remove it with `kubectl delete -k k8s` (deletes the PVCs) before applying.

### Ingress

`k8s/base/ingress.yaml` routes everything to `api-gateway:8080` (class `nginx`). minikube: `minikube addons enable ingress`,
then `curl http://$(minikube ip)/api/v1/tracks`.

### Checks

```bash
# the normal path works
curl http://$(minikube ip)/api/v1/tracks
# a database is unreachable from a foreign pod (times out)
kubectl run attacker --rm -it --image=postgres:17-alpine -- psql -h catalog-db -U spotty_catalog_user -c 'select 1'
# a service is unreachable around the gateway, even with forged identity headers (times out)
kubectl run attacker --rm -it --image=curlimages/curl -- \
  curl -m 5 -H "X-User-Id: 1" -H "X-User-Roles: ADMIN" http://auth-service:8081/api/v1/admin/users
# pods run as non-root on a read-only filesystem without a ServiceAccount token
kubectl exec deploy/catalog-service -- id
kubectl exec deploy/catalog-service -- touch /app/x          # Read-only file system
kubectl exec deploy/catalog-service -- ls /var/run/secrets/kubernetes.io   # No such file or directory
```
