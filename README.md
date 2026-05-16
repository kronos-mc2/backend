# Backend setup

## Prerequisites

- Java 25
- PostgreSQL (docker container `gik-pg` from your command)

## Run

Baza se pokrece sa:

```bash
docker run -d --name gik-pg \
  -e POSTGRES_PASSWORD=gik \
  -e POSTGRES_USER=gik \
  -e POSTGRES_DB=gik \
  -p 5432:5432 \
  postgres
```

Backend cita osjetljive vrijednosti iz env varijabli. Za lokalni run prije starta postavi barem:

```bash
export DB_PASSWORD=gik
export AUTH_JWT_SECRET=change-this-local-secret-to-at-least-32-bytes
```

Lokalno mozes drzati sve vrijednosti u ignoriranom `.env` fileu. Spring Boot ga ucitava automatski kad backend pokreces iz `backend/` foldera, a helper script samo dodatno postavi `JAVA_HOME` i `SPRING_PROFILES_ACTIVE=dev`:

```bash
bash scripts/run-dev.sh
```



```bash
export JAVA_HOME=$(/usr/libexec/java_home)
./mvnw spring-boot:run
```

Za dev profil (na prvom loadu, samo ako je `events` tablica prazna, ubaci mock evente):

```bash
export JAVA_HOME=$(/usr/libexec/java_home)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

U `dev` profilu backend logira svaki HTTP poziv (method, path, status, duration).

## IntelliJ run

Za pokretanje iz IntelliJ IDEA:

1. Otvori `backend` kao Maven/Spring Boot projekt ili napravi Spring Boot run configuration za `hr.kronos.backend.BackendApplication`.
2. Postavi JDK na Java 25.
3. Postavi `Working directory` na apsolutni `backend` folder, npr. `/Users/dgulic/Projects/KRONOS-GIK/backend`.
4. Postavi `Active profiles` na `dev` ili dodaj env varijablu `SPRING_PROFILES_ACTIVE=dev`.
5. Ne moras rucno dodavati `DB_PASSWORD`, `AUTH_JWT_SECRET` i ostale lokalne varijable ako postoji `backend/.env`, jer ga Spring ucitava preko `spring.config.import`.

Config import podrzava oba najcesca working directoryja:

- `backend/` -> cita `.env`
- parent folder `KRONOS-GIK/` -> cita `backend/.env`

Ako IntelliJ i dalje ne ucita `.env`, u run configurationu rucno dodaj env varijablu `AUTH_JWT_SECRET` ili provjeri da working directory nije neki treci folder.

## Tests

Pokretanje svih backend testova:

```bash
export JAVA_HOME=$(/usr/libexec/java_home)
./mvnw test
```

Trenutno su pokriveni:

- `EventService` business pravila oko pristupa private eventima i `joined` filtera
- `PasswordPolicy` validacija lozinke
- `JwtService` generiranje/parsiranje tokena i sigurnosne granice za secret/expiration

Repo ima i GitHub Actions workflow koji se vrti na svaki `push` i `pull_request` i pokrece `./mvnw test` na Java 25.

## Test deploy na Raspberry Pi

Test profil je namijenjen za javni test backend iza Caddyja na domeni koju postavis u svojem DNS-u.

Priprema env filea:

```bash
cd backend/deploy/test
cp backend.env.example backend.env
```

U `backend.env` obavezno promijeni:

- `POSTGRES_PASSWORD`
- `DB_PASSWORD` na istu vrijednost kao `POSTGRES_PASSWORD`
- `AUTH_JWT_SECRET` na dugi random secret

Pokretanje kroz Podman Compose:

```bash
cd backend
podman compose -f deploy/test/compose.podman.yml up -d --build
```

Caddy reverse-proxyja tvoju test API domenu na backend container i sam izdaje TLS certifikat. U DNS-u record za tu domenu treba pokazivati na javni IP Raspberry Pi-ja, a portovi `80` i `443` moraju biti dostupni prema Pi-ju.

Provjera:

```bash
curl https://your-test-api.example.com/health
```

Ocekivani odgovor:

```json
{"status":"ok"}
```

Default DB connection:

- `jdbc:postgresql://localhost:5432/gik`
- username: `gik`
- password: set through `DB_PASSWORD`

You can override with env vars:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `AUTH_JWT_SECRET`
- `AUTH_JWT_EXPIRATION_SECONDS` (default je 2592000 sekundi = 30 dana; backend odbija vece vrijednosti)
- `AUTH_GOOGLE_CLIENT_IDS` (comma-separated Google client IDs)
- `AUTH_APPLE_CLIENT_ID` (Apple token audience; for native iOS login this should be your iOS bundle identifier)
- `PAYMENTS_STUB_ENABLED` (default `true`; Stripe-named provider radi kao lokalni stub bez vanjskog poziva)
- `STRIPE_PUBLISHABLE_KEY` (publishable key koji frontend moze dobiti u checkout responseu kad se ukljuci realni Stripe flow)
- `LOCATION_SEARCH_NOMINATIM_BASE_URL` (default `https://nominatim.openstreetmap.org`)
- `LOCATION_SEARCH_USER_AGENT` (default `GdjeIKadaBackend/1.0`; promijeni za produkcijski deployment)
- `APP_NOTIFICATIONS_PUSH_ENABLED` / property `app.notifications.push.enabled` (default `true`; iskljucuje remote push slanje bez gasenja preferenci/tokena)
- `APP_NOTIFICATIONS_EXPO_ENDPOINT` / property `app.notifications.expo.endpoint` (default `https://exp.host/--/api/v2/push/send`)
- `APP_WEBSOCKET_ALLOWED_ORIGINS` / property `app.websocket.allowed-origins` za WebSocket origin allowlistu; default je `*` za native/local razvoj

## API routes

- `GET /api/events`
- `GET /api/locations/search?query=&locale=&limit=&lat=&lng=`
- `GET /api/events/{id}`
- `POST /api/events`
- `PATCH /api/events/{id}`
- `DELETE /api/events/{id}`
- `GET /api/events/{id}/participants`
- `POST /api/events/{id}/participants/{userId}/approve`
- `DELETE /api/events/{id}/participants/{userId}`
- `POST /api/events/{id}/participants/{userId}/block`
- `POST /api/events/{id}/media`
- `DELETE /api/events/{id}/media/{mediaId}`
- `POST /api/events/{eventId}/ticket-checkout`
- `POST /api/ticket-orders/{orderId}/confirm`
- `POST /api/events/{id}/join`
- `DELETE /api/events/{id}/join`
- `POST /api/events/{id}/ratings`
- `POST /api/events/{id}/ratings/full`
- `POST /api/events/{id}/like`
- `DELETE /api/events/{id}/like`
- `GET /api/users/me/events`
- `GET /api/users/me/liked-events`
- `GET /api/users/{userId}/events/upcoming`
- `PATCH /api/users/me/profile`
- `GET /api/users/me/activity`
- `GET /api/users/me/transactions`
- `GET /api/users/me/notifications/preferences`
- `PATCH /api/users/me/notifications/preferences`
- `POST /api/users/me/notifications/push-tokens`
- `DELETE /api/users/me/notifications/push-tokens?token=`
- `GET /api/feed?cursor=&limit=`
- `GET /api/social/friends`
- `GET /api/messages/chat-rooms?query=`
- `POST /api/messages/chat-rooms`
- `POST /api/messages/events/{eventId}/chat-room`
- `GET /api/messages/chat-rooms/{id}`
- `PATCH /api/messages/chat-rooms/{id}`
- `PATCH /api/messages/chat-rooms/{id}/notification-settings`
- `GET /api/messages/chat-rooms/{id}/messages`
- `POST /api/messages/chat-rooms/{id}/messages`
- `POST /api/messages/chat-rooms/{id}/share-event`
- `POST /api/messages/chat-rooms/{id}/polls`
- `POST /api/messages/polls/{id}/vote`
- `GET /api/messages/people?query=` (returns results only when `query` has at least 2 characters)
- `GET /api/messages/conversations` legacy adapter
- `POST /api/messages/conversations/{id}/share-event` legacy adapter
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/google`
- `POST /api/auth/apple`
- `GET /api/auth/me`

Napomena: `/api/events` i `/api/feed` vracaju samo evente gdje je `visibility = public`.
Svi `/api/**` endpointi (osim javnih auth endpointa) traze `Authorization: Bearer <token>`.
`POST /api/events` prihvaca canonical single-language polja `title`, `where`, `about` i opcionalni `entryInstructions`; backend ih sprema u postojece HR/EN stupce. Stara `titleHr/titleEn`, `whereHr/whereEn`, `aboutHr/aboutEn` i `entryInstructionsHr/entryInstructionsEn` polja ostaju podrzana radi kompatibilnosti.
Event response DTO dodatno vraca `creatorName` i `creatorAvatarUrl` iz `app_users` kad creator postoji, kako map/feed/details klijenti mogu prikazati organizatora bez dodatnog profila requesta.
Owner-only event management endpointi dopustaju creatoru update/delete eventa, media URL management, pregled/prihvacanje waitliste, micanje sudionika i blokiranje korisnika s neplacenog eventa. Owner remove sprema participant status `rejected`, block dodatno sprema `event_blocks`, a backend zapisuje in-app `app_notifications` za approve/remove/block. Blokirani korisnici vise ne vide event kroz map/feed/list discovery i ne mogu ga ponovno joinati; profil/kalendar mogu prikazati status `blocked`.
`POST /api/events/{id}/ratings/full` sprema odvojenu ocjenu/komentar za event u `event_ratings` i ocjenu/komentar za organizatora u `event_organizer_ratings`. Backend scheduler oznacava `published` evente kao `finished` jedan dan nakon `end_at/start_at/when_iso`.
`GET /api/locations/search` proxyja Nominatim/OpenStreetMap location autocomplete s limitom, localeom, opcionalnom proximity koordinatom i kratkim in-memory cacheom; koristi se u frontend create event address flowu.

`GET /api/messages/chat-rooms` vraca samo sobe u kojima je trenutni korisnik clan kroz `chat_members`; legacy seed razgovori `c1/c2/c3` se brisu migracijom `V6__remove_legacy_mock_chats.sql`.
Chat room/member/message DTO-ovi vracaju `avatarUrl` iz `app_users.avatar_url`; direct roomovi dodatno vracaju `directUserId` za dohvat buducih eventova sugovornika. Direct chatovi ignoriraju `adminOnly` i oba korisnika mogu pisati. Chat room DTO vraca i `mutedByMe`, a `PATCH /api/messages/chat-rooms/{id}/notification-settings` sprema per-chat mute u `chat_notification_mutes`.

Poruke salju Expo push notifikacije nakon uspjesnog REST writea (`text`, `event_share`, `poll`). Primatelji se filtriraju server-side prema `user_notification_preferences`, `chat_notification_mutes`, clanstvu u sobi i aktivnim `user_push_tokens`; posiljatelj se nikad ne obavjestava. Expo `DeviceNotRegistered` odgovor automatski disablea taj token.

`GET /api/messages/people?query=` vraca praznu listu za prazan ili prekratak query, tako da novi chat ne ucitava cijeli popis korisnika prije stvarne pretrage.

## Payments

Payment provider je definiran kao Stripe. Trenutni backend ima production-shaped stub: `PaymentProvider` i `StripePaymentProvider` kreiraju lokalni ticket checkout bez vanjskog Stripe poziva dok je `PAYMENTS_STUB_ENABLED=true`.

Paid join flow:

- `POST /api/events/{eventId}/ticket-checkout` provjerava da je event `paid`, kreira ili koristi `event_ticket_products`, zatim zapisuje `ticket_orders` i `payments`.
- `POST /api/ticket-orders/{orderId}/confirm` potvrdjuje stub payment, oznacava order/payment kao succeeded, zapisuje `transactions` red i tek tada pridruzuje korisnika eventu.
- Direktni `POST /api/events/{id}/join` za paid event vraca `402 Payment Required` ako korisnik nema uspjesan ticket order.

Za realni Stripe flow treba zamijeniti stub u `StripePaymentProvider` stvarnim PaymentIntent pozivima, dodati server secret konfiguraciju i frontend `@stripe/stripe-react-native` PaymentSheet.

## WebSocket routes

- `/ws/messages`

Chat WebSocket koristi isti JWT kao REST. Native klijent salje `Authorization: Bearer <token>` u handshakeu, a backend podrzava i `access_token` query parametar kao fallback za platforme koje ne mogu poslati header. Slanje poruka ostaje REST; WebSocket emitira realtime evente `message.created`, `poll.updated` i `room.updated` clanovima sobe.

## Flyway note

If you already had an older `V1` applied in the same DB and get checksum mismatch, run:

```bash
./mvnw flyway:repair
```

or recreate a fresh database/container.
