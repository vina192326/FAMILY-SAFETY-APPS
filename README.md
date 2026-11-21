# FAMILY SAFETY — One Android APK

The target product is one Android application with three separated experiences:
- **Parent Mode** — direct Parent Dashboard; no account login.
- **Kids Mode** — free to use, no subscription/payment; generates a temporary pairing OTP.
- **Private settings entry** — neutral public label; Owner Dashboard is shown only after server-side Owner authentication.

## Repository status

The backend foundation in `backend/` includes:
- Parent/Kids device bootstrap and device-scoped bearer tokens.
- Kids OTP creation/rotation; Parent OTP validation with attempt throttling.
- Pairing-session-gated subscription plan catalogue.
- Server-created Razorpay orders and server-side payment signature + payment-resource verification.
- Unique Permit issuance and device binding in one PostgreSQL transaction after captured payment.
- Kids reconnect for an existing valid Permit without another OTP.
- Parent linked-device and purchase history endpoints.
- Owner password-only authentication with optional User ID, generic invalid-credential message, BCrypt password hashes, rate-limited login, short-lived sessions and password rotation.
- Dynamic history retention foundation: offline retention window, immutable 24-hour timer after first Parent view, and Parent delete controls.

The single Android app module in `app/` now contains separate Parent and Kids screens. Parent mode has no-login device bootstrap, encrypted token storage, linked-device/history APIs, OTP validation → plan catalogue → Razorpay order creation, 24h/7d retention, PDF preference, Kids live telemetry viewer with 15-second polling and Maps deep link, 20-key permission status viewer, and PDF save/share. Kids mode has its own encrypted device token, six-digit OTP generation, one-button 20-step permission/capability wizard, Kids permission snapshot upload, manual sync, and explicit visible foreground live-location sharing that submits telemetry and LOCATION history; optional APP_USAGE history is sent only with Usage Access. Parent remains a viewer/controller; Kids is the data source.

## Parent Mode — remaining work

See [Parent Mode implementation status](docs/PARENT_MODE_IMPLEMENTATION_STATUS.md) for the detailed screen/control checklist. Major outstanding items include:
- Exact Stitch UI parity and full Child Profile CRUD.
- Complete Razorpay checkout UI → payment verification in Parent app → Permit refresh. (Order creation is wired; checkout/verification UI is not.)
- Real map/location/safe-place/geofence and Ring/Pause/Locate/Nudge/Check-In commands.
- Activity charts, app usage, screen-time limits, routines and pause-all controls.
- Live alert/SOS pipeline, push notifications, severity and quiet-hours controls.
- CSV export and local cache expiry. (PDF save and system Share Sheet are now implemented.)
- Persisted settings for guardian roles, timezone, routines, notifications, language/theme, permissions, updates, support, export, deletion and AI opt-out.
- Kids-side native permission rationale/request flows and consented telemetry upload. Parent permission status only displays snapshots submitted by Kids.
- Remaining backend APIs, database migrations V006–V008 (retention, permission status, live telemetry), push registration/event ingestion, automated tests and end-to-end integration. Apply V006–V008 before deploying the latest APIs.

## Build and release status

GitHub Actions is configured to assemble a debug APK and upload it as an artifact. A successful Android or backend build has **not yet been verified** after the latest changes. The app API base URL is configurable through `FAMILY_API_BASE_URL`, but currently falls back to a placeholder (`https://api.example.invalid`); no production HTTPS backend, release signing key, release APK or Play-ready package is claimed.

## Security invariants

1. Kids Mode remains free; no Kids login or purchase.
2. Every Kids installation needs its own Permit and its own successful purchase.
3. A Permit cannot be reused for another Kids installation.
4. Client-side payment success, UPI intent returns, screenshots and UTR strings are not proof of payment.
5. Owner credentials and backend secrets must never be embedded in the APK.

See `docs/FINAL_REQUIREMENTS.md`, `docs/PAIRING_PAYMENT_API.md` and `backend/README.md` for details.
\n\nKids Mode detail and permission matrix: `docs/KIDS_MODE_IMPLEMENTATION_STATUS.md`.\n