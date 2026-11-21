# Pairing, billing and Permit API contract

All endpoints are versioned under `/v1`. Production traffic requires HTTPS.

## Device bootstrap
- `POST /v1/kids/bootstrap` — Kids app sends an installation public-key fingerprint; server returns a device ID, one-time device credential and current pairing OTP. Kids use is free.
- `POST /v1/kids/pairing-otp/rotate` — authenticated Kids device rotates its short-lived OTP.
- `POST /v1/parent/bootstrap` — Parent app registers an installation identity and receives a device-scoped Parent credential. No user login fields are collected.

## Pairing and plan discovery
- `POST /v1/parent/pairing/validate` — Parent credential + OTP. Returns a short-lived pairing-session token and target Kids device summary. It does not create a binding or Permit.
- `GET /v1/parent/plans` — requires a valid pairing session; returns only active plans with amount, currency, duration unit/value, history retention and explicit capability list.
- `POST /v1/parent/payments/orders` — requires Parent + pairing-session credentials; creates a gateway order for exactly one Kids device and one plan.

## Payment and Permit
- `POST /v1/parent/payments/verify` — accepts gateway order/payment IDs and gateway signature. Server verifies signature and independently checks the gateway payment record. Client assertion alone is ignored.
- Webhook callback processing is not implemented yet. Payment verification currently uses a server-verified Razorpay signature plus an independent server-to-server payment lookup.
- A successful verification transaction creates one Permit and one binding. Unique constraints prevent one Permit from binding to multiple Kids devices.
- `GET /v1/parent/devices` — lists devices and Permit/binding status for this Parent installation.
- `GET /v1/parent/purchases` — returns payment and Permit history.
- `POST /v1/kids/reconnect` — Kids device credential reconnects if its Permit and binding remain valid; OTP is not requested again.

## Owner
- `POST /v1/private/session` — password required, User ID optional; returns a short-lived Owner session. All failed credential combinations return the same generic error.
- `/v1/private/**` — requires Owner session. Parent/Kids credentials are rejected.

## Error semantics
- `OTP_INVALID`, `OTP_EXPIRED`, `OTP_RATE_LIMITED`
- `PLAN_UNAVAILABLE`, `PAYMENT_GATEWAY_NOT_CONFIGURED`, `PAYMENT_NOT_CAPTURED`, `PAYMENT_MISMATCH`
- `PERMIT_ALREADY_BOUND`, `BINDING_EXPIRED`, `BINDING_REVOKED`
- `OWNER_AUTH_FAILED`, `OWNER_SESSION_REQUIRED`

Never return a Permit before verified payment. Never treat a UPI intent result or a manually entered UTR as payment verification.
