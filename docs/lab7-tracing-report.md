# Лабораторна 7: трасування, логування, OpenTelemetry (спільний звіт)

> Чернетка спільного звіту. Розділи 1–4 описують трасування в сервісах (гілка `tracing-task`).
> Розділи 5–7 (колектор, сховища, Grafana, інцидент) дописують відповідальні за інфраструктуру та логи.

## 1. Наскрізний сценарій

**Артист завантажує трек.** Один трейс охоплює три сервіси й Kafka:

```
client
  └─ api-gateway            SERVER  POST /api/v1/media/{trackId}/master
       └─ streaming-service SERVER  POST /api/v1/media/{trackId}/master
            ├─ CLIENT  GET catalog-service /internal/tracks/{trackId}   (власник треку)
            │    └─ catalog-service SERVER GET /internal/tracks/{trackId}
            └─ (job QUEUED, traceparent збережено в transcoding_job)
       ┆ асинхронно, @Scheduled TranscodingWorker
       └─ streaming-service  transcode
            └─ complete-transcoding  (@Observed: job READY + outbox_event у одній транзакції)
       ┆ асинхронно, @Scheduled OutboxRelay
       └─ streaming-service  outbox send
            └─ PRODUCER streaming.asset.ready send
                 └─ catalog-service CONSUMER streaming.asset.ready receive
                      └─ apply-asset-ready (@Observed: трек PUBLISHED, processed_event)
```

Якщо catalog не може застосувати подію, record іде в `-retry-N` і `-dlt` з тим самим трейсом: кожна спроба видна як окремий спан-нащадок.

## 2. Як передається контекст (W3C Trace Context)

| Ділянка | Механізм |
|---|---|
| клієнт → gateway → сервіс | `spring-boot-starter-opentelemetry`: вхідний `traceparent` продовжується, gateway (`spring-cloud-gateway-server-webmvc`) додає свій спан і передає заголовок далі. |
| streaming → catalog (HTTP) | Клієнт `InternalTracksApi` зібраний з автоконфігурованого `RestClient.Builder` (`CatalogClientConfig`), тому кожен виклик отримує CLIENT-спан і заголовок `traceparent`. `RestClient.create()` цього не робить. Так само вже працює catalog → auth (`AuthClientConfig`). |
| HTTP-запит → воркер → relay | Дві ділянки виконуються в `@Scheduled`-потоках, де поточного спану немає. Контекст переноситься через БД: `TranscodingJob.traceparent` (записується в `TranscodingService.queue()`) і `OutboxEvent.traceparent` (записується в `OutboxWriter.append()` у тій самій транзакції, що й подія). `TraceContextCarrier` робить inject/extract через `Propagator`. |
| streaming → Kafka → catalog | `spring.kafka.template.observation-enabled: true` (PRODUCER-спан, `traceparent` у заголовках запису) і `spring.kafka.listener.observation-enabled: true` у catalog (CONSUMER-спан відновлює контекст із заголовка). |

Чому не просто `observation-enabled` для Kafka: подія відправляється relay'ем, а не HTTP-запитом, тож без збереження контексту в outbox Kafka-спан почав би новий трейс і не зв'язався б із запитом користувача. Це стандартний прийом «trace context in the outbox».

Порожні опитування воркера й relay (кожні 1–2 с) трейсів не створюють: `management.observations.enable.tasks.scheduled: false`.

## 3. Налаштування сервісів

Однаково в усіх 7 сервісах (`application.yaml`):

```yaml
management:
  tracing:
    sampling:
      probability: 1.0          # пишемо 100%, рішення приймає колектор (tail sampling)
  otlp:
    metrics:
      export:
        enabled: false          # метрики лишаються в Prometheus (scrape)
  observations:
    annotations:
      enabled: true             # @Observed
```

Експорт вмикається лише в кластері: маніфести задають
`MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces` (ключ `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` у `spotty-config`). Локально без колектора нічого не надсилається.

Метадані k8s (слайд 22): Downward API → `POD_NAME`, `POD_NAMESPACE`, `NODE_NAME` → `OTEL_RESOURCE_ATTRIBUTES=k8s.namespace.name=…,k8s.pod.name=…,k8s.node.name=…`.

Власні спостереження (`@Observed`, і метрика, і спан):
- `spotty.transcoding.complete` (`TranscodingService.complete`);
- `spotty.asset-ready.apply` (`AssetReadyHandler.handle`);
- спани `transcode` і `outbox send` (Tracer API, бо їм потрібен батько з БД), з атрибутами `track.id`, `transcoding.job.id`, `outbox.event.id`.

Номери треків і подій ідуть лише в атрибути спанів, а не в мітки метрик (висока кардинальність, слайд 16).

## 4. Тести

| Тест | Що перевіряє |
|---|---|
| `GatewayTracePropagationTest` (api-gateway) | вхідний `traceparent` клієнта доходить до сервісу з тим самим traceId і новим батьківським спаном (спан gateway). |
| `MediaUploadTraceTest` (streaming) | виклик у catalog несе `traceparent` того ж трейсу і `X-Internal-Token`; job зберігає `traceparent`; 403 для чужого треку, 404 для неіснуючого, 401 без користувача. |
| `OutboxTracePropagationTest` (streaming, Embedded Kafka) | upload-спан → job → воркер → outbox → Kafka-запис: у всіх той самий traceId, а батьківський спан на кожному кроці новий. |
| `AssetReadyTraceTest` (catalog, Embedded Kafka) | трек публікується в трейсі події; запис у DLT має `traceparent` того ж трейсу. |
| `AuthClientResilienceIntegrationTest$TraceHeaders` (catalog) | після переходу з Brave на OpenTelemetry `traceparent` так само доходить до auth-service. |

## 5. OpenTelemetry Collector, Tempo, Loki *(дописати)*
- маніфести `k8s/observability/`, конфіг колектора, політики tail sampling.

## 6. JSON-логи та Grafana *(дописати)*
- формат логів, поля `traceId`/`spanId`, перехід від спану до логів у Grafana.

## 7. Діагностичний цикл *(дописати)*
- інцидент (DLT або Kafka down → `OutboxBacklog`), скріншоти трейсу й логів, TraceQL/LogQL-запити.
