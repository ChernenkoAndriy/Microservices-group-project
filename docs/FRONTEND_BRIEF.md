# Spotty — frontend build instructions

You are building the FRONTEND for "Spotty", a parody music streaming service for
gloriously bad music. Read this entire prompt before doing anything.

=====================================================================
## 1. Workflow (two phases)
=====================================================================

PHASE 1 — Questions and plan:
- Read the `docs/` folder, all .md files in the repo, and the OpenAPI contracts.
- Ask me ALL your clarifying questions now, in one list. This is the only
  moment you may ask questions.
- Then give me a numbered step-by-step plan (no code). Include which endpoints
  exist vs. need stubs, and which external-facing gRPC services need a REST layer.
- Stop and wait. I will tell you which steps to implement.

PHASE 2 — Autonomous implementation:
- Implement the steps I approved WITHOUT asking any questions. I will be busy
  with other tasks.
- If something is unclear, choose the most reasonable option based on the docs
  and contracts, write the decision to `docs/DECISIONS.md` (what, why,
  alternatives), and continue.
- Test everything yourself: run the app, call the endpoints, check the frontend
  works against the real backend in the cluster. Fix what fails.
- When done, write a short summary to `docs/WORK_LOG.md`: what was done, what
  was tested, what is left, and any known issues.

=====================================================================
## Frontend tech
=====================================================================
- TypeScript + React + Vite. Single-page app with client-side routing, so music
  keeps playing while navigating.
- Persistent bottom player bar that never unmounts.
- HTML5 <audio> for playback. Use the backend's audio/stream endpoint if it
  exists; otherwise stub it (see section 5) with royalty-free placeholder audio.
- Fully responsive (desktop + mobile).
- Put the frontend in a `frontend/` folder in the repo root.

=====================================================================
## Visual design — VAPORWAVE
=====================================================================
Style: pastel, ironic, 90s shopping mall / early internet aesthetic. It must look
polished and intentional, not messy. Decorations live mainly in the header;
the content area stays clean and readable.

### Color palette (use ONLY these — no default blue, no green)
- Background: #FCE4F2 (pale pink)
- Cards / surfaces: #FFFFFF
- Primary accent: #B967FF (purple) — play button, active track, active nav, links
- Secondary accent: #01CDFE (cyan) — progress bar, hard offset shadows
- Highlight: #FF71CE (hot pink) — badges, hover states, "NOW SUFFERING" label
- Extra: #05FFA1 (mint) — small decorative details only
- Text primary: #3D2A5C (deep purple, never pure black)
- Text secondary: #8C7AA8
- Borders: 2px solid #3D2A5C
Define all colors as CSS variables so night mode just swaps them.

### Night mode (toggle in header)
- Background #1A1030, surfaces #2A1B4A, text #F5F0FF, secondary text #B5A6D6,
  borders #8F7BB8. Accents stay the same.

### Typography (Google Fonts)
- "VT323": logo, section headings, timestamps, numbers, window titles.
- "Rubik" (400 and 500): body, track names, buttons.
- Logo "Spotty": VT323, italic, white or purple, with hard text-shadow
  (3px 3px 0 #3D2A5C). Next to it small Japanese text "スポッティ".

### Signature details
- Cards: surface color, 2px deep-purple border, 4px radius, HARD offset shadow
  (4px 4px 0 #01CDFE). No blurry shadows anywhere.
- Buttons: same hard-shadow style; on hover move 2px down-right and shrink the
  shadow to 1px (feels like pressing a physical button).
- Header: gradient #FF71CE → #B967FF → #01CDFE, a white perspective grid at the
  bottom, a striped retro sun, and small floating shapes (wireframe pyramid,
  checkerboard tile) that drift slowly.
- Old-OS window frames: the hero panel looks like a 90s desktop window with a
  purple title bar "now_suffering.exe" and fake minimize/close buttons.
- Optional: a marble Greek bust (public-domain image from Wikimedia Commons) and
  palm tree silhouette in the header, low opacity, never over text.
- Very subtle scanline/VHS overlay over the whole page (opacity 3–5%).
- Album covers: pastel gradients, 4px radius, 2px border; lift slightly on hover.

### Layout (main page)
1. Header: logo, search bar ("Search for regrets…"), night mode toggle, avatar.
2. Left sidebar (becomes bottom nav on mobile): Home, Search, Your library,
   list of playlists.
3. Hero window "now_suffering.exe": large cover, "NOW SUFFERING:" label in pink
   VT323, track title in large VT323, artist, Pause/Play button,
   "Skip (recommended)" button.
4. "Bargain bin picks": grid of 4–6 album cards (cover, title, artist).
5. "Songs to cancel plans to": track list (number, title, artist, duration in
   VT323). Current track in purple with a 3-bar animated equalizer, bars
   slightly out of sync on purpose; it freezes when paused.
6. Persistent bottom player bar: cover thumbnail, title/artist, prev /
   play-pause / next, progress bar (cyan fill inside a bordered track, VT323
   timestamps), volume slider labeled "Bearable" left and "Why" right.
7. Footer: "© 1997 Spotty Corp. All rights regretted."

### Funny UI copy
- "Now suffering:" instead of "Now playing"
- Skip tooltip: "Skip (recommended)"; paused button: "Play anyway"
- Loading: "Finding something worse…"
- Empty playlist: "Your playlist is empty. Honestly, good choice."
- Like button tooltip: "You sure?"
- Error state: "ERROR 404: TASTE NOT FOUND"

### Placeholder data (only if the backend has no data yet)
- "Lukewarm Forever" — Gary and the Mild Disappointments
- "My Printer Hates Me" — Office Ballads Collective
- "Recorder Solo (Live, Unrehearsed)" — Grade 4B
- "Deductible Bangers" — DJ Tax Return
- "Accordion Crimes" — Polka Panic
- "Christmas in July" — The Off-Key Nephews
- "Elevator to Nowhere" — Muzak Supreme
- "Dial-Up Love Song" — 56k Heartbreak

### Interactions and accessibility
- Hover states on every clickable element.
- Keyboard: Space = play/pause, ← / → = seek 5s.
- All text meets WCAG AA contrast (4.5:1) in both day and night mode.
- Respect prefers-reduced-motion: disable floating/drifting animations.

=====================================================================
## Local development vs. container
=====================================================================
During development:
- Run the frontend locally with `npm run dev` (Vite) for fast rebuilds.
- Use the Vite dev-server proxy (or kubectl port-forward) to reach the backend
  in the cluster, so there are no CORS problems and no hardcoded URLs.
- Backend URL comes from an env variable, documented in `.env.example`.

At the end (after the frontend works locally):
- Create a production multi-stage `frontend/Dockerfile`:
  build stage (node, `npm ci`, `npm run build`) → small runtime stage
  (nginx:alpine) serving the static files.
- nginx config: SPA fallback to index.html, and proxy `/api` to the backend
  service inside the cluster (so the browser only talks to one origin).
- Add Kubernetes manifests for the frontend (Deployment + Service, and Ingress
  if the project already uses one) to BOTH `k8s-local/` and `k8/`, following the
  existing manifests' conventions. 1 replica, low resource requests/limits
  (e.g. 50m CPU / 64Mi RAM request).
- Build the image locally and load it into the local cluster (use the right
  method for this cluster, e.g. `kind load` / `minikube image load` /
  local registry). Use imagePullPolicy: IfNotPresent.
- Verify the frontend pod runs and works against the backend in the cluster.
- Teammates must be able to run the whole thing WITHOUT installing Node or npm.
  Write `docs/FRONTEND.md` with:
  - how to run it in the cluster (exact commands),
  - how to run it locally with npm for development,
  - environment variables.

=====================================================================
## Final deliverable: manual checks
=====================================================================
At the very end, create `docs/MANUAL_CHECKS.md`: a checklist (with [ ] boxes)
of everything I must verify by hand because you could not fully verify it
yourself. For each item: what to check, where/how (URL, click path), and
what the expected result is. Include at least:
- visual look matches the vaporwave design (day and night mode),
- audio actually plays, pauses, seeks, and keeps playing while navigating,
- mobile layout,
- keyboard shortcuts,
- every backend stub listed in docs/STUBS.md,
- every decision in docs/DECISIONS.md that I should confirm,
- anything that failed or was skipped during your testing.