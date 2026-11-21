# Dynamic History Retention — Implementation Contract

## Product behavior

1. A Kids device uploads permitted history only while its binding and Permit are valid.
2. History records are encrypted at rest with AES-256-GCM using a dedicated server secret.
3. If Parent has not viewed a record, it is eligible for deletion 7 days after server receipt.
4. The first authenticated Parent history read marks currently visible records as viewed and sets a fixed delete deadline of exactly 24 hours later. Reopening or refreshing does not extend the deadline.
5. Parent sees a persistent warning: **“Data will auto-delete in 24 hours”** immediately after first view, with a live remaining-time value derived from the server's `deleteAt`.
6. A Parent may delete any item immediately. The server deletes its encrypted payload and keeps only a minimal deletion audit entry.
7. The P-15 setting **“📄 Save History as PDF Before Auto-Delete”** is persisted per Parent installation.
8. When that preference is ON, the Android app should create the PDF locally as soon as the Parent opens the relevant history (not at the deadline), save it to a user-visible location, and show export success/failure. The preference does not mean the server can guarantee a local file save while the device is offline.
9. Per-item Download and Share create separate Parent-controlled local copies. They do not pause or extend cloud expiry. The local copy is outside cloud retention and can be deleted by the Parent from the phone.
10. At the server deadline, encrypted cloud history is deleted regardless of whether it was downloaded/shared. Android must also purge its app-cache copy by the server-provided deadline. A user-exported copy in Downloads is not silently deleted by the app.

## Backend endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/v1/kids/history` | Ingest one encrypted history JSON record |
| GET | `/v1/parent/history?kidsDeviceId=<uuid>` | Read history and start first-view timer |
| DELETE | `/v1/parent/history/{recordId}` | Immediate Parent deletion |
| GET | `/v1/parent/history/preferences` | Read PDF-before-delete preference |
| PUT | `/v1/parent/history/preferences` | Save PDF-before-delete preference |

History payload types: LOCATION, APP_USAGE, TELEMETRY, MEDIA. Current ingestion accepts JSON payloads up to 1 MiB. MEDIA is metadata only at present; encrypted binary object storage for photo/video files is not yet integrated.

## Operational setup

Apply `V005__dynamic_history_retention.sql`. Generate a separate 32-byte key with `openssl rand -base64 32`, place it in `HISTORY_ENCRYPTION_KEY_BASE64` in a deployment secret manager, and back it up under a controlled key-management policy. Never commit the key or include it in the APK. If the key is lost, encrypted history cannot be decrypted.

The backend purge worker runs every 60 seconds. Expiry timestamps are authoritative server time; Android should schedule cache cleanup using WorkManager and re-check the server deadline whenever online. WorkManager is not an exact-alarm service, so it cannot promise a millisecond-exact local cleanup while the OS is suspended. Server-side cloud deletion is authoritative.

## Acceptance tests still required

- Unviewed record expires after seven days.
- First view sets deleteAt to viewedAt + 24h; subsequent reads do not change either timestamp.
- A different Parent device cannot read/delete the record.
- Invalid/expired Permit cannot upload.
- Tampered ciphertext fails AES-GCM authentication.
- Immediate Delete removes the payload and writes only the minimal audit row.
- PDF preference survives app restart.
- PDF is generated locally when enabled; no PDF generated when disabled.
- Download/Share does not alter deleteAt.
- Cloud purge deletes expired records and Android cache is cleared after expiry.
- Timezone, daylight-saving, process restart, offline device, and duplicate ingestion cases are tested.
- No photos/videos are accepted as binary until encrypted object storage and signed download URLs are implemented.

## Current status

Backend schema, API routes and scheduled deletion worker have been committed. The Android P-15 toggle, visible countdown, PDF generation, Downloads/Share sheet, cache purge, encrypted media storage, and real PostgreSQL/API acceptance tests are not yet implemented or verified. Do not mark this feature complete until these pass.
