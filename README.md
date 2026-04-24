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

## Test deploy na Raspberry Pi

Test profil je namijenjen za javni test backend iza Caddyja na domeni:

- `https://test-api-gik.nerizz.com`

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

Caddy reverse-proxyja `test-api-gik.nerizz.com` na backend container i sam izdaje TLS certifikat. U Cloudflareu DNS record za `test-api-gik.nerizz.com` treba pokazivati na javni IP Raspberry Pi-ja, a portovi `80` i `443` moraju biti dostupni prema Pi-ju.

Provjera:

```bash
curl https://test-api-gik.nerizz.com/health
```

Ocekivani odgovor:

```json
{"status":"ok"}
```

Default DB connection:

- `jdbc:postgresql://localhost:5432/gik`
- username: `gik`
- password: `gik`

You can override with env vars:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `AUTH_JWT_SECRET`
- `AUTH_JWT_EXPIRATION_SECONDS` (default je 2592000 sekundi = 30 dana; backend odbija vece vrijednosti)
- `AUTH_GOOGLE_CLIENT_IDS` (comma-separated Google client IDs)
- `AUTH_APPLE_CLIENT_ID` (Apple token audience; for native iOS login this should be your iOS bundle identifier)

## API routes

- `GET /api/events`
- `POST /api/events`
- `GET /api/feed`
- `GET /api/social/friends`
- `GET /api/messages/conversations`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/google`
- `POST /api/auth/apple`
- `GET /api/auth/me`

Napomena: `/api/events` i `/api/feed` vracaju samo evente gdje je `visibility = public`.
Svi `/api/**` endpointi (osim javnih auth endpointa) traze `Authorization: Bearer <token>`.

## Flyway note

If you already had an older `V1` applied in the same DB and get checksum mismatch, run:

```bash
./mvnw flyway:repair
```

or recreate a fresh database/container.
