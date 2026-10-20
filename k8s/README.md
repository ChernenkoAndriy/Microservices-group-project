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

The gateway is a NodePort service on port `30080` (Docker Desktop: http://localhost:30080).
Without a NodePort mapping (kind): `kubectl port-forward svc/api-gateway 8080:8080`.

Remove everything, data included: `kubectl delete -k k8s`.

## Monitoring

`kubectl apply -k k8s` also starts Prometheus (`k8s/monitoring/prometheus.yaml`). It finds pods with the
`prometheus.io/scrape: "true"` annotation and scrapes their `/actuator/prometheus`, one target per replica.

- UI: http://localhost:30090 (Docker Desktop), or `kubectl port-forward svc/prometheus 9090:9090`.
- Targets: Status -> Targets, job `spotty-services`; every service pod should be `UP`.
- Alert rules go into the `prometheus-rules` ConfigMap; Prometheus sends alerts to `alertmanager:9093`.
- After changing the config or rules: `kubectl apply -k k8s`, wait about a minute for the ConfigMap to update,
  then `curl -X POST http://localhost:30090/-/reload`.

Custom metrics of streaming-service: `spotty_outbox_pending_events` (Gauge), `spotty_transcoding_active_seconds`
(LongTaskTimer; set `TRANSCODING_SIMULATED_DELAY_MS`, e.g. 15000, to see a transcoding in progress) and
`spotty_upload_master_size_bytes` (DistributionSummary).
