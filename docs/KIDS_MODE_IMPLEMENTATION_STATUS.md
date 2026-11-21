# Kids Mode implementation status — 21 Nov 2026

Repository: vina192326/FAMILY-SAFETY-APPS, branch main.
This is a source review, not a successful build or device acceptance test.

## Architecture
- One Android APK, one app module. Parent Mode and Kids Mode have separate screens, separate device bootstrap tokens and separate encrypted token storage in Android Keystore.
- Parent Mode is a viewer/controller and does not request the child's device permissions.
- Kids Mode creates a Kids device identity, rotates a six-digit pairing OTP and displays the OTP expiry for the Parent to enter.
- Parent verifies OTP, chooses the plan, creates a payment order. Complete payment checkout/verification UI and Permit activation still need to be finished and tested.
- Kids Mode is accessible from the Parent dashboard's "Kids Mode" entry and Settings → Family & Guardians → Kids Mode Setup. On the child phone, open the same APK and tap Kids Mode.

## One-button 20-step permission wizard
The Kids screen has one "Start Setup — 20 steps" button. It guides through the following permission/capability states in sequence and uploads a 20-row snapshot to the backend.

| # | Permission/state | Implementation |
|---:|---|---|
| 1 | Foreground location | Android runtime request for coarse + fine location |
| 2 | Background location | Separate Android Settings step; user must enable explicitly |
| 3 | Notifications | Android runtime request on Android 13+ |
| 4 | Usage access | Opens Android Usage Access settings; optional |
| 5 | Accessibility control | Explicitly not requested; remote click/swipe not supported |
| 6 | Camera | Not requested |
| 7 | Microphone | Not requested |
| 8 | Contacts | Not requested |
| 9 | Call logs | Not requested |
| 10 | Phone state | Not requested |
| 11 | SMS | Not requested |
| 12 | Photos/media | Not requested |
| 13 | Files | Not requested |
| 14 | Bluetooth nearby | Not requested |
| 15 | Wi-Fi/network state | Normal network-state capability; no dangerous runtime prompt |
| 16 | Battery optimization | Optional Android settings review |
| 17 | Display over other apps | Not requested |
| 18 | Device admin / whole-phone lock | Not requested |
| 19 | Exact alarms | Not requested |
| 20 | Full-screen intent | Not requested |

The list is 20 tracked permission states, not 20 permissions that should all be granted. Dangerous or unrelated access is not silently enabled. The child/phone user can decline optional items.

## Kids → Parent data
- Kids bootstrap and OTP rotation use the existing authenticated /v1/kids/bootstrap and /v1/kids/pairing-otp/rotate APIs.
- Permission snapshot is submitted to POST /v1/kids/permissions and Parent reads GET /v1/parent/permissions.
- Manual "Sync with Parent now" submits permission state and a latest telemetry snapshot.
- Explicit "Start Live Location Sharing" starts a visible Android location foreground service. Android displays an ongoing notification; "Stop Live Location Sharing" stops the service.
- Location updates are requested at 15-second intervals / 10-metre minimum displacement where Android providers deliver updates. Each update submits latest telemetry and a LOCATION history record. Optional app-usage label is submitted as APP_USAGE only if the user enabled Usage Access.
- Backend encrypts latest telemetry and history records with AES-256-GCM. Parent dashboard polls latest telemetry every 15 seconds and shows freshness/stale status.
- Telemetry requires a valid, non-revoked and unexpired Permit binding. Before payment/Permit activation, uploads are rejected; the Kids UI must not claim Parent can view live data yet.

## Not implemented / blocked
- End-to-end Razorpay checkout, payment verification screen, Permit refresh and reconnection UX.
- Production HTTPS backend, database, migrations V006–V008, encryption/token secrets, Razorpay credentials and FCM configuration.
- Persistent offline queue/retry for location/history if network is unavailable.
- FCM or secure command queue with acknowledgements; supported Parent actions such as check-in, pause in-app sharing, ring only after user-approved implementation.
- Full location history route visualization, safe places/geofences, SOS event pipeline and alert feed.
- Full app usage durations/charts and robust foreground-app reporting; Usage Access is optional and Android may return incomplete data.
- Multi-parent invitation/approval, auto-renew mandate, full payment history, exports for all media, and account/device revocation flows.
- Android tests, backend integration tests, emulator and physical-device testing, successful debug/release build and signed APK.

## Safety boundary
This app does not implement covert Accessibility clicks/swipes, hidden camera/microphone capture, SMS or call-log scraping, arbitrary remote phone calls, silent DND/volume override, or silent full-device lock. Android permissions are granted only on the Kids device by the person using it. Any later remote command must be visible, consent-based, authenticated, limited to the linked device and auditable.
