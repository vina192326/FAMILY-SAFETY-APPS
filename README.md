# FAMILY SAFETY — One Android APK

The target product is one Android application with three separated experiences:
- **Parent Mode** — direct Parent Dashboard; no account login.
- **Kids Mode** — free to use, no subscription/payment; generates a temporary pairing OTP.
- **Private settings entry** — neutral public label; Owner Dashboard is shown only after server-side Owner authentication.

## Repository status

The backend foundation is implemented in `backend/`:
- Parent/Kids device bootstrap and device-scoped bearer tokens.
- Kids OTP creation/rotation; Parent OTP validation with attempt throttling.
- Pairing-session-gated subscription plan catalogue.
- Server-created Razorpay orders and server-side payment signature + payment-resource verification.
- Unique Permit issuance and device binding in one PostgreSQL transaction after captured payment.
- Kids reconnect for an existing valid Permit without another OTP.
- Parent linked-device and purchase history endpoints.
- Owner password-only authentication with optional User ID, generic invalid-credential message, BCrypt password hashes, rate-limited login, short-lived sessions and password rotation.

Backend compilation has passed in GitHub Actions. See the workflow link in the repository Actions tab.

## Critical remaining work before an APK can be called complete

- Android application module and approved UI implementation are not yet in this repository.
- The approved Stitch design package has been recovered from the user's ChatGPT Library and audited in `docs/STITCH_DESIGN_AUDIT.md` (386 archive entries, 119 HTML artifacts, 110 PNG renders, 18 primary inventory screens). Exact Android UI implementation and screen-by-screen validation remain pending.
- Android Keystore integration, runtime permission flows, notification/accessibility/location handling (only if included in the approved design), and client API wiring.
- Owner UI, concealed neutral entry, dashboard and password-change screen.
- Razorpay webhook signature verification and idempotent event processing.
- Real PostgreSQL deployment, configured plan catalogue, live payment credentials, HTTPS and secret management.
- Automated migration runner, backend integration/security tests, emulator and physical Android device testing, release signing and APK artifact.

## Security invariants

1. Kids Mode remains free; no Kids login or purchase.
2. Every Kids installation needs its own Permit and its own successful purchase.
3. A Permit cannot be reused for another Kids installation.
4. Client-side payment success, UPI intent returns, screenshots and UTR strings are not proof of payment.
5. Owner credentials and backend secrets must never be embedded in the APK.

See `docs/FINAL_REQUIREMENTS.md`, `docs/PAIRING_PAYMENT_API.md` and `backend/README.md` for details.
