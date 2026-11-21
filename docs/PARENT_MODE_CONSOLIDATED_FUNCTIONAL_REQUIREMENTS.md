# Parent Mode — Consolidated Functional Requirements (User-approved scope)

This document consolidates the latest Parent Mode request with the existing product architecture. Parent Mode is a control, billing and viewing client. It does not independently collect child telemetry. The paired Kids installation collects only the explicitly enabled, disclosed signals and uploads them to the backend.

## 1. Device linking and billing

### Required flow
1. Parent enters the six-digit, short-lived pairing code displayed by the Kids installation.
2. Backend validates OTP and returns the target Kids device summary plus a short-lived pairing session.
3. Parent selects one plan for that Kids installation: 10 minutes, 1 day, 1 month, or Lifetime. Plan duration and expiry are server-defined; Lifetime must be a separately configured SKU.
4. Parent sees the payment instructions and server-created order. Support gateway UPI checkout and an Owner-configured UPI ID / QR presentation if enabled.
5. Parent may submit UTR/reference for manual reconciliation. UTR text or screenshot alone is never proof of payment. A trusted gateway webhook/server API or an Owner/reviewer reconciliation against the merchant bank statement must confirm exact amount, currency, order, payer/reference and paid status before activation.
6. On verified payment, backend atomically issues a unique Permit and binds it to that exact Kids installation.
7. Parent may link multiple Kids installations. Every additional Kids installation requires its own plan, payment and Permit. Reconnect an existing valid binding without repeating OTP/payment.

### Payment safeguards
- Never activate on QR scan, UPI intent return, screenshot, client callback or UTR submission alone.
- Manual verification must be an audited Owner/reviewer action with evidence/reference and idempotency; do not expose bank credentials to the app.
- Auto-renew is opt-in only, requires a supported UPI AutoPay mandate and clear cancellation path. Do not claim auto-renew if the chosen payment provider/mandate integration is not configured.

## 2. Live monitoring — Kids-originated data only

Parent displays data received from the paired Kids app/backend:
- Live GPS location on Google Maps, with permission status, timestamp, accuracy and stale/offline state.
- App usage summaries and, only where Android permits and the Kids app has explicitly enabled Usage Access, a current/most-recent app activity indicator. Do not promise uninterrupted real-time foreground-app spying.
- Battery percentage, charging state, network class (Wi-Fi / cellular generation where reliably available), app version and last contact.
- Safe-place entry/exit and child-initiated check-ins.
- Every tile must be labelled LIVE, LAST KNOWN, HISTORY, UNAVAILABLE or PERMISSION NEEDED. A stale feed must not be labelled live.

Parent app must not run its own location, microphone, camera, call-log or app-usage collection to obtain Kids data.

## 3. Remote controls — supported, consent-based subset

All commands must be authenticated, bound to one Kids Permit/device, delivered through FCM as a wake-up hint, acknowledged by Kids app, logged with actor/time/result, rate-limited and shown with an explicit status (queued/delivered/accepted/completed/failed/offline). FCM delivery does not grant Android permissions or bypass OS restrictions.

Supported implementation candidates, subject to Android policy and visible Kids-side behavior:
- Ring/Safety alert: a visible, user-disclosed alert with sound only within Android notification/channel, DND and volume rules; no claim that it overrides silent/DND.
- Pause app's own selected family-safety feature or schedule, with clear duration and emergency exception; do not lock down the whole device.
- Locate request: asks the Kids app to send a fresh location if permission is already granted and the location service is active.
- Check-in prompt / Nudge: visible notification/prompt on Kids device.
- Brightness/flashlight: only as a visible, foreground Kids-side action with OS permission/API support and clear user indication; no hidden control.

Not included in this product scope:
- Secret touch/click/swipe/navigation via Accessibility.
- Remote calls to arbitrary numbers, forced auto-speaker, call interception or call-log extraction.
- Forcing volume or overriding DND/silent mode.
- Full-device remote lock or device-admin enrollment by stealth. Android device lock is not available to an ordinary app without special managed-device provisioning; this app must not pretend otherwise.
- Hidden camera/microphone activation or recording.

If device lock is later required, it needs a separately reviewed, opt-in Android Enterprise / fully managed-device design and explicit device-owner provisioning. Accessibility must not be used as a covert remote-control channel.

## 4. History, reports and export

- Location history/routes, permitted app-usage summaries, safe-place events, check-ins, alerts and device telemetry come from Kids app ingestion.
- Call logs are excluded from default collection. Do not request READ_CALL_LOG or extract call history.
- Support PDF/CSV reports and user-selected media download/share only after secure encrypted object storage and signed, expiring download URLs exist.
- Parent can share an exported file using Android Sharesheet (e.g. WhatsApp/email if installed); do not upload it to third-party apps automatically.
- Parent can delete a record immediately.
- Retention: unviewed data remains for the selected offline retention period (24 hours or up to 7 days, subject to plan/configuration). First authenticated Parent view sets an immutable 24-hour deletion deadline. Reopening must not reset it. PDF-before-delete is a local export preference, not a cloud retention extension. A user-exported Downloads copy remains under Parent control.

## 5. Settings and privacy

- Silent/zero-disturb: do not secretly silence the Kids device or suppress safety notifications. A Kids-side quiet presentation setting may mute nonessential app sounds, but must preserve Android-required foreground notifications and emergency alerts.
- Notification controls: configure only this app's own notifications and disclosed event categories; do not hide Android privacy indicators or intercept all notifications.
- Auto-renew: opt-in UPI mandate only when supported, with renewal amount, date, cancellation and mandate status visible.
- History retention selector: 24 hours / 7 days, constrained by plan and applicable law. Show current retention and delete deadline.
- PDF save toggle: locally generate/export a PDF on first view when enabled; report success/failure.

## 6. Security and access

- Parent opens directly without Google/email/username/password login; installation identity + device-scoped token is not a human login.
- Owner uses a separate concealed neutral entry and server-verified access code/password. No Google/Email login. Owner access never appears as a public Parent login screen.
- Emergency safe wipe means revoke the lost Kids installation's Permit/session, stop future uploads/commands, and request deletion of its server-held data under the configured retention/deletion policy. It does not factory-reset or erase the entire phone. Local app data can only be cleared when the Kids app next receives and processes the authenticated wipe/revoke instruction, or through normal OS uninstall/clear-data.
- Multi-Parent: additional guardian invitations require Primary Parent approval. Each guardian has a distinct device identity and role (Primary, Guardian, View-only). Enforce per-action permissions, audit events, revocation and notification to Primary Parent.

## 7. Required backend / Android work

- Kids: OTP, plan-independent free app bootstrap, contextual permission requests, telemetry collectors, upload queue, FCM command receiver, command acknowledgements, permission snapshots and pairing/Permit enforcement.
- Parent: pairing form, plan picker, payment/UTR reconciliation state, multiple child cards, separate live data dashboard, command center, reports/export, retention controls, guardian management.
- Backend: webhook/manual reconciliation, payment ledger, Permit/device binding, telemetry ingestion, freshness/status computation, FCM registration/delivery, command audit and idempotency, role-based multi-parent authorization, retention workers and export links.
- Android permission flows must be version-aware and user-visible. A Parent request can ask the Kids device to display an explanation; it cannot silently grant an Android permission.
- Production API endpoint, TLS, secrets, push credentials, payment provider setup, database migrations, monitoring and tested backup/restore are required.

## 8. Acceptance checklist

Do not call Parent Mode complete until:
- Every supported Parent button has a real action and clear loading/success/failure/offline state.
- Pairing + separate payment + unique Permit works for multiple Kids devices.
- Payment cannot be forged by UTR text, screenshot or client-side state.
- Kids-originated telemetry is correctly bound, fresh/stale labelled and permission-aware.
- Commands are authenticated, acknowledged, auditable and tested offline/reconnect.
- Unsupported Android operations are clearly disabled/explained, not simulated.
- History retention, first-view 24-hour timer, manual delete and PDF/export pass tests.
- Multi-guardian invitation, approval, role checks and revocation pass tests.
- Security, privacy, Android policy, emulator and physical-device tests pass.
- Debug/release APK builds; production configuration and signing are verified.
