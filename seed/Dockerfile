FROM alpine:3.20
RUN apk add --no-cache bash curl jq ffmpeg
COPY seed.sh /seed.sh
RUN chmod +x /seed.sh
ENTRYPOINT ["/seed.sh"]
