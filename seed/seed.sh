#!/usr/bin/env bash
# Seeds demo data through the API gateway: 8 artists, 8 albums x 2 tracks with synthesized audio (ffmpeg, so
# royalty-free by construction) and a demo listener. Idempotent: does nothing when the catalog already has tracks.
set -euo pipefail
API="${API_URL:-http://api-gateway:8080}/api/v1"
PASSWORD="${SEED_PASSWORD:-Spotty-demo-1997}"
LISTENER_EMAIL="${DEMO_EMAIL:-demo@spotty.local}"

log() { echo "[seed] $*"; }

until curl -fsS "$API/genres" >/dev/null 2>&1; do log "waiting for the gateway..."; sleep 5; done


register_login() { # email name role -> access token
  curl -sS -o /dev/null -X POST "$API/auth/register" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg e "$1" --arg p "$PASSWORD" --arg n "$2" --arg r "$3" '{email:$e,password:$p,displayName:$n,role:$r}')" || true
  curl -fsS -X POST "$API/auth/login" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg e "$1" --arg p "$PASSWORD" '{email:$e,password:$p}')" | jq -r .accessToken
}

# artist | album | genre | track1 | track2 | frequency
DATA='Gary and the Mild Disappointments|Lukewarm Forever|indie-rock|Lukewarm Forever|Mildly Upset|220
Office Ballads Collective|My Printer Hates Me|folk|My Printer Hates Me|Paper Jam Serenade|262
Grade 4B|Recorder Solo (Live, Unrehearsed)|classical|Recorder Solo (Live, Unrehearsed)|Hot Cross Buns (Again)|330
DJ Tax Return|Deductible Bangers|electronic|Deductible Bangers|Form 1040 Drop|196
Polka Panic|Accordion Crimes|folk|Accordion Crimes|Squeezebox Regret|247
The Off-Key Nephews|Christmas in July|pop|Christmas in July|Jingle Sweat|294
Muzak Supreme|Elevator to Nowhere|ambient|Elevator to Nowhere|Floor Seven Forever|174
56k Heartbreak|Dial-Up Love Song|electronic|Dial-Up Love Song|Busy Signal Blues|440'

n=0
while IFS='|' read -r artist album genre t1 t2 freq; do
  n=$((n+1))
  if [ "$(curl -fsS -G "$API/tracks" --data-urlencode "q=$t1" -d size=1 | jq .page.totalElements)" -gt 0 ]; then log "skip $artist (already seeded)"; continue; fi
  slug=$(echo "$artist" | tr 'A-Z' 'a-z' | tr -c 'a-z0-9\n' '-' | sed 's/-*$//')
  token=$(register_login "$slug@spotty.local" "$artist" ARTIST)
  auth=(-H "Authorization: Bearer $token")
  log "artist $artist"
  album_id=$(curl -fsS -X POST "$API/albums" "${auth[@]}" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg t "$album" '{title:$t,type:"ALBUM",releaseDate:"2026-10-01"}')" | jq -r .id)
  i=0
  for title in "$t1" "$t2"; do
    i=$((i+1))
    track_id=$(curl -fsS -X POST "$API/tracks" "${auth[@]}" -H 'Content-Type: application/json' \
      -d "$(jq -nc --arg t "$title" --arg g "$genre" --argjson a "$album_id" --argjson n "$i" \
        '{title:$t,genreSlugs:[$g],albumId:$a,trackNumber:$n,explicit:false,releaseDate:"2026-10-01"}')" | jq -r .id)
    f=$((freq * i))
    ffmpeg -nostdin -loglevel error -y -f lavfi -i "sine=frequency=$f:duration=24" \
      -f lavfi -i "sine=frequency=$((f * 3 / 2)):duration=24" \
      -filter_complex "[0][1]amix=inputs=2,tremolo=f=$((3 + n)):d=0.6,volume=0.5" -b:a 128k "/tmp/t$track_id.mp3"
    curl -fsS -X POST "$API/media/$track_id/master" "${auth[@]}" -F "file=@/tmp/t$track_id.mp3;type=audio/mpeg" >/dev/null
    rm -f "/tmp/t$track_id.mp3"
    log "  track $track_id '$title' uploaded"
  done
done <<< "$DATA"

register_login "$LISTENER_EMAIL" "Demo Listener" LISTENER >/dev/null
log "demo listener: $LISTENER_EMAIL / $PASSWORD"
log "done (tracks become PUBLISHED when transcoding finishes)"
