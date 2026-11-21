# Parent Mode — Verified Audit (21 Nov 2026)

Repository: vina192326/FAMILY-SAFETY-APPS, branch main.

This is a source-level audit of the current repository. It is not a device/emulator acceptance test. Status labels:
- **CONNECTED** — Android code calls a real backend endpoint.
- **PARTIAL** — some underlying service exists, but the complete user flow is not wired.
- **UI ONLY** — visible screen/action exists but has no real operation.
- **MISSING** — no implementation found in the inspected source.
- **BLOCKED** — cannot be verified without deployed services, credentials, Kids app or test devices.

## Executive result

**Parent Mode is NOT complete.** The current Android app is a Compose starter shell, not a fully functional Parent dashboard. The verified Android-to-backend calls now include Parent bootstrap, linked-device list, history read/delete, PDF-before-delete preference, six-digit OTP validation, pairing-session plan catalogue and payment-order creation. Payment checkout, payment verification UI and Permit activation are still not completed in the Android app. Most other visible controls remain placeholders.

## Screen-by-screen findings

| Screen / control | Current source behavior | Status |
|---|---|---|
| App launch / Parent no-login | Attempts Parent device bootstrap; encrypted token stored with Android Keystore | PARTIAL / BLOCKED: API base URL is https://api.example.invalid |
| Home | Shows linked-device count and basic cards; Add Child, Link Device, Check-In, Downtime navigate to detail pages | UI ONLY for most actions |
| Devices | Lists linked devices and plan/Permit data | CONNECTED |
| Run Diagnostics | Opens generic not-connected page | UI ONLY |
| Activity | History entry and report navigation; date chips do not change query | PARTIAL |
| Alerts | Empty state; no alert-feed API | MISSING |
| Settings search | Filters static local settings list | UI ONLY |
| PDF-before-delete switch | GET/PUT backend preference; state changes on user interaction | CONNECTED (preference only) |
| Pair Device | 6-digit input now calls `/v1/parent/pairing/validate` and loads plan catalogue after server verification | CONNECTED (build/runtime still pending) |
| Plan selection | Server plan catalogue is now shown and selection creates a Razorpay order | PARTIAL; checkout and verification UI missing |
| UPI / QR / Razorpay checkout | Android can create a Razorpay order; no SDK checkout or payment verification UI yet | PARTIAL |
| Manual UTR reconciliation | No reviewed manual-payment workflow found | MISSING |
| History read | Calls GET /v1/parent/history | CONNECTED |
| History delete | Calls DELETE /v1/parent/history/{id} | CONNECTED |
| PDF generation / Downloads / Share | Not implemented | MISSING |
| Location map / live GPS | No Kids telemetry feed or map integration in Parent app | MISSING |
| Live app usage | No Kids telemetry feed | MISSING |
| Battery / charging / network | No Kids telemetry feed | MISSING |
| Ring / Locate / Nudge / Check-In commands | No command queue/FCM ack pipeline | MISSING |
| Pause / Downtime / routines | No command/policy backend wired to UI | MISSING |
| Permission Centre (20 rows) | Requirements doc only; no Android screen or Kids permission snapshots | MISSING |
| Separate Family Data Dashboard | Requirements doc only; no telemetry-backed dashboard | MISSING |
| Safe Places / geofence | Placeholder navigation only | MISSING |
| Alerts / SOS / push notifications | No event ingestion or push registration pipeline found in inspected app/backend files | MISSING |
| Multi-parent guardians | No invitation, approval or role APIs found | MISSING |
| Auto-renew UPI mandate | Not implemented | MISSING |
| Retention selector 24h/7d | Backend retention foundation uses 7-day unviewed and 24h after first view; Parent selector is not implemented | PARTIAL |
| Emergency Safe Wipe | No revoke-all + data-deletion workflow wired to UI | MISSING |
| Owner-only security settings | Backend Owner auth exists separately; no Parent UI should expose it | PARTIAL |

## Backend foundation verified in source

- Parent/Kids device bootstrap and six-digit OTP pairing endpoints exist.
- Plan catalogue is returned only after pairing-session validation.
- Razorpay order creation and server-side signature + payment lookup exist.
- Permit and device binding are created after payment verification.
- Parent device list and purchase-history endpoints exist.
- Kids reconnect checks existing binding/Permit.
- History ingestion/read/delete and PDF preference endpoints exist.
- History payload encryption uses AES-GCM; server purge worker is scheduled every 60 seconds.
- Owner authentication foundation is separate from Parent mode.

These source files do not prove production readiness. Database migrations, environment secrets, deployed HTTPS endpoint, Razorpay configuration, FCM credentials, live Kids app behavior and integration tests remain required.

## Build verification

The inspected Android workflow run 44912002354 failed at the SDK licence step before :app:assembleDebug; build and artifact upload were skipped. The job log shows the failing command was sdkmanager --licenses from PATH and returned command not found. The workflow has now been updated to use android-actions/setup-android@v3, the SDK manager installed by that action, Java 17 setup and a manual workflow_dispatch trigger. **The corrected workflow has not yet been run/verified.** No APK is available to claim.

## Critical configuration blocker

app/build.gradle.kts currently hardcodes https://api.example.invalid as API_BASE_URL. Until a real HTTPS backend is deployed and configured, the Parent app cannot perform real network operations. Never replace this with an unencrypted public endpoint for production.

## Remaining completion gates

1. Deploy and configure the backend, database migrations, secrets, payment provider and FCM.
2. Finish Android pairing → plan → payment → verified Permit UI.
3. Implement Kids-side telemetry, permissions and upload pipeline; Parent only displays server-fed data.
4. Implement live Family Data Dashboard, maps, freshness/stale states and history.
5. Implement secure, acknowledged, supported FCM commands and safe routines.
6. Implement alert/SOS pipeline, guardian invitations/roles, retention selector, PDF/CSV export, Sharesheet and revocation/wipe workflow.
7. Complete all settings persistence and remove placeholder navigation/actions.
8. Run backend tests, Android unit/UI tests, emulator tests and physical-device end-to-end tests.
9. Verify debug and release APK builds, signing, install/upgrade and production configuration.

## Platform/security scope

Do not implement covert Accessibility click/swipe control, hidden camera/microphone, call-log scraping, forced arbitrary calls, secret DND/volume overrides or silent full-device lock. Any supported command must be visible, consent-based, device-scoped, acknowledged and auditable. OS permission requests must be completed on the Kids device; a Parent request or FCM message cannot silently grant them.

## Additional work in this continuation

- Parent pairing UI now validates the 6-digit Kids code against the backend, fetches plans for the resulting pairing session, and creates a server-side Razorpay order for the selected plan. The app explicitly labels the order as pending; checkout, payment confirmation UI and Permit activation remain incomplete.
- Parent Settings now expose 24-hour / 7-day unviewed-history retention and the PDF preference using the backend preference API.
- Backend history ingestion now reads the Parent retention setting (24 or 168 hours) for unviewed records. First Parent read still sets the immutable 24-hour delete deadline. Migration `V006__parent_history_retention_preference.sql` is required before deployment.
- Android API URL can be supplied through `FAMILY_API_BASE_URL` Gradle property/environment variable; a real deployed HTTPS endpoint is still needed.
- The latest source changes have not yet been validated by a successful Android or backend build. Do not treat this as a completed release.


## Work committed in latest continuation

- Added Kids permission snapshot ingestion and Parent read API for up to 20 documented Android permission states (migration V007).
- Added encrypted latest Kids telemetry snapshot ingestion and Parent read API for battery/network/app label/location fields (migration V008). Parent screen displays only Kids-submitted data and can open reported GPS coordinates in the installed Maps app.
- Added Parent Child Profile shortcuts to Live Family Data, Permission Centre and History.
- Added Kids-to-Parent data contract and backend CI workflow.
- Added 24h/7d history retention selector migration V006 in the previous pass.

## Remaining blockers

- No Kids Android module/collector exists in this repository. No device submits permission snapshots or live telemetry until Kids-side consent UX, Android permission prompts, location/usage/battery/network collection and authenticated upload are built.
- FCM command delivery/acknowledgement and supported device controls are not implemented.
- Payment SDK checkout, payment verification UI and Permit refresh are incomplete.
- Alerts/SOS event feed, multi-parent guardians, PDF/CSV export and Sharesheet, automatic PDF generation, complete settings persistence, account deletion/revocation and full safe-wipe workflow remain incomplete.
- No successful build or emulator/physical-device test has been verified after latest changes. Migrations V006–V008 must be applied before deploying corresponding APIs.
