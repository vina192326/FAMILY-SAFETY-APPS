CREATE TABLE kid_permission_status (
  kids_device_id UUID NOT NULL REFERENCES app_devices(id) ON DELETE CASCADE,
  permission_key VARCHAR(64) NOT NULL,
  granted BOOLEAN NOT NULL,
  required BOOLEAN NOT NULL DEFAULT FALSE,
  detail VARCHAR(240),
  last_checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(kids_device_id, permission_key)
);
CREATE INDEX idx_kid_permission_status_device ON kid_permission_status(kids_device_id, last_checked_at DESC);
