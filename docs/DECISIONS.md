# Decisions

Each entry: what was decided, why, and the alternatives considered. Entries marked **[confirm]** are listed in
`docs/MANUAL_CHECKS.md` for the owner to confirm.

## Phase 1 (agreed with the owner)

1. **Folder names differ from the brief.** Contracts live in `api-contracts/` (not `openapi/`); manifests in
   `k8s/` (not `k8/`) and `k8s-local/` (a git-ignored kustomize overlay over `../k8s`). The frontend manifest
   goes to `k8s/services/frontend.yaml` and is picked up by `k8s-local` automatically.
2. **Cluster:** minikube (docker driver), 4 CPUs, **8 GB** (owner's choice: WSL has 12 GB, leave room for builds
   and Playwright). Observability and monitoring pods are scaled to 0 while working and restored at the end.
3. **Branch:** `feature/frontend-vaporwave` from `grpc-task` (5 commits ahead of `main`).
4. **No Ingress** in the project: the frontend is a NodePort service (30300) plus `kubectl port-forward`.
5. **WCAG AA vs. palette:** brief colors stay for fills, borders, shadows, decorations and large VT323 text;
   small text in day mode uses darker shades of the accents (added as extra tokens). **[confirm]**
6. **Login:** Login/Register pages and an avatar menu; anonymous users can browse and search; no automatic
   demo login. A demo listener account is created by the seed Job.
7. **Backend scope:** every missing backend piece is implemented for real (owner: "implement everything for
   real", no teammate exclusions), including the optional pieces O1–O4. Stubs only where impossible.
8. **No migration tool:** the project creates schemas with Hibernate `ddl-auto: update` (additive) and
   ClickHouse `CREATE TABLE IF NOT EXISTS`; new tables follow that (owner: "we don't need migrations").
9. **Audio:** synthesized with ffmpeg (royalty-free by construction), uploaded by a seed Job.
10. **Decorations:** bust and palm drawn as inline SVG, no external image download. Covers are pastel gradients
    derived from the id when no `coverUrl` exists.
11. **Tests:** Vitest + Playwright (Chromium only, headless, 1 worker).
12. **CLAUDE.md** mentions `C:\Users\maksy\.wslconfig` / 12 GB; the actual file is `C:\Users\maksu\.wslconfig`
    (14 GB). Not changed; owner said to plan for 12 GB.

## Implementation
