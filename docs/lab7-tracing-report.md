# Лабораторна 7: трасування, логування, OpenTelemetry (спільний звіт)

> Розділи 1–4 описують трасування в сервісах, 5–6 — колектор, сховища, JSON-логи та Grafana,
> 7 — діагностичний цикл на прикладі інциденту, 8 — як відтворити демонстрацію.

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

Створення треку перед завантаженням (`POST /api/v1/tracks`) — окремий трейс api-gateway → catalog-service → auth-service (catalog бере профіль артиста через `AuthClientConfig`).

**Другий потік з Kafka: реєстрація → лист.** Реєстрація, блокування й розблокування користувача пишуть `AuthEmailEvent` в outbox auth-service у тій самій транзакції:

```
client
  └─ api-gateway             SERVER  POST /api/v1/auth/register
       └─ authentication-service SERVER POST /api/v1/auth/register
            └─ (outbox_events.traceparent = контекст запиту)
       ┆ асинхронно, @Scheduled OutboxPoller
       └─ authentication-service  outbox send
            └─ PRODUCER auth.user.email send
                 └─ notification-service CONSUMER auth.user.email receive
                      └─ NotificationLog + ProcessedEvent (або -retry-N / .DLT з тим самим трейсом)
```

## 2. Як передається контекст (W3C Trace Context)

| Ділянка | Механізм |
|---|---|
| клієнт → gateway → сервіс | `spring-boot-starter-opentelemetry`: вхідний `traceparent` продовжується, gateway (`spring-cloud-gateway-server-webmvc`) додає свій спан і передає заголовок далі. |
| streaming → catalog (HTTP) | Клієнт `InternalTracksApi` зібраний з автоконфігурованого `RestClient.Builder` (`CatalogClientConfig`), тому кожен виклик отримує CLIENT-спан і заголовок `traceparent`. `RestClient.create()` цього не робить. Так само вже працює catalog → auth (`AuthClientConfig`). |
| HTTP-запит → воркер → relay | Дві ділянки виконуються в `@Scheduled`-потоках, де поточного спану немає. Контекст переноситься через БД: `TranscodingJob.traceparent` (записується в `TranscodingService.queue()`) і `OutboxEvent.traceparent` (записується в `OutboxWriter.append()` у тій самій транзакції, що й подія). `TraceContextCarrier` робить inject/extract через `Propagator`. |
| streaming → Kafka → catalog | `spring.kafka.template.observation-enabled: true` (PRODUCER-спан, `traceparent` у заголовках запису) і `spring.kafka.listener.observation-enabled: true` у catalog (CONSUMER-спан відновлює контекст із заголовка). |
| auth → Kafka → notification | Той самий прийом: `OutboxEvent.traceparent` (записує `OutboxService.save()`), `OutboxPoller` надсилає подію в спані `outbox send`, що продовжує цей контекст (власний `TraceContextCarrier`). У auth увімкнено `spring.kafka.template.observation-enabled`, у notification — `listener` і `template` (щоб записи в `-retry-N`/`.DLT` теж несли трейс). |

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
| `OutboxTracePropagationTest` (auth, Embedded Kafka) | реєстрація в спані → рядок outbox має `traceparent` запиту → запис у `auth.user.email` несе `traceparent` того ж трейсу з новим батьківським спаном (спан poller'а). |
| `AuthEmailTraceTest` (notification, Embedded Kafka) | `NotificationLog` зберігається в трейсі події; запис у `.DLT` має `traceparent` того ж трейсу. |

## 5. OpenTelemetry Collector, Tempo, Loki

### 5.1 Архітектура

Усе розгортається з `k8s/observability/` (входить у `kubectl apply -k k8s` і `kubectl apply -k k8s-local`).

```
 7 сервісів (Spring Boot, OTel SDK)                      Grafana :3000 (NodePort 30030)
   │  спани: OTLP/HTTP :4318                              │  Tempo, Loki, Prometheus (provisioning)
   ▼                                                      │
 otel-collector (Deployment, 1 репліка) ── OTLP/gRPC ──▶ Tempo :4317 ◀── запити :3200
   memory_limiter → filter/actuator → tail_sampling → batch
   метрики колектора :8888 ◀── Prometheus

 stdout подів (JSON) → /var/log/pods/default_*/
   ▼
 otel-collector-logs (DaemonSet, filelog) ── OTLP/HTTP ──▶ Loki :3100/otlp ◀── запити :3100
```

Два окремі колектори, бо в них різні ролі: колектор трейсів має бути **один** (tail sampling вирішує долю трейсу лише тоді, коли всі його спани потрапили в той самий екземпляр; кілька реплік потребували б `loadbalancing`-експортера з маршрутизацією за trace id), а колектор логів має працювати **на кожному вузлі**, бо читає файли логів вузла.

| Компонент | Маніфест | Що важливо |
|---|---|---|
| otel-collector | `otel-collector.yaml` | приймає OTLP gRPC `:4317` і HTTP `:4318`, експортує в `tempo:4317`; `health_check` `:13133` для проб; власні метрики `:8888` (Prometheus скрейпить за анотацією). |
| otel-collector-logs | `otel-collector-logs.yaml` | `filelog` по `/var/log/pods/default_*`, без логів самого стеку спостережуваності; `start_at: end`, офсети в `file_storage`, тож перезапуск пода не надсилає логи вдруге. |
| Tempo | `tempo.yaml` | один бінарник, локальне сховище, `block_retention: 24h`, `max_block_duration: 5m`. Дані зникають разом із подом — достатньо для лабораторної. |
| Loki | `loki.yaml` | один бінарник, `tsdb` + `filesystem`, схема `v13`; `allow_structured_metadata: true` — без цього Loki не зберіг би `trace_id`/`span_id` як метадані запису. |
| Grafana | `grafana.yaml` | джерела даних провіжняться ConfigMap'ом: Tempo (за замовчуванням), Loki, Prometheus, зі зв'язками між ними (розділ 6.3). |

Сервіси надсилають спани лише в кластері: `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` = `http://otel-collector:4318/v1/traces` (з `spotty-config`), а `OTEL_RESOURCE_ATTRIBUTES` додає `k8s.namespace.name`, `k8s.pod.name`, `k8s.node.name`. Назва сервісу в Tempo — `spring.application.name`: `api-gateway`, `authentication-service`, `track-service` (catalog), `streaming-service`, `notification-service`, `library-service`, `recommendation-service`.

### 5.2 Конвеєр колектора трейсів

```yaml
receivers:  otlp (grpc 0.0.0.0:4317, http 0.0.0.0:4318)
processors: memory_limiter → filter/actuator → tail_sampling → batch
exporters:  otlp/tempo (tempo:4317, insecure), debug (verbosity: basic)
```

| Процесор | Навіщо |
|---|---|
| `memory_limiter` (80 % ліміту, сплеск 25 %) | першим у ланцюжку: при нестачі пам'яті колектор відмовляє в прийомі замість OOM. Tail sampling тримає трейси в пам'яті, тому це обов'язково. |
| `filter/actuator` | викидає спани `/actuator/**` (Prometheus опитує кожен под кожні 15 с, а k8s-проби ще частіше). Стоїть **до** семплінгу, щоб шум не займав буфер `num_traces`. |
| `tail_sampling` | рішення про трейс приймається після того, як зібрано його спани (див. 5.3). |
| `batch` (512 спанів / 2 с) | останнім, щоб пакувати вже відібрані спани. |

Сервіси пишуть 100 % трейсів (`management.tracing.sampling.probability: 1.0`) — інакше head-семплінг у сервісі відкинув би трейс раніше, ніж стало відомо, що в ньому помилка.

### 5.3 Tail-based sampling

```yaml
tail_sampling:
  decision_wait: 10s
  num_traces: 20000
  expected_new_traces_per_sec: 50
  decision_cache: { sampled_cache_size: 20000, non_sampled_cache_size: 20000 }
  policies:
    - { name: keep-errors, type: status_code, status_code: { status_codes: [ERROR] } }
    - { name: keep-slow,   type: latency,     latency: { threshold_ms: 1000 } }
    - { name: sample-ok,   type: probabilistic, probabilistic: { sampling_percentage: 25 } }
```

- Політики об'єднуються через **OR**: трейс зберігається, якщо спрацювала хоч одна. Тому **кожен трейс, де хоч один спан має статус ERROR, зберігається завжди** (`keep-errors`), незалежно від 25 %.
- `keep-slow` зберігає трейси довші за 1 с — саме вони цікаві при розборі затримок.
- `sample-ok` лишає чверть звичайних успішних трейсів, щоб бачити «нормальну» картину для порівняння.
- `decision_wait: 10s` — скільки колектор чекає від першого спану трейсу до рішення. Kafka-частина наших сценаріїв відбувається пізніше за HTTP-запит (воркер і relay опитують кожні 1–2 с, poller auth — кожні 5 с), тому вікно взято з запасом.
- `decision_cache`: спани, що приходять **після** рішення (наприклад, повторна спроба з `-retry-N` чи relay після відновлення Kafka), ідуть за вже прийнятим рішенням для цього trace id, а не утворюють «уламок» трейсу.
- Обмеження: рішення приймається за спанами, що надійшли за `decision_wait`. Якщо помилка стається пізніше (наприклад, перша невдала спроба `outbox send` через кілька секунд після запиту, коли воркер і relay вже відпрацювали), а до того трейс був коротким і успішним, він уже міг потрапити в 75 % відкинутих, і пізні спани з помилкою підуть за тим рішенням. Для демонстрації інциденту 7.1 транскодування штучно подовжується (`TRANSCODING_SIMULATED_DELAY_MS=3000`), тож трейс зберігається ще до помилки через `keep-slow`; у продакшні вікно `decision_wait` підбирають під найдовший асинхронний крок.

Перевірка: метрики колектора `otelcol_processor_tail_sampling_count_traces_sampled{policy=…, sampled=…}` і `otelcol_processor_tail_sampling_sampling_decision_latency` на `otel-collector:8888/metrics`; у логах колектора експортер `debug` друкує кількість відправлених у Tempo спанів.

## 6. JSON-логи та Grafana

### 6.1 Формат логів

Усі 7 сервісів пишуть у stdout по одному JSON-об'єкту на рядок — вбудоване структуроване логування Spring Boot у форматі Logstash:

```yaml
logging:
  structured:
    format:
      console: logstash
```

Значення MDC стають полями запису. Micrometer Tracing кладе в MDC `traceId` і `spanId` поточного спану, тож кожен рядок, записаний у межах запиту або обробки Kafka-повідомлення, містить їх:

```json
{"@timestamp":"2026-10-28T16:27:24.6475005+02:00","@version":"1",
 "message":"Transcoding job id=1 for track id=77 is ready, event id=72545f51-… is in the outbox",
 "logger_name":"com.epam.java.specialization.streamingservice.service.TranscodingService",
 "thread_name":"Test worker","level":"INFO","level_value":20000,
 "traceId":"432eff4840a6aa1b6fb9d6a3bdc47ce0","spanId":"6ca586da5e4fe156"}
```

`traceId` той самий, що в Tempo, у всіх сервісах трейсу: контекст передається через `traceparent`, а не генерується кожним сервісом окремо. Рядки поза трейсом (старт застосунку, внутрішні потоки Kafka) — теж JSON, просто без цих полів. У сервісах із gateway/auth/catalog у MDC є ще `correlationId`.

### 6.2 Шлях логу до Loki

Оператори `filelog`-ресівера в `otel-collector-logs`:

1. `container` — розбирає формат CRI, бере namespace, под і контейнер зі шляху файлу;
2. `json_parser` (лише якщо тіло починається з `{`) — поля JSON → атрибути запису;
3. `trace_parser` — `attributes.traceId` / `attributes.spanId` стають **trace context самого лог-запису** (OTLP `TraceId`/`SpanId`);
4. `severity_parser` — рівень з `level` (logstash) або `log.level` (ecs);
5. `move` — `message` → тіло запису;
6. `copy` — `k8s.container.name` → `service.name` (у Loki це мітка `service_name`).

Loki через OTLP-ендпоінт зберігає trace context запису як **structured metadata** `trace_id` і `span_id` (тому `allow_structured_metadata: true`). Це не мітки, тож висока кардинальність trace id не роздуває індекс.

### 6.3 Перехід спан ↔ логи в Grafana

Налаштовано в провіжнінгу джерел даних (`grafana.yaml`):

- **Tempo → Loki** (`tracesToLogsV2`): на кожному спані є кнопка **«Logs for this span»**, яка відкриває Loki із запитом
  `{k8s_namespace_name="default"} | trace_id="<traceId спану>"` у вікні часу спану ±5 хв. Один клік — і видно логи всіх сервісів цього трейсу.
- **Loki → Tempo** (`derivedFields` на `trace_id`): у розгорнутому записі логу є посилання **«View trace»**, яке відкриває трейс у Tempo.
- Tempo також пов'язаний із Prometheus (`tracesToMetrics`) і Loki (`lokiSearch`), увімкнено Node graph.

## 7. Діагностичний цикл

Цикл: **сигнал (алерт / помилка) → трейс-помилка в Tempo → логи цього трейсу в Loki → причина → виправлення → перевірка**. Нижче — два інциденти, які відтворюються на кластері (кроки — у розділі 8).

### 7.1 Kafka недоступна → `OutboxBacklog`

1. **Сигнал.** `kubectl scale statefulset/kafka --replicas=0`, потім артист завантажує трек. Транскодування завершується, подія `asset.ready` лягає в outbox, але relay не може її відправити. Через 2 хв `spotty_outbox_pending_events > 0` піднімає алерт `OutboxBacklog` у Prometheus/Alertmanager.
2. **Трейс.** Спан `outbox send` закінчується з помилкою (`span.error(...)` у `OutboxRelay`), тому трейс гарантовано зберігає політика `keep-errors`, хоча звичайні трейси семплуються на 25 %. Пошук у Tempo (TraceQL):
   ```
   { resource.service.name = "streaming-service" && name = "outbox send" && status = error }
   ```
   У трейсі видно весь шлях: запит через gateway, перевірку треку в catalog, `transcode`, і невдалі спроби `outbox send` з подією-винятком `KafkaException: Send failed`. Relay повторює спробу щосекунди, тож кожна спроба — окрема пара спанів `outbox send` + PRODUCER зі статусом ERROR у тому самому трейсі.
3. **Логи.** «Logs for this span» → у Loki рядки цього трейсу, зокрема
   `Could not send outbox event id=… (attempt N), will retry: …` від streaming-service. Причина — брокер недоступний: `kubectl get pods` показує, що `kafka-0` немає.
4. **Виправлення і перевірка.** `kubectl scale statefulset/kafka --replicas=1`. Relay відправляє подію, і **в тому самому трейсі** з'являються успішний `outbox send` → PRODUCER → CONSUMER у `track-service` → `apply-asset-ready` (бо `traceparent` зберігся в рядку outbox). Трек стає `PUBLISHED`, алерт переходить у resolved.

### 7.2 Синхронна залежність недоступна

`kubectl scale deploy/catalog-service --replicas=0` і повторне завантаження: CLIENT-спан streaming-service → catalog закінчується помилкою з'єднання, сервіс повертає 5xx, трейс зберігається через `keep-errors`. Пошук: `{ resource.service.name = "streaming-service" && kind = client && status = error }`; «Logs for this span» показує помилку виклику catalog з тим самим `traceId` у streaming-service і api-gateway.

### 7.3 Корисні запити

| Мета | Запит |
|---|---|
| усі трейси з помилками | TraceQL `{ status = error }` |
| трейси з Kafka-споживачем у catalog | TraceQL `{ resource.service.name = "track-service" && kind = consumer }` |
| ланцюжок auth → notification | TraceQL `{ resource.service.name = "notification-service" && kind = consumer }` |
| повільні трейси | TraceQL `{ duration > 1s }` |
| логи одного трейсу | LogQL `{k8s_namespace_name="default"} \| trace_id="<traceId>"` |
| помилки сервісу | LogQL `{service_name="streaming-service"} \| json \| level="ERROR"` |

## 8. Демонстрація

Кроки відтворення — наскрізний сценарій, JSON-логи, перехід спан → логи, інциденти 7.1/7.2.

Скріншоти (тека `screenshots/lab7/`):

| Файл | Що на ньому |
|---|---|
| `01-trace-upload-tree.png` | трейс завантаження: api-gateway, streaming-service, track-service, PRODUCER/CONSUMER Kafka в одному дереві |
| `02-trace-upload-node-graph.png` | Node graph того ж трейсу |
| `03-span-to-logs.png` | «Logs for this span» → логи всіх сервісів з одним `trace_id` |
| `04-json-logs.png` | `kubectl logs` — JSON-рядки з `traceId`/`spanId` |
| `05-trace-register-notification.png` | трейс реєстрації: auth → Kafka → notification |
| `06-alert-outbox-backlog.png` | алерт `OutboxBacklog` firing |
| `07-trace-outbox-error.png` | трейс із помилковими `outbox send` і успішною відправкою після відновлення Kafka |
| `08-logs-outbox-error.png` | логи цього трейсу в Loki |
| `09-tail-sampling-metrics.png` | метрики `otelcol_processor_tail_sampling_*` |
