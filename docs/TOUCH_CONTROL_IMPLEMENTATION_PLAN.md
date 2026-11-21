# Touch Control — consent-first implementation plan

## Current status

**Not enabled in the APK.** The current Android source does not contain a Touch Control screen, an AccessibilityService, a command API, or a server-side command queue. Do not advertise remote Tap/Swipe/Scroll as working until the full acceptance gate below passes.

This document defines a safe implementation boundary. It is not evidence that the feature is implemented.

## Intended user flow

1. Parent opens the linked Kids device and sends a **Touch session request**.
2. The Kids device receives a normal, visible notification and an in-app request card showing the requesting guardian, requested actions, and session duration.
3. The child/device user must tap **Allow this session** on the Kids device. No approval is inferred from pairing, a Permit, or an earlier session.
4. The Kids screen shows a persistent active-session banner and a prominent **Stop Touch Control** action. The Android notification remains visible while the session is active.
5. Parent can send only the allow-listed commands: Tap, Swipe, Scroll, and Back. Each command is shown in the Kids session UI and is subject to validation and acknowledgement.
6. The session expires automatically after at most 10 minutes. Either side can stop it immediately. Reconnection never silently resumes a session.

## Hard safety boundaries

- No screen capture, screen streaming, microphone, camera, keylogging, text extraction, or reading content from other apps.
- No hidden Accessibility activation. The device user must explicitly enable any Android Accessibility service in Android Settings after reading an in-app disclosure; the app must never navigate there and enable it silently.
- No actions on Android permission dialogs, payment screens, password fields, banking/financial apps, emergency-call UI, or other protected/sensitive surfaces.
- No arbitrary coordinates from an unauthenticated or stale command. Commands must be bound to the linked Kids device, active Permit, approved session ID, expiry, nonce/sequence, and authenticated Parent.
- Do not accept commands after session expiry, stop, device unlink, Permit revoke, token revoke, or guardian-role revoke.
- Every request, approval, command, acknowledgement, expiry, and stop event must be audit logged without storing screen contents or sensitive text.
- Kids device remains the final authority and can reject any command.

## Proposed API contract (not yet implemented)

- `POST /v1/parent/touch-sessions`: Parent requests a session for a linked Kids device. Server validates Parent/Kids relationship, active Permit, guardian role and rate limits. Returns a pending request ID and expiry.
- `GET /v1/kids/touch-sessions/pending`: Kids polls for pending requests using its device token.
- `POST /v1/kids/touch-sessions/{id}/decision`: Kids approves or rejects. Approval creates a short-lived session with a maximum 10-minute expiry.
- `GET /v1/kids/touch-sessions/{id}/commands`: Kids fetches commands only for its currently approved session.
- `POST /v1/parent/touch-sessions/{id}/commands`: Parent submits one allow-listed command with monotonic sequence number, nonce, bounded coordinates/duration and short TTL.
- `POST /v1/kids/touch-sessions/{id}/ack`: Kids acknowledges accepted/rejected/executed command.
- `POST /v1/(parent|kids)/touch-sessions/{id}/stop`: Either side stops; server invalidates queued commands immediately.

All endpoints require HTTPS, authenticated device tokens, active Permit, relationship/role checks, strict request validation, replay protection, rate limits and audit events. Do not implement the client against these routes until backend routes, persistence, tests and deployment are present.

## Android implementation requirements

- Parent UI: request, pending, approved, active, expired, rejected and stopped states; show the linked Kids device name and session expiry.
- Kids UI: request details, explicit Allow/Reject, visible active banner, Stop button, command activity log and timeout.
- If AccessibilityService is eventually approved as the platform mechanism, declare only the minimum capability, provide a plain-language disclosure, require manual system enablement, avoid window-content retrieval, and dispatch only allow-listed gestures during the approved session. Never enable it by default.
- A non-accessibility in-app-only mode is preferred where it can satisfy the use case.
- Keep the feature unavailable if the backend is offline or authorization cannot be revalidated.

## Acceptance gate

Do not mark Touch Control complete until:
1. Backend schema, endpoints, authorization, replay protection, rate limits, audit logging and automated tests are merged.
2. Parent and Kids screens are integrated and visibly expose every session state.
3. Android service behavior is reviewed against current Android and Google Play Accessibility policies.
4. Tests prove that no command executes without a fresh Kids approval, after stop/expiry/revoke, or against a different device/session.
5. Build, emulator tests and physical-device tests pass on supported Android versions.

## Present implementation decision

The existing Kids permission wizard's `ACCESSIBILITY_CONTROL` row correctly remains **Not requested**. Do not change it to granted or add an Accessibility permission prompt until the complete, disclosed, consent-first flow is implemented and reviewed.
