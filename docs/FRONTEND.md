# Spotty frontend

React + TypeScript + Vite single-page app in `frontend/`. The browser only talks to one origin: `/api` is proxied
to the API gateway (Vite dev proxy locally, nginx in the cluster). Types come from `api-contracts/` via
`npm run gen:api` (openapi-typescript); no hand-written API types.

## Run it in the cluster (no Node needed)

```bash
docker build -t spotty-frontend:latest frontend
minikube image load spotty-frontend:latest
kubectl apply -k k8s-local            # includes k8s/services/frontend.yaml
kubectl port-forward svc/frontend 3000:8080
# open http://localhost:3000
```

Demo data (8 artists, 8 albums, synthesized audio, demo listener `demo@spotty.local` / `Spotty-demo-1997`):

```bash
docker build -t spotty-seed:latest seed
minikube image load spotty-seed:latest
kubectl apply -f k8s/services/seed-job.yaml
kubectl logs -f job/spotty-seed
```

## Run it locally with npm (development)

```bash
kubectl port-forward svc/api-gateway 8080:8080
cd frontend
cp .env.example .env
npm ci
npm run dev            # http://localhost:5173
```

Other scripts: `npm test` (Vitest), `npm run test:e2e` (Playwright, Chromium, needs the app and backend running),
`npm run gen:api` (regenerate API types), `npm run build`.

## Environment variables

| Variable | Where | Meaning | Default |
|---|---|---|---|
| `BACKEND_URL` | Vite dev/preview, nginx container | Where `/api` is proxied (API gateway) | `http://localhost:8080` (dev), `http://api-gateway:8080` (container) |
| `E2E_BASE_URL` | Playwright | App under test | `http://localhost:5173` |
