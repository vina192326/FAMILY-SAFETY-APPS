# Ktor backend

Requires JDK 17+, PostgreSQL 14+, and real Razorpay credentials for paid flows.

1. Create the database and apply `db/migration/V001__initial_schema.sql`.
2. Supply all variables from `.env.example` through a secret manager.
3. Add active plans to `subscription_plans`. Amounts are in paise; duration units are MINUTES, HOURS, DAYS, MONTHS or YEARS; capabilities is a JSON array.
4. Run `gradle run` from this directory after the application source is added.

Payment confirmation must be server-to-server: validate the gateway signature and independently fetch the payment record, checking order, amount, currency and captured status before issuing a Permit. UPI intent return values, screenshots, local flags and manually entered UTRs are never proof.

Do not expose this service publicly until HTTPS, rate limiting, webhook signature verification/idempotency, Owner password+TOTP authentication, device credential rotation, integration tests and deployment secret management are implemented.
