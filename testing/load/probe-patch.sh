#!/usr/bin/env bash
# Finding of load run #1: under load the liveness probe (full /actuator/health incl. DB + Kafka checks, 1 s timeout)
# timed out on a CPU-throttled pod, so kubelet killed a healthy catalog-service and the gateway circuit breaker opened.
# This switches liveness/readiness to the dedicated Spring Boot probe groups and gives them 3 s.
#   bash testing/load/probe-patch.sh
set -euo pipefail
for d in api-gateway auth-service catalog-service; do
  kubectl patch deployment "$d" --type=strategic -p "$(cat <<EOF
{"spec":{"template":{"spec":{"containers":[{"name":"$d",
  "livenessProbe":{"httpGet":{"path":"/actuator/health/liveness","port":"http"},"timeoutSeconds":3,"periodSeconds":15,"failureThreshold":3},
  "readinessProbe":{"httpGet":{"path":"/actuator/health/readiness","port":"http"},"timeoutSeconds":3,"periodSeconds":10,"failureThreshold":3}
}]}}}}
EOF
)"
done
kubectl rollout status deployment/api-gateway deployment/auth-service deployment/catalog-service --timeout=15m
