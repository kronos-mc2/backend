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

## API routes

- `GET /api/events`
- `GET /api/events/{id}`
- `POST /api/events`
- `POST /api/events/{id}/join`
- `DELETE /api/events/{id}/join`
- `POST /api/events/{id}/like`
- `DELETE /api/events/{id}/like`
- `GET /api/users/me/events`
- `GET /api/users/me/liked-events`
- `GET /api/feed?cursor=&limit=`
- `GET /api/social/friends`
- `GET /api/messages/conversations`
- `POST /api/messages/conversations/{id}/share-event`
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
