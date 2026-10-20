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
