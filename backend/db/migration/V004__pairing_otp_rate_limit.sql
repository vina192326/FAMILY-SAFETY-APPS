CREATE TABLE IF NOT EXISTS pairing_otp_attempts (
  id BIGSERIAL PRIMARY KEY,
  parent_device_id UUID NOT NULL REFERENCES app_devices(id),
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS pairing_otp_attempts_rate_idx
  ON pairing_otp_attempts(parent_device_id, attempted_at DESC);
