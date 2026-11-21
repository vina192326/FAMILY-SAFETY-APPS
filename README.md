# FAMILY SAFETY — Single APK

A single Android application (`com.familysafety.app`) with three isolated experiences:
- **Parent Mode** — direct dashboard access; no email, username, password or parent login.
- **Kids Mode** — permanently free; no account or payment. Generates a short-lived pairing OTP.
- **Private settings entry** — neutral public label; the private Owner console is shown only after server-verified Owner authentication. No public Owner/Admin/Master Login wording.

## Non-negotiable purchase and binding rules
1. Each Kids installation has its own installation identity and requires its own Permit.
2. A Parent enters the OTP currently displayed by the Kids installation. The backend validates it before exposing plans.
3. The Parent chooses a plan with explicit duration, price, history-retention access and permissions.
4. Payment must be verified by the configured payment gateway on the server. A client callback, screenshot, UPI intent result, UTR string or local "success" flag is never proof of payment.
5. Only a verified/captured payment can create a unique Permit and activate the device binding.
6. A still-valid binding reconnects using its device credential; it does not ask for another OTP.
7. One Permit can bind to only one Kids device. Additional Kids installations need separate purchases and separate Permits.

## Repository layout
- `android/` — one Android application module; Parent/Kids/Owner are in-app modes, not separate APKs.
- `backend/` — Ktor API, PostgreSQL schema and payment-gateway integration.
- `docs/` — API, security, release and test requirements.

## Backend configuration
Copy `backend/.env.example` to a private environment file. Configure PostgreSQL, public API URL, payment gateway key ID/secret and Owner password/TOTP secret. Never commit real secrets.

## Important status
This repository is an implementation baseline, not a claim that a signed production APK has already passed device testing. A production release requires configured gateway credentials, Firebase credentials if push is enabled, HTTPS deployment, Android SDK/Gradle build, emulator and physical-device tests, and release signing.
