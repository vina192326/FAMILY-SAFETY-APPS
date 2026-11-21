# Ktor backend — Owner authentication baseline

Requires JDK 17+, PostgreSQL 14+, and the schema migrations under `db/migration`.

## Owner authentication

- `POST /v1/private/session` accepts `{"password":"..."}` with optional `userId`.
- A correct configured BCrypt password works without a User ID.
- If a User ID is supplied, it must match `OWNER_USER_ID`.
- Every failed credential combination returns exactly `Invalid User ID or Password.` with HTTP 401. The API does not distinguish which field failed.
- Successful authentication returns a 20-minute bearer session. Only its SHA-256 hash is stored in PostgreSQL.
- `POST /v1/private/password` requires the Owner bearer session plus current and new passwords. It stores a BCrypt hash (cost 12), retires all previously active password hashes, and revokes all sessions so the Owner must sign in again.
- Login attempts are rate-limited per remote IP (8 attempts per 15 minutes) and audited in PostgreSQL.

### Provisioning the initial Owner password set

Migration `V003__seed_initial_owner_password_hashes.sql` seeds the three requested initial credentials as BCrypt cost-12 hashes directly into the backend Owner password table. No plaintext password is stored in the APK or Kotlin source. These credentials were publicly disclosed, so rotate them from the private Owner Dashboard after initial access. The password-change endpoint replaces all active hashes and revokes all Owner sessions. For a fresh production deployment, replace the initial hash migration with hashes generated for new private passwords.

Optional `OWNER_USER_ID` enables the second login field. If it is blank, leave User ID empty; password-only login remains supported.

## Important security notes

The neutral Owner entry is an Android UI concern; hiding it does not protect API endpoints. All Owner actions must require the server-issued Owner bearer token. Parent/Kids tokens must never be accepted for private APIs. Deploy only behind HTTPS, set a strong `TOKEN_PEPPER`, apply database migrations, and configure network-level rate limits and monitoring before production.
