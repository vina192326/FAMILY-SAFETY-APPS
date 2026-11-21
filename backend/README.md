# Ktor backend

Requires JDK 17+, PostgreSQL 14+, and real Razorpay credentials for paid flows.

## Database setup

Apply SQL migrations in filename order:
1. `db/migration/V001__initial_schema.sql`
2. `db/migration/V002__owner_auth.sql`
3. `db/migration/V003__seed_initial_owner_password_hashes.sql`
4. `db/migration/V004__pairing_otp_rate_limit.sql`

The repository does not yet include an automatic migration runner. Apply migrations with your PostgreSQL migration process before starting the API. Add subscription plans explicitly; no plan prices have been invented. Amounts are in paise; duration units are MINUTES, HOURS, DAYS, MONTHS or YEARS; `capabilities` is a JSON array.

## Run

Set variables from `.env.example` in a secret manager and run `gradle run`.

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

## Not yet production complete

- Razorpay webhook signature verification and idempotent event processing.
- Android application module, UI integration and device-side Keystore storage.
- Owner UI and authenticated Owner dashboard routes.
- Subscription-plan administration/seed data and payment live-credential setup.
- Automated database migration runner, integration tests against PostgreSQL/Razorpay sandbox, and full security review.

Never expose this service publicly until HTTPS, gateway webhook handling, deployment secret management, monitoring and end-to-end tests are complete. UPI intent return values, screenshots, local flags and manually entered UTRs are never proof of payment.
