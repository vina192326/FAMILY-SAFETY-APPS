CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_devices (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  role VARCHAR(12) NOT NULL CHECK (role IN ('PARENT','KIDS')),
  install_fingerprint VARCHAR(128) NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(role, install_fingerprint)
);

CREATE TABLE pairing_otps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  kids_device_id UUID NOT NULL REFERENCES app_devices(id) ON DELETE CASCADE,
  otp_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pairing_otp_device ON pairing_otps(kids_device_id, expires_at DESC);

CREATE TABLE subscription_plans (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(100) NOT NULL,
  amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
  currency CHAR(3) NOT NULL DEFAULT 'INR',
  duration_value INTEGER NOT NULL CHECK (duration_value > 0),
  duration_unit VARCHAR(10) NOT NULL CHECK (duration_unit IN ('MINUTES','HOURS','DAYS','MONTHS','YEARS')),
  history_days INTEGER NOT NULL DEFAULT 0 CHECK (history_days >= 0),
  capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pairing_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  parent_device_id UUID NOT NULL REFERENCES app_devices(id),
  kids_device_id UUID NOT NULL REFERENCES app_devices(id),
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(id, parent_device_id, kids_device_id)
);

CREATE TABLE payment_orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  parent_device_id UUID NOT NULL REFERENCES app_devices(id),
  kids_device_id UUID NOT NULL REFERENCES app_devices(id),
  pairing_session_id UUID NOT NULL REFERENCES pairing_sessions(id),
  plan_id UUID NOT NULL REFERENCES subscription_plans(id),
  gateway_order_id VARCHAR(100) UNIQUE,
  gateway_payment_id VARCHAR(100) UNIQUE,
  amount_paise BIGINT NOT NULL,
  currency CHAR(3) NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED','AUTHORIZED','CAPTURED','FAILED','REFUNDED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  verified_at TIMESTAMPTZ
);

CREATE TABLE permits (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  public_code VARCHAR(48) NOT NULL UNIQUE,
  kids_device_id UUID NOT NULL REFERENCES app_devices(id),
  parent_device_id UUID NOT NULL REFERENCES app_devices(id),
  plan_id UUID NOT NULL REFERENCES subscription_plans(id),
  payment_order_id UUID NOT NULL UNIQUE REFERENCES payment_orders(id),
  starts_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_permits_device_validity ON permits(kids_device_id, expires_at DESC);

CREATE TABLE device_bindings (
  kids_device_id UUID PRIMARY KEY REFERENCES app_devices(id),
  parent_device_id UUID NOT NULL REFERENCES app_devices(id),
  permit_id UUID NOT NULL UNIQUE REFERENCES permits(id),
  bound_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ
);

CREATE TABLE owner_auth_audit (
  id BIGSERIAL PRIMARY KEY,
  success BOOLEAN NOT NULL,
  remote_ip INET,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_orders_status ON payment_orders(status);
CREATE INDEX idx_pairing_sessions_expiry ON pairing_sessions(expires_at);
