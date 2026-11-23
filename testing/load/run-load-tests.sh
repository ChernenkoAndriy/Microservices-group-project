#!/usr/bin/env bash
# Runs the k6 scenarios as Jobs in the current kubectl context (minikube) and stores the results.
#
#   bash testing/load/run-load-tests.sh            # all three scenarios
#   bash testing/load/run-load-tests.sh 02-spike-login
#
# Results: testing/load/results/<scenario>/{k6.log,summary.txt,summary.json,resources.txt}
set -euo pipefail
cd "$(dirname "$0")"
HERE=$(pwd)

SCENARIOS=("$@")
if [ ${#SCENARIOS[@]} -eq 0 ]; then
  SCENARIOS=(01-load-artist-dashboard 02-spike-login 03-stress-catalog-auth)
fi

kubectl create configmap k6-scripts --from-file=scripts/ --dry-run=client -o yaml | kubectl apply -f - >/dev/null

for s in "${SCENARIOS[@]}"; do
  name=$(echo "$s" | tr '[:upper:]' '[:lower:]' | cut -c1-40)
  out="$HERE/results/$s"
  mkdir -p "$out"
  # Start every scenario on a healthy, idle system: all services available, then a short cool-down
  kubectl wait --for=condition=available --timeout=15m deploy/api-gateway deploy/auth-service deploy/catalog-service >/dev/null
  sleep "${COOLDOWN_SECONDS:-60}"
  restarts_before=$(kubectl get pods --no-headers | grep -E "^(api-gateway|auth-service|catalog-service)" | awk '{print $1"="$4}' | tr '\n' ' ')
  run_id="$(date +%s)"
  started=$(date +%s)
  echo "=== $s (run $run_id) started $(date '+%Y-%m-%d %H:%M:%S')"

  kubectl delete job "k6-$name" --ignore-not-found --wait=true >/dev/null
  sed "s/__NAME__/$name/; s/__SCRIPT__/$s.js/; s/__RUN_ID__/$run_id/" k6-job.yaml | kubectl apply -f - >/dev/null

  # Resource usage of the services while the test runs (metrics-server), one sample every 15 s
  : >"$out/resources.txt"
  (
    while true; do
      { echo "--- $(date '+%H:%M:%S')"; kubectl top pods --no-headers 2>/dev/null | grep -E "service|gateway" | sort; } >>"$out/resources.txt"
      sleep 15
    done
  ) &
  sampler=$!

  kubectl wait --for=condition=ready pod -l job-name="k6-$name" --timeout=180s >/dev/null || true
  kubectl logs -f "job/k6-$name" >"$out/k6.log" 2>&1 || true
  # The job is done when the log stream ends; wait for its final state
  kubectl wait --for=condition=complete --timeout=60s "job/k6-$name" >/dev/null 2>&1 \
    && status="all thresholds passed" || status="k6 exited non-zero (a threshold failed or the run aborted)"
  kill "$sampler" 2>/dev/null || true

  # Warnings/errors of the services during the run, grouped (the evidence for the report), and pod restarts
  since="$(( $(date +%s) - started + 30 ))s"
  : >"$out/service-warnings.txt"
  for app in api-gateway catalog-service auth-service; do
    echo "## $app" >>"$out/service-warnings.txt"
    for prev in "" "--previous"; do
      kubectl logs -l app="$app" $prev --since="$since" --tail=-1 2>/dev/null
    done | grep -E '"level":"(ERROR|WARN)"' \
      | sed -E 's/.*"message":"([^"]{0,160}).*/\1/; s/ip:[0-9.]+/ip:X/; s/user:[0-9]+/user:N/; s/[0-9a-f]{8}-[0-9a-f-]{27}/UUID/g; s/(GET|POST) \/api[^ ]*/\1 PATH/' \
      | sort | uniq -c | sort -rn | head -15 >>"$out/service-warnings.txt" || true
  done
  {
    echo "restarts before: $restarts_before"
    echo "restarts after : $(kubectl get pods --no-headers | grep -E "^(api-gateway|auth-service|catalog-service)" | awk '{print $1"="$4}' | tr '\n' ' ')"
  } >>"$out/service-warnings.txt"

  sed -n '/^==== /,/^###K6_SUMMARY_JSON###/p' "$out/k6.log" | grep -v '###K6_SUMMARY_JSON###' >"$out/summary.txt" || true
  sed -n '/^###K6_SUMMARY_JSON###/,/^###K6_SUMMARY_END###/p' "$out/k6.log" | sed '1d;$d' >"$out/summary.json" || true
  echo "$status" >>"$out/summary.txt"
  echo "    finished $(date '+%H:%M:%S'): $status"
  cat "$out/summary.txt"
  echo
done
