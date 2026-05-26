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

Lokalno mozes drzati dev vrijednosti u ignoriranom `.env.dev` fileu. `scripts/run-dev.sh` prvo cita `.env.dev`, a ako ga nema pada na `.env`, postavi `JAVA_HOME` i `SPRING_PROFILES_ACTIVE=dev`:

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
5. Za IntelliJ najjednostavnije rucno dodaj env varijable iz `backend/.env.dev` u run configuration. Spring automatski cita `backend/.env`, dok `scripts/run-dev.sh` cita `.env.dev`.

Config import za `.env` podrzava oba najcesca working directoryja:

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
- `MINIO_ROOT_PASSWORD` i `APP_STORAGE_SECRET_KEY` na istu novu vrijednost
- `APP_STORAGE_PUBLIC_BASE_URL` ostavi praznim za standardni authenticated media flow

Storage za test deploy:

- Backend container treba interni endpoint `APP_STORAGE_ENDPOINT=http://minio:9000`.
- Uploaded event media se aplikaciji vraca kao authenticated backend URL oblika `/api/events/{eventId}/media/{mediaId}/content`; frontend ga ucitava s Bearer tokenom.
- Bucket `APP_STORAGE_BUCKET` treba biti private. Compose ima `minio-init` koji radi `mc anonymous set none local/$APP_STORAGE_BUCKET`; ako rucno mijenjas bucket ili MinIO credentials, provjeri policy ponovno:

```bash
podman compose -f deploy/test/compose.podman.yml run --rm --entrypoint /bin/sh minio-init -lc '
mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" &&
mc mb --ignore-existing "local/$APP_STORAGE_BUCKET" &&
mc anonymous set none "local/$APP_STORAGE_BUCKET" &&
mc anonymous get "local/$APP_STORAGE_BUCKET"
'
```

- Ne moras izlagati MinIO port `9000` direktno na hostu. Backend ga vidi preko compose mreze kao `minio:9000`, a mobitel slike vidi preko backend `/api/events/.../media/.../content` endpointa.
- Ako ipak imas Caddy `/media/*` route iz ranijeg public-bucket setupa, makni ga ili ga ostavi bez public bucket policyja. App ga vise ne treba.
- Za Cloudflare R2 kasnije mijenjas endpoint, region/account endpoint, bucket, access/secret key i po potrebi `APP_STORAGE_PATH_STYLE_ACCESS`; bucket i dalje moze ostati private jer backend streama media.

Pokretanje kroz Podman Compose:

```bash
cd backend
podman compose -f deploy/test/compose.podman.yml up -d --build
```

Caddy reverse-proxyja tvoju test API domenu na backend container i sam izdaje TLS certifikat. U DNS-u record za tu domenu treba pokazivati na javni IP Raspberry Pi-ja, a portovi `80` i `443` moraju biti dostupni prema Pi-ju. Media slike idu kroz isti backend API host i ne trebaju zaseban public MinIO route.

Provjera:

```bash
curl https://your-test-api.example.com/health
```

Ocekivani odgovor:

```json
{"status":"ok"}
```

Ako Google login u backend logu padne na dohvat `https://www.googleapis.com/oauth2/v3/certs`, to nije client ID problem nego DNS/egress problem backend containera. Provjeri na Pi-ju:

```bash
podman exec gik_backend_test getent hosts www.googleapis.com
podman exec gik_backend_test curl -I https://www.googleapis.com/oauth2/v3/certs
```

Ako prvi ili drugi poziv padne, popravi DNS/internet za Podman containere pa redeployaj backend. Backend za takav slucaj vraca `503` s porukom da ne moze dohvatiti Google token certificates.

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
- `AUTH_GOOGLE_CLIENT_IDS` (comma-separated Google OAuth audience vrijednosti; za trenutni native Google Sign-In obavezno stavi Web client ID iz frontend `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`, a ne Android client ID)
- `AUTH_APPLE_CLIENT_ID` (Apple token audience; za native iOS login ovo treba biti iOS bundle identifier)
- `PAYMENTS_STUB_ENABLED` (default `true`; Stripe-named provider radi kao lokalni stub bez vanjskog poziva)
- `STRIPE_PUBLISHABLE_KEY` (publishable key koji frontend moze dobiti u checkout responseu kad se ukljuci realni Stripe flow)
- `LOCATION_SEARCH_NOMINATIM_BASE_URL` (default `https://nominatim.openstreetmap.org`)
- `LOCATION_SEARCH_USER_AGENT` (default `GdjeIKadaBackend/1.0`; promijeni za produkcijski deployment)
- `APP_NOTIFICATIONS_PUSH_ENABLED` / property `app.notifications.push.enabled` (default `true`; iskljucuje remote push slanje bez gasenja preferenci/tokena)
- `APP_NOTIFICATIONS_EXPO_ENDPOINT` / property `app.notifications.expo.endpoint` (default `https://exp.host/--/api/v2/push/send`)
- `MESSAGES_ENCRYPTION_SECRET` / property `app.messages.encryption.secret` za AES-GCM encryption-at-rest novih text poruka; minimalno 32 znaka. Ako nije postavljen, lokalno se koristi `AUTH_JWT_SECRET` kao kompatibilni fallback.
- `APP_WEBSOCKET_ALLOWED_ORIGINS` / property `app.websocket.allowed-origins` za WebSocket origin allowlistu; default je `*` za native/local razvoj
- `APP_STORAGE_ENDPOINT`, `APP_STORAGE_BUCKET`, `APP_STORAGE_ACCESS_KEY`, `APP_STORAGE_SECRET_KEY` za S3-compatible image storage. Lokalno/testirano cilja MinIO, ali isti sloj radi za Cloudflare R2 endpoint. Uploaded media se standardno servira kroz authenticated backend endpoint; `APP_STORAGE_PUBLIC_BASE_URL` ostaje samo legacy/direct URL fallback i ne treba biti postavljen za test deploy. Default ukupna kvota je 10 GB (`APP_STORAGE_MAX_TOTAL_BYTES`), a upload slike je 5 MB (`APP_STORAGE_MAX_FILE_BYTES`).

## Local MinIO media storage

Za lokalni test storagea mozes pokrenuti MinIO:

```bash
docker run -d --name gik-minio \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=gikminio \
  -e MINIO_ROOT_PASSWORD=gikminio123 \
  quay.io/minio/minio server /data --console-address ":9001"
```

Napravi bucket `gik-event-media` u MinIO konzoli (`http://localhost:9001`) i postavi backend env:

```bash
export APP_STORAGE_ENDPOINT=http://localhost:9000
export APP_STORAGE_REGION=auto
export APP_STORAGE_BUCKET=gik-event-media
export APP_STORAGE_PUBLIC_BASE_URL=
export APP_STORAGE_ACCESS_KEY=gikminio
export APP_STORAGE_SECRET_KEY=gikminio123
export APP_STORAGE_PATH_STYLE_ACCESS=true
```

Kad se backend vrti u containeru, endpoint mora biti interni MinIO host. Bucket drzi private i pusti da backend provjerava event access prije streamanja slike.

U `dev` profilu backend, ako je baza prazna i postoji `../Gdje-I-Kada-Native/.local/issue-drafts/test-data/events.normalized.csv`, importira lokalne test evente. Ako je storage konfiguriran i lokalne slike postoje, seed ih upload-a u storage; inace koristi source image URL iz CSV-a. Originalni source page iz CSV-a sprema se u `events.source_url` i vraca kao `sourceUrl` u event DTO-u, bez lijepljenja linka u opis.

## API routes

- `GET /api/events`
- `GET /api/locations/search?query=&locale=&limit=&lat=&lng=`
- `GET /api/events/{id}`
- `POST /api/events`
- `POST /api/events` multipart `event` JSON + `images`
- `PATCH /api/events/{id}`
- `DELETE /api/events/{id}`
- `GET /api/events/{id}/participants`
- `POST /api/events/{id}/participants/{userId}/approve`
- `DELETE /api/events/{id}/participants/{userId}`
- `POST /api/events/{id}/participants/{userId}/block`
- `POST /api/events/{id}/media` JSON URL ili multipart `image`
- `GET /api/events/{id}/media/{mediaId}/content`
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
- `GET /api/users/me/feed-preferences`
- `POST /api/users/me/feed-preferences`
- `DELETE /api/users/me/feed-preferences/{preferenceId}`
- `GET /api/users/{userId}/events/upcoming`
- `PATCH /api/users/me/profile`
- `GET /api/users/me/activity`
- `GET /api/users/me/transactions`
- `GET /api/users/me/notifications/preferences`
- `PATCH /api/users/me/notifications/preferences`
- `POST /api/users/me/notifications/push-tokens`
- `DELETE /api/users/me/notifications/push-tokens?token=`
- `GET /api/feed?cursor=&limit=&seed=`
- `POST /api/feed/impressions`
- `GET /api/social/friends`
- `GET /api/social/events/{eventId}/share-recipients`
- `POST /api/social/friend-requests`
- `PATCH /api/social/friend-requests/{id}`
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

Napomena: `/api/events`, `/api/feed`, event details i authenticated media content koriste isti event access model. Public published eventi su vidljivi svima, a friends-only published eventi su vidljivi samo kreatoru i prihvacenim prijateljima kreatora. Korisnici bez tog accessa ne dobivaju ni event ni njegove media zapise.
Svi `/api/**` endpointi (osim javnih auth endpointa) traze `Authorization: Bearer <token>`.
`POST /api/events` prihvaca canonical single-language polja `title`, `where`, `about` i opcionalni `entryInstructions`; backend ih sprema u postojece HR/EN stupce. Stara `titleHr/titleEn`, `whereHr/whereEn`, `aboutHr/aboutEn` i `entryInstructionsHr/entryInstructionsEn` polja ostaju podrzana radi kompatibilnosti.
Event response DTO dodatno vraca `creatorName` i `creatorAvatarUrl` iz `app_users` kad creator postoji, kako map/feed/details klijenti mogu prikazati organizatora bez dodatnog profila requesta.
Event response DTO vraca `sourceUrl` za vanjske/importirane evente. Migracija `V18__event_external_source_url.sql` dodaje `events.source_url` i premjesta legacy `Izvor:`/`Source:` link iz opisa u zasebno polje.
Event response DTO vraca i `tags`, a `POST/PATCH /api/events` prihvaca do 5 tagova. `GET/POST/DELETE /api/users/me/feed-preferences` sprema FYP `Not interested` preference po eventu, kreatoru ili tagu; `/api/feed` i discovery liste ih filtriraju server-side. `/api/feed` prima `seed` za stabilan random redoslijed po ulasku u FYP, vraca samo buduce published evente dostupne korisniku i koristi `event_feed_impressions` kako bi eventi s vise prikaza bez interakcije pali nize u feedu. `POST /api/feed/impressions` povecava prikaze, a like/join/share/not-interested oznacavaju interakciju.
Owner-only event management endpointi dopustaju creatoru update/delete eventa, media URL management, multipart image upload, pregled/prihvacanje waitliste, micanje sudionika i blokiranje korisnika s neplacenog eventa. Image upload prihvaca JPG/PNG, najvise 5 slika po eventu, 5 MB po slici, minimalno 640x640 px za korisnicki upload i globalnu storage kvotu od 10 GB po backend konfiguraciji. Owner remove sprema participant status `rejected`, block dodatno sprema `event_blocks`, a backend zapisuje in-app `app_notifications` za approve/remove/block. Blokirani korisnici vise ne vide event kroz map/feed/list discovery i ne mogu ga ponovno joinati; profil/kalendar mogu prikazati status `blocked`.
`POST /api/events/{id}/join` odbija prosle evente i kad scheduler jos nije prebacio status u `finished`. `POST /api/events/{id}/ratings` sprema organizer rating za korisnike koji su bili `joined/approved` na prosli ili zavrseni event; frontend taj AVG organizer rating koristi u event details summaryju. `POST /api/events/{id}/ratings/full` ostaje kompatibilan endpoint za spremanje odvojenih event/organizer ocjena. Backend scheduler oznacava `published` evente kao `finished` jedan dan nakon `end_at/start_at/when_iso`.
`GET /api/locations/search` proxyja Nominatim/OpenStreetMap location autocomplete s limitom, localeom, opcionalnom proximity koordinatom i kratkim in-memory cacheom; koristi se u frontend create event address flowu.

`GET /api/social/friends` vraca prihvacene prijatelje trenutnog korisnika ukljucujuci `avatarUrl`. `GET /api/social/events/{eventId}/share-recipients` prvo provjerava da korisnik smije vidjeti event, zatim za public evente vraca korisnikove prijatelje, a za friends-only evente vraca samo korisnikove prijatelje koji su prijatelji kreatora eventa ili samog kreatora ako je korisniku prijatelj.

`GET /api/messages/chat-rooms` vraca samo sobe u kojima je trenutni korisnik clan kroz `chat_members`; legacy seed razgovori `c1/c2/c3` se brisu migracijom `V6__remove_legacy_mock_chats.sql`. Last-message preview za text poruke dekodira encrypted-at-rest zapis prije slanja DTO-a, pa chat lista prikazuje stvarni tekst umjesto generickog labela.
Chat room/member/message DTO-ovi vracaju `avatarUrl` iz `app_users.avatar_url`; direct roomovi dodatno vracaju `directUserId` za dohvat buducih eventova sugovornika. Direct chatovi ignoriraju `adminOnly` i oba korisnika mogu pisati. Chat room DTO vraca i `mutedByMe`, a `PATCH /api/messages/chat-rooms/{id}/notification-settings` sprema per-chat mute u `chat_notification_mutes`.
`POST /api/messages/chat-rooms/{id}/share-event` za friends-only evente dodatno provjerava sve clanove sobe: creator smije biti u sobi, a svaki drugi clan mora imati prihvacen friendship s kreatorom eventa. Ako uvjet nije ispunjen, endpoint vraca `403` i ne kreira event share poruku.
Google/Apple login verificira id token na backendu, provjerava issuer/audience/email verification, sprema provider `sub` u `user_social_identities` i tek tada izdaje nas JWT. Nove text poruke se spremaju encrypted-at-rest u `messages.encrypted_body` + `encryption_nonce` kroz AES-GCM; stari plaintext `body` ostaje fallback za postojece zapise. `POST /api/social/friend-requests` kreira friend request i ubacuje posebnu chat poruku u direct room, a `PATCH /api/social/friend-requests/{id}` prihvaca ili odbija request.

Poruke salju Expo push notifikacije nakon uspjesnog REST writea (`text`, `event_share`, `poll`). Primatelji se filtriraju server-side prema `user_notification_preferences`, `chat_notification_mutes`, clanstvu u sobi i aktivnim `user_push_tokens`; posiljatelj se nikad ne obavjestava. `user_push_tokens.locale` cuva HR/EN jezik uredaja za fallback tekst push poruke, Android payload ide na `messages` channel, a Expo `DeviceNotRegistered` odgovor automatski disablea taj token.

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
