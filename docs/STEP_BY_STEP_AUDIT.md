# FAMILY SAFETY — Step-by-Step Source & Repository Audit

Audit date: 2026-11-21
Source: `stitch_family_safety_ui_ux_design_system (2).zip` recovered from the user's Library.
Scope: From first launch / no-login role selection through Parent, Kids, Owner, every mapped settings group, permissions, backend, and release checks.

## 1. Evidence and audit method

The ZIP was opened and inspected directly. It contains 507 archive entries:
- 110 rendered screen folders with `code.html` and `screen.png`.
- 9 additional `three.js_*` HTML demo files (not application screens).
- `screen_inventory.json` declares 18 primary inventory entries.
- `feature_screen_mapping.json` declares 161 capabilities in 12 functional groups.
- Navigation, design tokens, Compose/manifest/API/database specifications, and numerous test/report documents.

Static HTML interaction scan across the 110 screen pages:
- 1,232 button / role=button elements.
- 471 anchor links; 418 point to `#` or another placeholder.
- 163 form inputs/selects/textareas.
- 624 inline/listener handler occurrences.
- 83 of 110 screens contain at least one placeholder link.
These counts describe design-prototype HTML only. They do not prove native Android behavior. Several handlers merely animate UI or display simulated success; several buttons have no action.

The repository was inspected separately. Its current Git tree contains 31 entries and a Ktor backend module only. There is no Android `app/` module, Gradle Android wrapper/project, Compose screen implementation, APK/AAB, signing artifact, or device-test suite.

## 2. Final user requirements — source-of-truth precedence

These latest user requirements override conflicting older ZIP text:
1. One Android application, one APK, one package, with three separated modes.
2. Parent Mode: no account login, email, username or password; launch directly to Parent Dashboard.
3. Kids Mode: free, no login/account/subscription/payment; generates the temporary pairing OTP.
4. Parent enters the Kids OTP. Only after successful OTP verification can plans be shown.
5. Payment must be verified by the real payment gateway/server before a unique Permit is issued and the Kids device is bound.
6. One Kids installation/device = one unique Permit and its own successful purchase. No Permit sharing, no Permit-free bypass, no re-use for a second device.
7. A valid existing binding reconnects without another OTP.
8. Owner entry is the discreet public label `अन्य`; no public Owner/Admin/Master Login label. The Owner Dashboard requires server authentication.
9. Owner login shows User ID and Password fields, but User ID may be blank. Correct Permanent Master Password alone authenticates. All failed credential combinations display exactly `Invalid User ID or Password.`
10. Initial Owner password choices are only `Virendra#19`, `Virendra#23`, `Virendra#26`. Store password hashes only; rotate after secure deployment because these values have been disclosed in chat.
11. Preserve the Stitch visual design. Adapt only conflicting labels/flows as required by the newer rules.

## 3. Launch and role routing audit

| Step | Intended behavior | Source/design finding | Repository status |
|---|---|---|---|
| 0. App launch | One package opens Splash then role selection | P-01 and a single-app role selector exist as designs | No Android launcher/activity |
| 1. Role selection | Parent / Kids / neutral `अन्य` entry | Role selector has Parent and Kids cards; Parent card says “Requires Login”; no `अन्य` entry | Not implemented |
| 2. Parent selection | Go directly to P-05, no login screen | Navigation map routes P-01 → P-02; P-02 contains email/password/code | Must bypass P-02 and delayed-login gate |
| 3. Kids selection | Free Kids setup; no account | K-01/K-02 pairing and K-03 permission designs exist | Not implemented |
| 4. Owner selection | `अन्य` → Owner password gate → Owner Dashboard | Owner gate design exists but public role selector does not expose neutral entry; gate currently presents username as a required credential | Backend login exists; UI/dashboard APIs absent |

**Required route correction:** P-01 → role selector → Parent bootstrap/device token → P-05. Kids selection → Kids bootstrap/OTP display → transparent setup. `अन्य` → Owner login → ADMIN-01. P-02 unified login, password recovery, public login-code activation, and 10-minute demo/activation gates must not block Parent Mode.

## 4. Parent Mode — step-by-step audit

### 4.1 First launch and dashboard
- Direct launch to P-05 without email, username, password, login code, or demo timer.
- Parent installation receives a device-scoped backend token silently as technical device registration, not an account login.
- Dashboard shows truthful child connection state; no sample family names, fake live GPS, hard-coded battery, fake alerts, or demo telemetry.
- Multi-child cards and child selection must be connected to actual server records.

Status: Design exists; Android screen and data binding absent.

### 4.2 Pair a Kids device (P-09)
Required sequence:
1. Kids device bootstrap generates a unique 6-digit OTP with expiry.
2. Kids screen shows/copies OTP and expiry; refresh invalidates prior unconsumed OTPs.
3. Parent enters OTP and submits.
4. Backend validates expiry, one-time use, device ownership, and rate limits.
5. Only after successful validation, Parent sees available plans.
6. Parent selects a plan and sees amount, duration, history retention, and permitted capabilities.
7. Backend creates a real gateway order.
8. Gateway payment is independently verified as captured, with matching order, amount, and currency.
9. Only then does one database transaction issue one unique Permit and bind it to that Kids device.
10. Parent sees success, Permit/expiry and linked device; Kids reconnects using its existing valid binding without OTP.

Current backend implementation:
- `POST /v1/kids/bootstrap`, `POST /v1/parent/bootstrap`
- `POST /v1/kids/pairing-otp/rotate`
- `POST /v1/parent/pairing/validate`
- `GET /v1/parent/plans`
- `POST /v1/parent/payments/orders`
- `POST /v1/parent/payments/verify`
- `POST /v1/kids/reconnect`
- `GET /v1/parent/devices`, `GET /v1/parent/purchases`

Backend gaps/risks:
- No plan catalogue seed/admin UI: plans endpoint will return an empty list until real plans are inserted.
- Razorpay credentials and production database are not configured/tested here.
- Razorpay webhook signature validation/idempotent webhook processing is not implemented.
- No Android client calls these APIs yet; no Keystore token storage.
- Bootstrap fingerprint is currently client-supplied; production device attestation/binding hardening is absent.
- OTP rotation inserts a new OTP but does not explicitly invalidate every older unconsumed OTP for that device.
- Rate limiting is per Parent device ID; fresh bootstrap identities can evade this without additional IP/device-level controls.
- An expired but not revoked binding can still block a new binding because the Kids device ID is the binding primary key; renewal/re-purchase lifecycle needs an explicit policy.
- No integration tests against PostgreSQL or Razorpay sandbox have been run.

### 4.3 Parent bottom navigation and child controls

The source Navigation Map defines five Parent destinations:

| Destination | Screens / subflows | Required functional checks | Status |
|---|---|---|---|
| Home | P-05, P-06, P-09 | child cards, status truth, open profile, pair child, SOS alert deep-link | Design only |
| Devices | P-08, diagnostics, P-09 | list devices, ping, battery/network/GPS health, unlink/reset safeguards | Design only |
| Activity | P-11/12, P-14 | screen-time/activity, routines/schedules, filters, PDF/CSV export | Design only |
| Alerts | P-13, P-10, Quiet Hours | SOS, geofence, critical alerts, live route, quiet-hours controls | Design only |
| Settings | P-15, P-16, P-17, P-18 | account/security, notification settings, privacy/DPDP, app preferences | Design only |

The 161-capability mapping groups are:
- 3.1 Accounts, Identity and Security — 14
- 3.2 Family and Child Profiles — 11
- 3.3 Device Linking and Device Management — 18
- 3.4 Location and Safety — 17
- 3.5 Screen Time and Routines — 13
- 3.6 App and Web Safety — 12
- 3.7 Device Health and Connectivity — 13
- 3.8 Communication, Notifications and Support — 15
- 3.9 Reports, Data and Family Controls — 11
- 3.10 AI and Intelligent Assistance — 9
- 3.11 Owner and Platform Administration — 16
- 3.12 Accessibility, Usability and Preferences — 12

These are mapped capability counts, not completed implementation counts.

## 5. Parent Settings — full category checklist

The P-15 design exposes these groups and controls; each needs real persistence, validation, error state, and tests:

1. Account & Security: profile, MFA/passkey/biometric controls, active sessions, credential/session revocation. Parent account login is not required under latest requirements; remove mandatory email/password/MFA dependency.
2. Family & Guardians: co-guardian roles, permissions, child profiles, invitations, custody/guardian changes. Require explicit consent and audit every privilege change.
3. Family Timezone: device/system timezone and family timezone; schedule calculations must be DST/timezone safe.
4. Safety Policies & Routines: bedtime/school schedule, safe geofences, emergency escalation, duration policy. Source says daily restrictions are not mandatory/default; do not silently force restrictive lockouts.
5. Privacy & Data: retention, live-only mode, export, clear history, delete account/vault. Source conflicts between 24-hour purge, 14-day rolling default, and selectable 7/14/30/90-day retention. Product owner must choose one clear retention policy before implementation.
6. AI Insights: opt-out and data-use disclosure; no background AI collection without consent.
7. Appearance & Language: English/Hindi only; light/dark/system theme; preserve Aegis tokens.
8. Support & Legal: WhatsApp deep link, support destination configuration, terms, privacy notice and consent records. Replace hard-coded sample phone numbers/URLs.
9. Android Permission Health: show actual granted/denied/restricted state and Settings shortcut; never display hard-coded “all healthy”.
10. App Health / Updates: version, diagnostics, update check, offline/network state, crash report consent.
11. Device / Access Management: linked Kids devices, Permit status/expiry, renew/revoke/unlink, purchase history, per-device ownership.
12. Notification preferences: quiet hours, sound/vibration preferences, emergency override rules. Normal telemetry may be quiet; SOS remains explicit and visible.

Current setting designs include dummy navigation links and sample data; no native preference persistence exists in the repository.

## 6. Kids Mode — step-by-step audit

| Step | Expected behavior | Status |
|---|---|---|
| K-01 Welcome | No login, account, subscription or payment; generate unique OTP | Design only |
| K-02 Pairing / transparency contract | Explain what Parent can see and why; allow child to understand linking | Design only |
| K-03 Permission onboarding | Request only permissions needed for a feature, one at a time with rationale | Design contains “Allow All Permissions” / 20-of-20 language; must not imply Android can grant all in one tap |
| K-03/K-04 Home | Show honest linked/unlinked, online/offline, permissions, sync and privacy status | Design only |
| K-05 Safety | SOS hold action, check-in, trusted contacts, clear call/help links | Design only; no real SOS API/FCM dispatch |
| K-06 Privacy | Visible sharing state, pause/request review where policy allows, data transparency | Design only |
| K-07 Help | Parent contacts, troubleshooting, support and diagnostics | Design only |
| K-08 Settings/status | permission status, reconnect, update, transparency, app health | Design only |
| K-08a Lock overlay | Only lawful, user-visible, parent-authorized lock/grace behavior; emergency exit must remain available | Design only; no native implementation |

Kids must remain usable without purchase. Linking/payment only changes protection entitlement; it must not create a Kids login or charge the child.

## 7. Owner Mode — step-by-step audit

Public entry:
- Add only the neutral label `अन्य` to role selection. Do not show Owner/Admin/Master Login publicly.
- Tapping it opens the Owner gate.

Owner gate:
- Show User ID and Password boxes.
- User ID optional; blank User ID + correct Permanent Master Password must authenticate.
- Wrong password, wrong User ID + password, and all other failures return exactly `Invalid User ID or Password.`
- Do not distinguish which field failed.
- Only the three latest `#` bootstrap password choices are valid; remove old `@` and numeric alternatives from UI/docs.
- Keep BCrypt hashes server-side only; rotate immediately after first secure Owner login/deployment.
- The current Owner HTML gate says minimum 12 characters, but the supplied bootstrap choices are 11 characters; bootstrap validation must allow the pre-provisioned choices and enforce a stronger minimum for a newly rotated password.

Current backend:
- `POST /v1/private/session`: optional User ID, required password, BCrypt hash comparison, generic failure message.
- Password hashes stored in database; no plaintext bootstrap password in Kotlin source.
- Successful session token is random, stored hashed, and expires in 20 minutes.
- `POST /v1/private/password`: session-gated password rotation; new password 12–128 characters; revokes all sessions after rotation.

Owner backend gaps:
- Owner Dashboard / ADMIN-01 and ADMIN-05 APIs are not implemented.
- No server-side API for code management, account management, device reset, remote config, audit log retrieval, payment gateway configuration, pricing management, or feature flags.
- No owner device whitelist / new-device step-up flow from the original security spec.
- Login rate limit currently allows 8 attempts per IP hash per 15 minutes, while the source security spec says 5; select and enforce one consistent policy.
- Owner login attempts are recorded, but the separate `owner_auth_audit` table in V001 is not populated by the current auth code.
- Owner UI requires a username visually; make it optional and preserve generic error behavior.

## 8. Android permission and privacy audit

The source permission declarations contain 38 unique permissions in the full declaration and 30 in the production manifest sample. High-impact items include:
- Fine/coarse/background location, activity recognition.
- Usage stats, package visibility/query-all-packages.
- Accessibility service, overlay, notification listener, device admin.
- Camera and microphone.
- Phone state, call phone, SMS read/send/receive, contacts.
- Boot receiver, foreground services, battery optimization exemption, exact alarms, Wi-Fi/Bluetooth.

These are not blanket permissions. Before implementing each one:
1. Map it to one visible user feature and explain necessity.
2. Request only when that feature is activated.
3. Use Android runtime/system Settings consent flows; never claim a one-tap grant of special access.
4. Allow denial and provide a reduced-functionality state wherever possible.
5. Do not covertly capture camera, microphone, messages, calls, keystrokes or screen content.
6. Show foreground notification/indicator where Android requires it.
7. Keep SOS and check-in explicit, user-triggered actions.
8. Review Google Play policy and Data Safety declarations before requesting sensitive permissions.
9. Do not claim services are immune to OEM task killing or that remote commands can grant Android permissions.
10. Explain battery, network, Doze and location accuracy limitations honestly.

## 9. Backend and data layer audit

Current implemented foundation:
- PostgreSQL migrations V001–V004.
- Parent/Kids device bootstrap and bearer-token hash storage.
- Kids OTP generation/rotation; Parent OTP validation and attempt throttling.
- Pairing sessions, plan catalogue query.
- Razorpay order creation and server-side payment signature + payment-resource verification.
- Unique Permit issuance and binding in a transaction after captured payment.
- Kids reconnect; Parent device and purchase history.
- Owner password-only auth, generic error, BCrypt hash storage, short-lived sessions and password rotation.

Missing before production:
- Android client integration and encrypted Android Keystore storage.
- Real deployed PostgreSQL, migration runner, plan data and backups.
- Razorpay sandbox/live verification and webhook signature/idempotency.
- FCM registration, SOS delivery, WebSocket telemetry, location/history ingestion, offline queue APIs, reports/export and retention workers.
- Owner admin APIs and screens.
- Rate limits, monitoring, audit log coverage, secrets rotation and production HTTPS configuration.
- Database integration tests, API contract tests, payment failure/refund/chargeback tests and concurrency tests.

## 10. Source inconsistencies requiring resolution

| Source conflict | Conflicting material | Decision for this implementation |
|---|---|---|
| Parent login | Role selector says “Requires Login”; Navigation Map routes to P-02; delayed-auth spec prompts login code after 10-min demo | Latest user rule: no Parent login and no delayed activation gate |
| Kids pairing | Some older screens ask Kids to enter code; latest user rule says Kids generates OTP | Kids generates; Parent enters |
| Owner ID | Gate visually asks username; backend allows blank | Keep field visible but optional |
| Owner password | Older docs list `@` and numeric codes; latest user gives `#` options | Only latest three `#` values |
| Permit bypass | ADMIN-07 says Permit-free bypass; payment-gated spec says 1 Permit = 1 device | No bypass; every Kids device needs own verified purchase |
| APK count | Single-APK architecture says one package; release checklist builds separate Parent and Kids APK/AABs | One Android application / one APK |
| Retention | 24-hour auto-delete, 14-day default, and 7/14/30/90-day options coexist | Must be resolved; do not silently pick |
| Permissions | “20/20 Allow All” and lockout vs Android per-feature/special access consent | Request individually; no forced blanket grant |
| Screen counts | Inventory says 18 primary entries; final audit says 28; delivery checklist says 33; ZIP has 110 rendered screen folders | Treat ZIP's 110 folders as design assets; map all before native implementation |

## 11. Current real verification status

| Check | Evidence | Result |
|---|---|---|
| ZIP recovered and inspected | 507 entries; 110 screen folders; 9 Three.js demos | Passed |
| Screen/HTML static audit | Counts above; 418 placeholder links | Audit only; not app test |
| Backend compilation | Latest GitHub Actions run 44904091349, job “Compile backend” success | Passed |
| Android compilation | No Android module in repository | Not run |
| PostgreSQL integration | No deployed DB/integration test run | Not run |
| Razorpay sandbox/live payment | No configured gateway test | Not run |
| OTP + real device pairing | No Android client | Not run |
| Owner UI/dashboard authorization | No Owner dashboard APIs/UI | Not run |
| Permission tests | No Android app/manifest in repository | Not run |
| Emulator / physical phone | No APK or test artifact | Not run |
| APK signing / install / upgrade | No APK or signing config in repository | Not run |

A green backend compile is not proof of runtime backend correctness, a working Android app, or a tested APK. ZIP test reports are source documents and are not independent evidence that this GitHub repository's current app passed those tests.

## 12. Recommended execution order (do not mark complete prematurely)

1. Freeze the latest role-routing and no-login rules.
2. Map all 110 screens to the single-APK routes and identify obsolete/conflicting screens.
3. Build the Android Gradle project, single app module, Compose theme/components and navigation.
4. Implement Splash → role selection → direct Parent Dashboard / Kids setup / `अन्य` Owner gate.
5. Implement Parent navigation, child profiles, pairing, plans, checkout, Permit status and purchase history.
6. Implement Kids free onboarding, OTP generation, transparent permission screens, SOS/check-in, privacy, help and settings.
7. Implement Owner gate, Owner Dashboard, password rotation, code/account/device/pricing/config/audit controls with server authorization.
8. Complete backend plans, webhooks, device-token/Keystore handling, FCM/WebSocket, telemetry, offline queue, history/export and retention.
9. Implement feature-scoped Android permission flows and OEM/device compatibility handling.
10. Run unit, API, DB, payment sandbox, navigation, accessibility, permission, offline, security and concurrency tests.
11. Build one release APK; verify signature, install, upgrade and run end-to-end tests on emulator and physical devices.
12. Only after every required test passes, mark release complete.

**Current conclusion:** Source/design audit performed. Backend compile passed. The Android app, approved UI integration, full backend features, real integrations, and APK testing remain incomplete. Do not claim “Complete” until steps 1–11 are evidenced.
