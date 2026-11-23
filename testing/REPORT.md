# Звіт про тестування: контрактні та навантажувальні тести

Дата запусків: 2026-11-20. Як усе запустити: [`README.md`](README.md). Англійська версія: [`REPORT.en.md`](REPORT.en.md).

| Завдання | Результат |
|---|---|
| Створити контрактний тест, що працює з двома різними сервісами | Pact JVM 4.7.7. Споживач (consumer) **catalog-service** (track-service), постачальник (provider) **auth-service**, взаємодія `GET /internal/users?ids=`: 2/2 тести споживача та 2/2 перевірки постачальника проходять |
| Перевірити, що зміна контракту з одного боку призводить до падіння тесту | Показано в обох напрямках: і зміна з боку постачальника, і зміна з боку споживача призводять до FAIL перевірки постачальника (`testing/contract/demo-breaking-change.sh`) |
| Створити та запустити 3 сценарії навантажувального тестування | k6 1.3.0 у minikube: **load** (дашборд артиста), **spike** (сплеск логінів), **stress** (ланцюжок catalog -> auth) |
| Створити звіт з даними запусків і результатів | Цей документ. Сирі дані: `testing/load/results/` та `testing/contract/results/` |

---

## 1. Контрактне тестування (Pact, consumer-driven contracts)

### 1.1 Що тестується

catalog-service показує ім'я артиста на треках і сторінках артистів. Він не зберігає користувачів, а отримує їх
з auth-service одним пакетним запитом (`ArtistDirectory` -> згенерований клієнт `InternalUsersApi` -> `GET /internal/users`).
Цей самий виклик навантажується і в тестах нижче.

```
catalog-service (споживач)                 pacts/catalog-service-auth-service.json              auth-service (постачальник)
AuthServiceContractTest  --записує-->  2 взаємодії, matchers, provider states  --відтворює-->  AuthServiceProviderContractTest
  реальний Spring-контекст:                                                                      реальний web-шар:
  AuthClientConfig (RestClient, X-Internal-Token,                                                InternalTokenFilter, SecurityConfig,
  JSON mapper застосунку з fail-on-unknown-properties),                                          InternalAuthController, GlobalExceptionHandler,
  ArtistDirectory (resilience4j)                                                                 InternalUserMapper, Jackson-конфіг застосунку
  -> Pact mock server на :18081                                                                   -> Spring7MockMvcTestTarget, @State підміняє БД
```

Взаємодії в pact-файлі:

| Взаємодія | Provider state | Запит | Очікувана відповідь (matchers) |
|---|---|---|---|
| пакетний пошук наявних користувачів | `users 7 (ARTIST) and 8 (LISTENER) exist` | `GET /internal/users?ids=7&ids=8`, заголовок `X-Internal-Token` (regex `.+`) | 200, `application/json`, масив з 2 об'єктів: `id` число, `email` / `displayName` рядок, `role` regex `LISTENER\|ARTIST\|ADMIN`, `status` regex `ACTIVE\|BLOCKED`, `createdAt` regex ISO-8601 |
| пакетний пошук невідомого користувача | `no user with id 999 exists` | `GET /internal/users?ids=999` | 200, `[]` (невідомі id пропускаються, а не 404) |

Тест споживача також перевіряє, що catalog-service робить із відповіддю: імена артистів розпізнано, `isArtist(7)` true,
`isArtist(8)` false, невідомий id показується як "Unknown artist", і `available == true` (без fallback). Retry у тесті
вимкнено, тож resilience4j не може приховати невідповідність.

Замість точних значень використано matchers. Постачальник може повертати інші імена, дати чи id, але не інші
типи, назви полів або значення enum.

### 1.2 Результат

| Тест | Команда | Результат |
|---|---|---|
| Споживач (catalog-service) | `cd track-service && ./gradlew contractTest` | 2 тести, 0 падінь. Записує `pacts/catalog-service-auth-service.json` |
| Постачальник (auth-service) | `cd auth-service && ./gradlew contractTest` | 2 взаємодії перевірено, 0 падінь |

Обидва тести також входять у звичайний `./gradlew test`: track-service 36/36, auth-service 107/107 (1 skipped, вимкнений раніше).

### 1.3 Зміна контракту з одного боку ламає тест

`testing/contract/demo-breaking-change.sh` вносить кожну зміну, запускає тести й повертає зміну назад.
Вивід (`testing/contract/results/demo-output.txt`):

```
== 0. Baseline
   consumer (catalog-service) test : PASSED
   provider (auth-service) verify  : PASSED
== 1. Provider renames the JSON field displayName -> display_name (auth-service internal-openapi.yaml)
   provider (auth-service) verify  : FAILED   <- expected FAILED
      | Actual map is missing the following keys: displayName
== 2. Consumer starts to require avatarUrl (catalog-service consumer test)
   consumer (catalog-service) test : PASSED   (the consumer itself is fine)
   provider (auth-service) verify  : FAILED   <- expected FAILED
      | Actual map is missing the following keys: avatarUrl
== 3. Restore and re-verify
   consumer (catalog-service) test : PASSED
   provider (auth-service) verify  : PASSED
```

- **Зміна з боку постачальника.** auth-service перейменовує JSON-поле `displayName` на `display_name`. Це типове
  «прибирання» до snake_case, а Java-геттер лишається `getDisplayName()`. auth-service і далі компілюється, а його власні
  юніт-тести проходять, бо працюють через Java-геттери. Лише Pact-перевірка помічає, що catalog-service у продакшні
  перестане показувати імена артистів.
- **Зміна з боку споживача.** catalog-service починає очікувати `avatarUrl` у кожному користувачі. Його власний тест
  зелений (mock повертає те, що йому сказали), але auth-service не повертає `avatarUrl` для користувачів без аватара
  (`non_null` inclusion), тож перевірка постачальника падає. Без pact-файлу це виявилося б лише після деплою.

У CI перевірка постачальника запускалася б на кожен PR auth-service, а pact-файл надходив би з Pact Broker
(`can-i-deploy`). Тут pact-файл передається через папку репозиторію `pacts/` (`@PactFolder("../pacts")`).

### 1.4 Вибір інструмента

- **Pact JVM 4.7.7.** Його модуль `spring7` підтримує Spring Boot 4.1 / Java 25, які використовує проєкт. Pact — це
  інструмент із лекції 10 (тест споживача `V4Pact` плюс перевірка постачальника `@Provider` / `@PactFolder`).
- **stubborn-contract** (продовження Spring Cloud Contract) розглядався. Він опублікований лише як
  `0.1.0-SNAPSHOT`, релізу ще немає, тому його не використано.

---

## 2. Навантажувальне тестування (k6)

### 2.1 Середовище

| | |
|---|---|
| Хост | Windows 11, 4 логічні CPU, 20 GB RAM; Docker Desktop (WSL2 обмежено до 14 GB у `%USERPROFILE%\.wslconfig`) |
| Кластер | minikube v1.39.0, docker driver, **4 CPU / 12 GB**, containerd, metrics-server |
| Розгорнуто | `kubectl apply -k k8s-local`: 1 репліка на сервіс. Под кожного сервісу: CPU request 100m / **limit 750m**, memory 384Mi / 768Mi |
| Масштабовано до 0 на час запусків | library, notification, recommendation, streaming сервіси, їхні БД, grafana, loki, tempo. Вони не лежать на тестованих шляхах, а на 4 CPU відбирали ресурс у сервісів під тестом |
| Генератор навантаження | `grafana/k6:1.3.0` як Kubernetes Job у тому ж кластері (`testing/load/k6-job.yaml`), CPU limit 1. Звертається до `http://api-gateway:8080`, як справжній клієнт: перевірка JWT, rate limiting, circuit breakers і bulkheads працюють |
| Що вимірювалося | підсумок k6 (`summary.txt`, `summary.json`), `kubectl top pods` кожні 15 с (`resources.txt`), згруповані WARN/ERROR логи gateway, catalog і auth та кількість рестартів (`service-warnings.txt`) |

Усі абсолютні числа отримано на кластері в ноутбуці: 3 JVM, PostgreSQL, Kafka і Prometheus ділять 4 CPU. Кожен
сервіс до того ж обмежено 0.75 CPU. Числа слід використовувати для порівняння сценаріїв і пошуку вузьких місць, а не як продакшн-потужність.

### 2.2 Сценарії

| # | Тип | Скрипт | Модель навантаження | Що перевіряє | Пороги (thresholds) |
|---|---|---|---|---|---|
| 1 | **Load** | `01-load-artist-dashboard.js` | закрита, `ramping-vus`: 0 -> 20 VU (30 с), тримати 2 хв, -> 0 (20 с); 1 с think time | Щоденний трафік: 20 артистів відкривають свій дашборд. Одна сторінка = 5 викликів: `GET /users/me`, `/genres`, `/artists/me/tracks`, `/tracks/{id}`, `/artists/{id}` (3 з них викликають catalog -> auth `/internal/users`) | p95 < 500 мс, p99 < 1 с, помилки < 1 %, checks > 99 %, p95 сторінки < 2 с |
| 2 | **Spike** | `02-spike-login.js` | відкрита, `ramping-arrival-rate`: 2/с (30 с) -> **60/с за 10 с**, тримати 40 с -> 2/с, відновлення 60 с | Сплеск логінів (BCrypt) з однієї IP. Rate limiter gateway (burst 50, 20/с на IP) має поглинути його відповідями 429 + `Retry-After`, auth-service не має повертати 5xx, а система має відновитися | 0 × 5xx, p95 успішних логінів < 1.5 с, rate limiter спрацював, кожна відповідь 200 або 429 |
| 3 | **Stress** | `03-stress-catalog-auth.js` | відкрита, `ramping-arrival-rate` сходинками: **10 / 20 / 35 / 50 / 75 / 100 req/s**, 15 с наростання + 45 с утримання кожна | Де ламається gateway -> catalog -> auth (`/internal/users`, той самий виклик, що в контрактному тесті)? Чи деградує система м'яко (catalog показує fallback "Unknown artist", bulkhead і circuit breaker gateway) чи падає? Запити розподілено між 30 користувачами, тож ліміт на користувача не є вузьким місцем | p95 < 800 мс, помилки < 5 %, fallback < 5 %. Навмисно суворі: таблиця по сходинках показує, де їх перетнуто |

Сценарії 2 і 3 використовують відкриту модель (arrival rate). k6 надсилає запити з цільовою частотою, навіть коли
відповіді сповільнюються, тож результати не спотворені coordinated omission (лекція 10). `dropped_iterations` рахує
запити, які k6 не зміг запустити, бо всі VU були зайняті.

### 2.3 Запуски

| Запуск | Час | Що запускалося | Навіщо |
|---|---|---|---|
| #1 | 12:44-12:50 | сценарій 1 | перший запуск, вихідні маніфести |
| #2 | 13:01-13:16 | сценарії 1, 2, 3 | після виправлення probes за результатами запуску #1 (див. 2.4) |
| #3 | 13:18-13:31 | сценарії 2, 3 | у скрипти додано розбивку по сходинках і фазах |
| #4 | 13:33-13:45 | сценарії 1, 2 | runner чекає на здорові сервіси + 60 с паузи; baseline spike ще 5/с |
| #5 | 13:55-14:09 | сценарії 2, 3 | фінальний spike з baseline 2/с (нижче виміряної пропускної здатності), фінальний stress |

Дані: `testing/load/results/run1-before-probe-fix`, `run2-after-probe-fix`, `run3-phased`, `run4-spike-baseline5`.
Останній запуск кожного сценарію лежить у `testing/load/results/<сценарій>/`.

### 2.4 Сценарій 1: load (дашборд артиста)

| Метрика | Запуск #1 (вихідні probes) | **Запуск #2 (виправлені probes)** | Запуск #4 (виправлені probes, зависання вузла) |
|---|---|---|---|
| Запити / частота | 2856 / 11.6 req/s | **2311 / 9.9 req/s** | 1856 / 8.5 req/s |
| Невдалі запити | **52.1 %** | **0.00 %** | 0.81 % |
| Checks пройдено | 44.3 % | **100 %** | 98.0 % |
| Затримка med / p95 / p99 | 521 / 2776 / - мс | **987 / 2470 / 3064 мс** | 864 / 4048 / 14067 мс |
| Max | 10052 мс | 7124 мс | 25979 мс |
| p95 сторінки (5 викликів) | > 2 с | 8.8 с | > 2 с |
| Пікове CPU gateway / catalog / auth | 603m / 599m / 521m | **636m / 746m / 745m** (limit 750m) | 537m / 494m / 231m |
| Рестарти подів під час запуску | catalog-service x1 | 0 | 0 |
| Пороги | 0/5 пройдено | помилки та checks PASS; затримка FAIL | помилки PASS; checks і затримка FAIL |

**Висновок 1 (запуск #1): liveness probe вбиває здоровий под під навантаженням.** Probes звертаються до повного
`/actuator/health` (перевірки БД + Kafka) з таймаутом 1 с. Під навантаженням catalog-service, обмежений по CPU,
відповідав на probe надто повільно. kubelet перезапустив його ("failed liveness probe, will be restarted"), і circuit
breaker gateway `catalog-service` відкрився: 382 × `CallNotPermittedException`, і 52 % запитів упали.
Виправлення (`testing/load/probe-patch.sh`): liveness -> `/actuator/health/liveness`, readiness -> `/actuator/health/readiness`
(групи probes Spring Boot, що не залежать від БД/Kafka), таймаут 3 с. Запуск #2 після цього: **0 помилок і 100 % checks**.
Рекомендація: перенести цю саму зміну в `k8s/services/*.yaml`.

**Висновок 2: затримку обмежує CPU limit пода.** У запуску #2 catalog-service і auth-service працюють на
745-746m зі свого ліміту 750m при всього ~10 req/s. Кожен запит гальмується CPU-квотою, тому p95 — 2.5 с замість < 500 мс.
Помилок при цьому 0: система повільна, але коректна. Допомогли б більше реплік або вищий CPU limit (HPA за CPU).
Вартість кожного запиту також збільшують 100 % семплювання трейсів і JSON-логування.

**Запуск #4: м'яка деградація.** Посеред запуску весь вузол minikube завис приблизно на 46 с. Hikari записав
`Thread starvation or clock leap detected (housekeeper delta=46s)`, і в той самий момент обірвалися з'єднання з Kafka.
Circuit breaker `auth-service` у catalog-service відкрився (CLOSED -> OPEN -> HALF_OPEN -> CLOSED у логах). Для 65
запитів catalog-service віддав "Unknown artist" замість помилки, тож частка помилок лишилася 0.81 %, і впав лише
check "artist name resolved". Це fallback resilience4j, який працює так, як задумано.

### 2.5 Сценарій 2: spike (сплеск логінів)

Фінальний запуск (#5), baseline 2/с, сплеск 60/с:

| Фаза | Запити | 200 | 429 | 5xx | Невдалі % | med | p95 | p99 |
|---|---|---|---|---|---|---|---|---|
| baseline (2/с) | 60 | 60 | 0 | 0 | **0.0 %** | 840 мс | 2742 мс | 3454 мс |
| spike (-> 60/с) | 2487 | 23 | **1493** | **971** | 39.0 % | 256 мс | 5277 мс | 13650 мс |
| recovery (2/с, 70 с) | 428 | 114 | 139 | 175 | 40.9 % | 206 мс | 1626 мс | 4876 мс |
| **разом** | 2985 (18.8 req/s) | | | | 38.4 % | 266 мс | 4305 мс | 13393 мс |

224 запити k6 не зміг запустити (dropped). Пороги: rate limiter спрацював PASS, 0 × 5xx FAIL, p95 успішних логінів FAIL, checks 87 % FAIL.
Пікове CPU auth-service 638m (limit 750m). Лог gateway: 816 × `Rate limit exceeded for ip`,
804 × `CircuitBreaker 'auth-service' is OPEN`, 503 × `HALF_OPEN`, 183 × I/O-помилки до auth-service.

**Висновок 3: rate limiter працює, але налаштований вище, ніж здатен обробити auth-service.**
- Один логін на незавантаженій системі триває **близько 300 мс** (виміряно curl у кластері, через gateway і напряму). Це
  BCrypt strength 10 плюс JWT і запис refresh-токена на поді з 0.75 CPU, що дає пропускну здатність приблизно
  **3 логіни/с на под**.
- Gateway пропускає **20/с на IP** (burst 50). Limiter відповів на 1493 запити коректним 429 + `Retry-After`,
  але ~20/с, які він пропустив, усе одно приблизно в 6 разів перевищували можливості auth-service. Запити ставали в чергу,
  і закінчився пул з'єднань БД: `HikariPool-1 - Connection is not available, request timed out after 30024ms (total=10, active=10, waiting=4)`.
  Причина в тому, що `AuthService.login()` позначено `@Transactional`, тож з'єднання з БД утримується під час
  CPU-важкої перевірки BCrypt. Далі виклики перевищували read timeout gateway (10 с) і поріг повільних викликів (5 с).
  Circuit breaker gateway відкрився й повертав fallback 503.
- Відновлення повільне: після кінця сплеску накопичена черга ще понад хвилину давала 41 % помилок.
- У запусках #3 і #4 baseline був 5/с. Це вже вище за пропускну здатність, тому впало 34 % (запуск #3) і 83 % (запуск #4)
  запитів навіть у *baseline*. Це підтверджує межу пропускної здатності; це не ефект сплеску.

Рекомендації: окремий, нижчий ліміт для `POST /api/v1/auth/login` (близько 2-3/с на IP), зберігши загальні 20/с для
інших маршрутів. Перевіряти пароль поза транзакцією БД, щоб з'єднання не утримувалося під час BCrypt. Запускати 2+ репліки
auth з HPA за CPU. BCrypt strength 10 лишити, але підібрати розмір подів під нього.

### 2.6 Сценарій 3: stress (gateway -> catalog -> auth)

Фінальний запуск (#5) по сходинках (`results/03-stress-catalog-auth/summary.txt`), поруч запуск #3 для порівняння:

| Сходинка | Запити (#5) | 2xx | 5xx | Невдалі % | med | p95 | p99 | Запуск #3: невдалі % / p95 |
|---|---|---|---|---|---|---|---|---|
| 10 req/s | 539 | 224 | 315 | 58.4 %* | 468 мс | 11947 мс | 19741 мс | **0.0 % / 1083 мс** |
| 20 req/s | 1125 | 1118 | 7 | **0.6 %** | 75 мс | **988 мс** | 1596 мс | 21.3 % / 8267 мс |
| 35 req/s | 1725 | 1291 | 434 | 25.2 % | 102 мс | 7769 мс | 13463 мс | 52.5 % / 7367 мс |
| 50 req/s | 2882 | 2003 | 879 | 30.5 % | 201 мс | 4332 мс | 9595 мс | 70.3 % / 24215 мс |
| 75 req/s | 3449 | 2256 | 1193 | 34.6 % | 1527 мс | 9724 мс | 12564 мс | 86.1 % (gateway перезапустився) |
| 100 req/s | 4757 | 1928 | 2829 | 59.5 % | 4008 мс | 11161 мс | 13899 мс | 100 % (помилки з'єднання) |
| **разом** | 15596 (35.3 req/s) | | | 38.6 % | 1327 мс | 9976 мс | 13485 мс | 60.7 % / 18302 мс |

\* Аномалія «прогріву»: сходинка 10 req/s запускалася одразу після фази setup (30 реєстрацій і логінів плюс 60 створень
треків), а в запуску #3 ця сама сходинка була чистою. Сходинка 20 req/s того ж запуску знову чиста.

Dropped-запити: 2242 у запуску #5, 9371 у запуску #3. Пікове CPU у запуску #5: gateway 644m, catalog 546m, auth 332m.
Пам'ять gateway досягла 605Mi з ліміту 768Mi. У запуску #5 рестартів подів не було.

**Висновок 4: точка відмови ланцюжка — близько 20-35 req/s на цьому кластері.** Обидва запуски чисті до 10-20 req/s
(p95 близько 1 с). Приблизно з 35 req/s частка помилок стрибає до 25-50 % і далі зростає з навантаженням. auth-service не є
вузьким місцем (332-508m, далеко від ліміту). Вузькі місця — catalog-service і gateway.

**Висновок 5: як система відмовляє.**
- *Запуск #3: каскадна відмова.* На 75 req/s усі потоки Tomcat у gateway чекали на catalog-service, тож
  сама liveness probe не встигала відповісти, навіть на `/actuator/health/liveness` з таймаутом 3 с. kubelet убив gateway
  (рестарт #3), і на останніх сходинках кожен запит отримував помилку з'єднання (`err`). Один повільний downstream-сервіс
  поклав точку входу. Рекомендація: винести actuator на окремий management-порт (`management.server.port`), щоб probes не
  чекали в тому самому пулі потоків, що й трафік користувачів, і збільшити `failureThreshold` liveness probe gateway.
- *Запуск #5: м'яка деградація.* Gateway вистояв. Його bulkhead для catalog-service (`max-concurrent-calls: 100`,
  `max-wait-duration: 0`) одразу відхиляв надлишок: 2903 × `BulkheadFullException`, а
  2782 × `CircuitBreaker 'catalog-service' is OPEN` перетворилися на швидкі відповіді 503. Лише 115 з приблизно 6000
  помилок gateway (близько 2 %) були повільними I/O-помилками до catalog-service.
  Власний fallback catalog-service для auth-service майже не знадобився: `artist_name_fallback` лишився нижче 5 % (PASS).
- Два попередження catalog-service `Error while extracting response for type List<UserSummaryDto>` з'явилися лише на
  найвищому навантаженні. Формат відповіді коректний: справжню відповідь перевірено через curl, і вона відповідає
  pact-файлу. Найімовірніша причина — read timeout клієнта auth (2 с, `auth-client.read-timeout`) закінчувався під час
  читання тіла відповіді на поді, якому бракувало CPU. Це не проблема контракту.

### 2.7 Підсумок висновків і рекомендацій

| # | Висновок | Докази | Рекомендація |
|---|---|---|---|
| 1 | Liveness/readiness probes на повному `/actuator/health` з таймаутом 1 с убивають здорові поди під навантаженням | запуск #1: 52 % помилок, рестарт catalog; запуск #2 після виправлення: 0 % | `/actuator/health/liveness` і `/readiness`, таймаут 3 с (`probe-patch.sh`); перенести в `k8s/services/*.yaml` |
| 2 | Затримку обмежує CPU limit 750m на под (CFS throttling) | запуск #2: catalog/auth на 745m при 10 req/s, p95 2.5 с | більше реплік / HPA, вищий ліміт; розглянути семплювання трейсів < 100 % |
| 3 | Пропускна здатність логіну — близько 3/с на под auth, а gateway пропускає 20/с на IP. З'єднання з БД утримується під час BCrypt | spike: 971 × 5xx, вичерпаний пул Hikari, повільне відновлення | окремий rate limit для логіну; BCrypt поза `@Transactional`; 2+ репліки auth |
| 4 | Точка відмови gateway -> catalog -> auth — близько 20-35 req/s на цьому кластері | таблиця stress по сходинках | масштабувати catalog-service і gateway (обидва впираються в CPU) |
| 5 | Actuator probes ділять пул потоків gateway, тож повільний downstream призвів до вбивства gateway (каскад) | запуск #3: рестарт gateway, 100 % помилок з'єднання | окремий management-порт для probes; bulkhead + circuit breaker (запуск #5) — правильний захист |
| + | Circuit breakers, bulkheads, rate limiter і fallback "Unknown artist" спрацювали, як задумано | запуск #4 (зависання вузла: 0.81 % помилок), запуск #5 (швидкий 503 замість падіння) | залишити їх і покрити алертами (стан circuit breaker, частка 429) |
