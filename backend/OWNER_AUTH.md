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

Set `OWNER_PASSWORD_HASHES` in the backend deployment secret manager to a comma-separated list of BCrypt hashes. The backend seeds them once only when no active Owner hashes exist. The source repository and APK must contain no plaintext Owner passwords or hashes. The three example strings previously shared in chat have been treated as compromised; choose fresh secrets before production and generate hashes locally using BCrypt. Never paste the plaintext values into GitHub.

Optional `OWNER_USER_ID` enables the second login field. If it is blank, leave User ID empty; password-only login remains supported.

## Important security notes

The neutral Owner entry is an Android UI concern; hiding it does not protect API endpoints. All Owner actions must require the server-issued Owner bearer token. Parent/Kids tokens must never be accepted for private APIs. Deploy only behind HTTPS, set a strong `TOKEN_PEPPER`, apply database migrations, and configure network-level rate limits and monitoring before production.
