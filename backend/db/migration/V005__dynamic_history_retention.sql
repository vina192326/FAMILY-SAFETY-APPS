CREATE TABLE history_records (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  kids_device_id UUID NOT NULL REFERENCES app_devices(id) ON DELETE CASCADE,
  parent_device_id UUID NOT NULL REFERENCES app_devices(id) ON DELETE CASCADE,
  record_type VARCHAR(24) NOT NULL CHECK (record_type IN ('LOCATION','APP_USAGE','TELEMETRY','MEDIA')),
  occurred_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  viewed_at TIMESTAMPTZ,
  delete_at TIMESTAMPTZ NOT NULL,
  payload_iv BYTEA NOT NULL,
  payload_ciphertext BYTEA NOT NULL,
  deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_history_parent_visible ON history_records(parent_device_id, kids_device_id, occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_history_expiry ON history_records(delete_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_history_unviewed_retention ON history_records(received_at) WHERE deleted_at IS NULL AND viewed_at IS NULL;

CREATE TABLE parent_history_preferences (
  parent_device_id UUID PRIMARY KEY REFERENCES app_devices(id) ON DELETE CASCADE,
  save_pdf_before_delete BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE history_deletion_audit (
  id BIGSERIAL PRIMARY KEY,
  record_id UUID NOT NULL,
  parent_device_id UUID NOT NULL,
  reason VARCHAR(24) NOT NULL CHECK (reason IN ('AUTO_EXPIRED','PARENT_DELETED','DEVICE_UNLINKED')),
  deleted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_history_audit_parent ON history_deletion_audit(parent_device_id, deleted_at DESC);
