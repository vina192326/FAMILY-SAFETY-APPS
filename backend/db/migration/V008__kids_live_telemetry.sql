CREATE TABLE kids_live_telemetry (
  kids_device_id UUID PRIMARY KEY REFERENCES app_devices(id) ON DELETE CASCADE,
  parent_device_id UUID NOT NULL REFERENCES app_devices(id) ON DELETE CASCADE,
  captured_at TIMESTAMPTZ NOT NULL,
  payload_iv BYTEA NOT NULL,
  payload_ciphertext BYTEA NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
