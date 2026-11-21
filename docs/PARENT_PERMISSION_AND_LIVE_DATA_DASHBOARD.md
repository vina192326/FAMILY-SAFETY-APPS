# Parent Permission Centre & Separate Live Data Dashboard

## Product intent

Parent Mode must show a dedicated **Permission Centre** and a separate **Family Data Dashboard**. The approved Stitch archive contains a 20-permission live-access screen and a Kids permission-onboarding flow. The old prototype text also describes silent remote grants and forced camera/microphone access; those behaviors are explicitly NOT carried forward.

## Non-negotiable Android and privacy rules

1. Android OS permissions cannot be silently granted by the Parent app or by an FCM message. A Parent action may send a permission request to the paired Kids device, but the Kids device must show a clear explanation and the device user/guardian must complete any required Android system dialog or Settings action.
2. Never report a permission as ON because a request was sent. Use server-reported Kids-device state with `granted`, `denied`, `permanently_denied`, `not_applicable`, `request_pending`, `unknown`, and `last_checked_at`.
3. No hidden camera/microphone activation, ambient audio, covert recording, call/SMS scraping, touch capture, or silent accessibility/device-admin enrollment. Camera and microphone are not remote live-surveillance features. If a future child-initiated SOS feature uses them, it must be foreground, visibly indicated, purpose-limited, and explicitly user-started.
4. Accessibility, notification-listener, usage access, background location, overlay, and battery-optimization exemptions are special/high-impact access. Explain the exact purpose and open the relevant Android Settings screen; never claim they can be silently toggled.
5. Collect only data needed for a visible family-safety feature. Show collection status and last update. Parent commands must be authenticated, auditable, rate-limited, and scoped to the paired Kids device.
6. A missing permission or offline Kids device means **Unavailable / Last known**, not live. Do not fabricate telemetry.
7. Follow the existing retention policy: protect queued data while Parent is offline for the configured retention window (2–3 days or up to 7 days as selected); after the first successful Parent view, start the immutable 24-hour cloud deletion deadline. Repeated viewing must not reset it. A locally exported file remains under Parent control.

## The 20 permission/capability rows

These are 20 user-facing capability rows, not 20 permissions that can all be granted with one button. Some are manifest declarations, some are runtime permissions, and some are special Settings access. Only request a row when the installed feature and Android version require it.

| # | Permission / capability | What Parent sees | Grant path / notes |
|---|---|---|---|
| 1 | Precise location | Live location availability, accuracy, timestamp | Android location runtime permission |
| 2 | Background location | Background location/geofence readiness | Separate Android background-location Settings flow where required |
| 3 | Activity recognition | Motion/activity signal readiness | Runtime permission on supported Android versions |
| 4 | App usage access | Screen-time/app-usage summaries | Usage Access Settings; special access |
| 5 | Notifications | Alert delivery readiness | Runtime notification permission on Android 13+ |
| 6 | Notification listener (optional) | Explicitly enabled notification-event integration status | Special Settings access; disabled by default; only if a documented feature requires it |
| 7 | Camera (child-initiated only) | Camera permission status for an explicitly started SOS feature | Runtime permission; never remotely activate camera |
| 8 | Microphone (child-initiated only) | Microphone status for an explicitly started SOS feature | Runtime permission; never remotely activate microphone |
| 9 | Bluetooth scan | Nearby accessory discovery readiness | Runtime permission on supported Android versions |
| 10 | Bluetooth connect | Paired accessory connection readiness | Runtime permission on supported Android versions |
| 11 | Nearby devices | Nearby-device feature readiness | Android-version-dependent runtime permission |
| 12 | Wi-Fi state | Network connection type/status | Normal manifest permission; no SSID/BSSID tracking by default |
| 13 | Network state | Online/offline connectivity | Normal manifest permission |
| 14 | Foreground location service | Visible location service running/stopped | Manifest/service declaration; persistent notification required |
| 15 | Foreground data-sync service | Upload/sync service state | Manifest/service declaration; obey Android foreground-service limits |
| 16 | Boot completed receiver | Whether sync can resume after reboot | Manifest receiver; no permission toggle on most devices |
| 17 | Battery optimization status | Optimization enabled/exempt status | User-directed system Settings flow; no bypass |
| 18 | Exact alarm capability | Scheduled safety reminder readiness | Special app access where required; use only for exact user-visible alarms |
| 19 | Wake lock capability | Whether a short, bounded sync/alert wake lock is in use | App capability, not a user-grantable permission; strict timeout |
| 20 | Internet access | Backend connection readiness | Normal manifest permission; show server reachability separately |

Do not include READ_CALL_LOG, READ_SMS, contacts harvesting, remote dialing, DND override, overlay lockout, device-admin lock, or accessibility touch control as default family telemetry permissions. If a separately approved feature later requires any high-impact capability, it needs a new threat/privacy review and explicit disclosure.

## Permission Centre UI and behavior

- Dedicated entry from Parent Settings and a shortcut on the Parent Home status area.
- Header: paired Kids device name, online/offline, last permission sync, granted count / applicable count.
- Each row: permission name, purpose, status, last checked time, and a clear action:
  - Granted: Details / Recheck
  - Not granted: Send request to Kids device
  - Request pending: Waiting for Kids device action
  - Denied/permanently denied: Show explanation and Settings instructions
  - Not applicable: Explain why it is not needed
  - Unknown/offline: Show last known state and timestamp
- “Request remaining” sends explanatory requests only. It must never flip rows to granted or bypass Android dialogs.
- Parent may revoke the app's server-side feature access at any time; OS-level revocation must be performed on the Kids device.

## Separate Family Data Dashboard

Create a distinct dashboard (not mixed into the permission list) with these sections:
- Live Overview: device online state, last sync, location freshness/accuracy, current safety status.
- Location: current location only while permission and live feed are active; route history separately marked with collection time and retention deadline.
- Activity & Screen Time: permitted UsageStats summaries, app/category durations, daily totals; never claim live foreground-app monitoring unless an explicitly approved Kids-side implementation supports it.
- Safe Places & Check-ins: geofence events and child check-in events.
- Alerts & SOS: event timeline, delivery/acknowledgement status, severity, timestamps.
- Device Health: battery level, charging, network type, app version, permission health, last contact.
- History & Exports: records with first-view timestamp, 24-hour delete deadline, download/share/delete actions and export status.

Every tile must show one of: **LIVE**, **LAST KNOWN**, **HISTORY**, **UNAVAILABLE**, or **PERMISSION NEEDED**. Live must include a server timestamp and freshness threshold. A stale event must automatically stop displaying as LIVE.

## Backend contract required before the UI can claim completion

- Kids device permission snapshot endpoint with per-capability status, OS/API level, last checked timestamp and app version.
- Parent read endpoint scoped to the authenticated Parent device and paired Kids device.
- Parent permission-request endpoint that creates an auditable request/event; FCM is a wake-up/delivery hint, not a grant.
- Kids app permission onboarding and Settings deep-link handler; callback updates the server only after the OS state is rechecked.
- Telemetry ingestion for location, activity summaries, check-ins, alerts and device health; server-side timestamps, validation, device binding, idempotency and rate limits.
- WebSocket/SSE or bounded polling for live updates; reconnect/backoff and offline queue.
- Retention and delete workers applied consistently to all stored telemetry.
- Tests: denied/revoked permission, Android version differences, offline Kids device, stale live data, duplicate event, revoked pairing, expired Permit, and deletion deadline.

## Completion gate

Do not mark this feature complete until the Kids Android app actually implements and reports these permission states, Parent requests are auditable, data feeds are real, separate dashboards work end-to-end, and tests pass on supported Android versions and physical devices.
