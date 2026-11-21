# Kids → Parent data integration contract

The Parent app is a viewer/controller only. It does not request Kids location, usage, call, camera, microphone or other device permissions and does not collect telemetry from the Parent phone.

## Kids telemetry upload

Authenticated Kids device endpoint:
- POST /v1/kids/telemetry
- Header: Authorization: Bearer <Kids device token>
- JSON: {"capturedAt":"2026-11-21T10:00:00Z","payloadJson":"{...}"}
- Maximum payload: 64 KiB.
- Requires a valid, non-revoked, unexpired Permit binding.
- Latest snapshot is encrypted with AES-256-GCM using HISTORY_ENCRYPTION_KEY_BASE64; database stores ciphertext only.
- Parent reads its own linked device snapshot from GET /v1/parent/telemetry?kidsDeviceId=<uuid>.

Suggested payload keys: latitude, longitude, accuracyMeters, batteryPercent, charging, networkType, foregroundAppLabel, screenState, locationPermission, usageAccessPermission. Upload only values the guardian and child have been clearly informed about and consented to. Do not include call content, messages, passwords, camera/microphone recordings or unrelated personal data.

The Parent screen shows the snapshot capture time; it must be treated as stale when old. A map deep link opens supplied coordinates in the device's installed map app. It is not a continuously streaming map until the Kids app uploads fresh snapshots.

## Kids permission snapshot

- POST /v1/kids/permissions with {"permissions":[{"key":"LOCATION_FOREGROUND","granted":true,"required":true,"detail":"While-in-use"}]}.
- Maximum 20 entries; only supported keys are accepted.
- Parent reads GET /v1/parent/permissions?kidsDeviceId=<uuid>.
- Supported keys: LOCATION_FOREGROUND, LOCATION_BACKGROUND, NOTIFICATIONS, USAGE_ACCESS, ACCESSIBILITY_CONTROL, CAMERA, MICROPHONE, CONTACTS, CALL_LOGS, PHONE_STATE, SMS, PHOTOS_MEDIA, FILES, BLUETOOTH_NEARBY, WIFI_NETWORK_STATE, BATTERY_OPTIMIZATION, DISPLAY_OVER_APPS, DEVICE_ADMIN_LOCK, EXACT_ALARMS, FULL_SCREEN_INTENT.
- Permission grant prompts must happen on the Kids device through Android's official APIs. Parent cannot grant them remotely or silently.

## Database migrations

Apply Flyway migrations V006, V007 and V008 before deploying these APIs. Keep the history encryption key stable and secret; rotate only with an explicit re-encryption plan.

## Kids Android source now present

The single Android app module now includes a KidsModeActivity, a 20-step permission/capability wizard, separate Kids device bootstrap/token storage, six-digit OTP generation, permission snapshot upload, manual sync, and an explicit Start/Stop Live Location Sharing action. Live sharing runs in a visible Android location foreground service and submits telemetry plus LOCATION history; APP_USAGE history is sent only when Usage Access is granted.

The Parent dashboard is still a viewer/controller. It does not collect the Kids phone's permissions or telemetry. The Kids uploader requires a valid active Permit binding for telemetry/history, so a successful OTP pairing without verified payment/Permit will not make live data appear.

## Remaining integration gates

The Kids collector is source-wired to the documented APIs, but has not been built or tested on a real Android device. There is no persistent offline retry queue yet. The app requires a deployed HTTPS backend, database migrations V006–V008, token and encryption secrets, and verified payment/Permit provisioning. FCM commands, acknowledgements, alerts/SOS and multi-parent remain unimplemented. This contract is not evidence of production live data availability.
