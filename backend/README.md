# Ktor backend

Requires JDK 17+, PostgreSQL 14+, and real Razorpay credentials for paid flows.

## Database setup

Apply SQL migrations in filename order:
1. `db/migration/V001__initial_schema.sql`
2. `db/migration/V002__owner_auth.sql`
3. `db/migration/V003__seed_initial_owner_password_hashes.sql`
4. `db/migration/V004__pairing_otp_rate_limit.sql`
5. `db/migration/V005__dynamic_history_retention.sql`

The repository does not yet include an automatic migration runner. Apply migrations with your PostgreSQL migration process before starting the API. Add subscription plans explicitly; no plan prices have been invented. Amounts are in paise; duration units are MINUTES, HOURS, DAYS, MONTHS or YEARS; `capabilities` is a JSON array.

## Run

Set variables from `.env.example` in a secret manager and run `gradle run`. Generate a dedicated 32-byte AES key for history payload encryption (for example `openssl rand -base64 32`) and set it as `HISTORY_ENCRYPTION_KEY_BASE64`. Keep it in a KMS/secret manager, never in source control or the APK.

## Implemented API

- Device bootstrap for Parent and Kids; Kids bootstrap creates a 10-minute pairing OTP.
- Authenticated Kids OTP rotation.
- Parent OTP validation with 15-minute attempt throttling and 15-minute pairing sessions.
- Plan catalogue available only after a valid pairing session.
- Razorpay order creation on the server.
- Payment verification: signature validation plus independent Razorpay payment lookup; requires exact order ID, amount, currency and captured status.
- Permit issuance and device binding in a single PostgreSQL transaction after verified payment.
- Kids reconnect while its Permit/binding remains active.
- Parent linked-device and purchase-history endpoints.
- Owner password-only login with optional User ID, generic invalid-credential response, BCrypt hashes, short-lived sessions, rate limiting and password rotation.
- Encrypted Kids history ingestion for location/app usage/telemetry/media metadata; unviewed cloud records expire after 7 days.
- First Parent history read starts a non-resetting 24-hour expiry timer; a server-side scheduled purge runs every 60 seconds.
- Parent can permanently delete an individual history record; PDF-before-delete preference is stored per Parent device.

## History retention API

- `POST /v1/kids/history` — Kids device uploads one JSON history record; AES-256-GCM encrypted at rest. Payload limit: 1 MiB. Requires an active Permit/binding.
- `GET /v1/parent/history?kidsDeviceId=<uuid>` — authenticated Parent reads history; this first view starts the 24-hour timer for all currently visible records for that linked Kids device. The response includes `viewedAt` and authoritative `deleteAt` timestamps.
- `DELETE /v1/parent/history/{recordId}` — immediately deletes a record and records a minimal deletion audit entry.
- `GET /v1/parent/history/preferences`, `PUT /v1/parent/history/preferences` — reads/updates `savePdfBeforeDelete` preference.

Retention behavior: data not yet viewed is retained for at most 7 days from server receipt. The first successful history read sets a fixed 24-hour countdown; subsequent reads do not reset it. The backend deletes expired records every minute. Downloads/shares must not pause cloud expiry; they create separate Parent-controlled local copies. The Android app must clear its cache at the returned `deleteAt` and, when the PDF preference is enabled, immediately generate/save the PDF locally after viewing (do not wait until the last minute).

## Not yet production complete

- Razorpay webhook signature verification and idempotent event processing.
- Android application module, UI integration, device-side Keystore storage, Downloads/Share sheet/PDF generation and scheduled local-cache purge.
- Media binary/object-storage integration; current history API stores encrypted JSON payload/metadata, not large photo/video binary objects.
- Owner UI and authenticated Owner dashboard routes.
- Subscription-plan administration/seed data and payment live-credential setup.
- Automated database migration runner, integration tests against PostgreSQL/Razorpay sandbox, and full security review.

Never expose this service publicly until HTTPS, gateway webhook handling, deployment secret management, monitoring and end-to-end tests are complete. UPI intent return values, screenshots, local flags and manually entered UTRs are never proof of payment.
