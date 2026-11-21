# Stitch UI/UX Source Audit — FAMILY SAFETY

## Source file recovered

- Library file: `stitch_family_safety_ui_ux_design_system (2).zip`
- Original size: 26,753,820 bytes
- Archive root: `stitch_family_safety_ui_ux_design_system/`
- Archive entries: 386
- HTML screen/code artifacts: 119
- PNG screen renders: 110
- Primary inventory: `screen_inventory.json` — 18 screens marked Created & Rendered

The archive was recovered from the user's existing ChatGPT Library upload on 2026-11-21. No re-upload is required.

## Primary screen inventory (source order)

| ID | Screen |
|---|---|
| P-01 | Parent App — Splash & App Launch |
| P-02 | Parent App — Common Unified Login (also shared with Owner in original design) |
| P-04 | Parent App — First-Run Onboarding |
| P-05 | Parent App — Home Dashboard |
| P-06 | Parent App — Child Profile Details |
| P-08 | Parent App — Devices & Diagnostics |
| P-09 | Parent App — Pair Device (QR & Ephemeral Code) |
| P-10 | Parent App — Location & Safe Places |
| P-11/12 | Parent App — Activity, Screen Time & Routines |
| P-13 | Parent App — Alerts & Emergency Centre |
| P-14 | Parent App — Reports & PDF/CSV Export |
| P-15 | Parent App — Settings Hub |
| P-16 | Parent App — Account & Security |
| K-01/02 | Kids App — Welcome, Pairing & Transparent Contract |
| K-03/04 | Kids App — Transparent Home & Permissions |
| K-05 | Kids App — Safety & Emergency SOS |
| K-06 | Kids App — Privacy & Sharing Transparency |
| ADMIN-01 | Owner App — Admin Dashboard & Code Manager |

Additional HTML/rendered screen variants in the archive include: parent offline sync, backup/restore, family guardian approvals, privacy/data governance, notifications, raw media gallery, multi-child fleet, system compatibility, guest access, history controls, WhatsApp support, owner master-password gate, owner release management, owner pricing/payment methods, owner settings, owner public-account/code console, owner module registry, and Kids transparency/lock/permissions/reconnect variants. The archive also includes Kotlin/SQL/API specifications, test reports, and release checklists.

## Visual design tokens read from source

The source defines the Aegis Guardian Material 3 design system:
- Brand navy: `#1E3A8A`; primary blue: `#2563EB`; light blue: `#38BDF8`; teal accent: `#0D9488`.
- Light surface: `#F8FAFC`; light text: `#0F172A`; muted text: `#475569`.
- Dark surface: `#0B1329`; dark text: `#F8FAFC`; muted text: `#94A3B8`.
- Safety green: `#10B981`; warning amber: `#F59E0B`; emergency red: `#EF4444`.
- Inter/Sans Serif typography; Material 3; light/dark schemes; system dynamic colors disabled by default to preserve brand identity.

## Visual review samples

- Role selector: `family_safety_role_selection_single_app/screen.png`
- Parent dashboard: `parent_app_home_dashboard/screen.png`
- Pairing screen: `parent_app_pair_device_p_09/screen.png`
- Owner gate: `owner_master_password/screen.png`
- Kids permission onboarding: `kids_app_k_03_allow_all_permissions_setup/screen.png`

The role selector presents two prominent cards (Parent/Guardian and Child Device), language switch, and a single-app architecture explanation. The Parent dashboard contains child cards, alert/perimeter card, quick actions, schedule card, and bottom navigation. The pairing screen uses a short numeric code/QR with expiry, refresh and cancel controls. The Owner gate has username and master-password fields, lockout telemetry and authentication controls. Kids setup uses a transparent, sequential permission checklist.

## Integration precedence and source conflicts

The archive is the visual source of truth. The user's later explicit functional requirements in the conversation take precedence over older text or labels embedded in the archive:

1. One Android application and one APK with separated Parent, Kids and private Owner experiences.
2. Parent Mode opens directly without email, username or password. Therefore the P-02 "Requires Login"/shared login behavior must not be enforced; retain the approved layout where possible while removing or neutralizing the conflicting login requirement.
3. Kids Mode remains free and login-free; it generates the linking OTP. Parent enters that OTP.
4. Plans, payment verification, unique per-Kids-device Permit and binding happen after OTP verification; reconnect uses an existing valid binding.
5. Owner entry is only the neutral public label "अन्य"; Owner dashboard requires server-side authentication. The Owner User ID field may be left blank; password-only success is allowed, with one generic invalid-credentials message.
6. Keep Kids privacy/transparency visible. Do not implement covert camera/microphone/keystroke capture or stealth surveillance. The source's privacy architecture explicitly rejects hidden camera/mic/keystroke surveillance.

## Packaging / implementation notes

- HTML files depend on Tailwind CDN, Google Fonts/Material Symbols, and in some cases remote image URLs. To guarantee offline/low-network visual consistency, these dependencies must be bundled or replaced with local equivalents before release.
- The archive's HTML pages are design references, not a complete Android application: screen navigation, data state, permission requests, backend calls, secure token storage and platform lifecycle behavior still need native integration.
- The source includes examples and test/simulation reports; a simulation report is not evidence that this repository's current Android APK has passed a real device test.
- The archive's legacy Gradle example targets older Android/Compose versions and must be reconciled with current Android SDK and Play policy before production.
- The design package contains older flows that conflict with the user's latest business rules (notably Parent login). Implement the latest rules without redesigning the approved visual composition.

## Verification status

This document records source recovery and inspection only. It does not claim that the Android client, all UI interactions, production backend, payment webhook, release APK, signing, emulator/device testing, or production deployment are complete.
