CREATE TABLE IF NOT EXISTS owner_passwords (
  id BIGSERIAL PRIMARY KEY,
  password_hash TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  retired_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS owner_password_hash_active_unique
  ON owner_passwords(password_hash) WHERE active = TRUE;

CREATE TABLE IF NOT EXISTS owner_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  token_hash CHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS owner_sessions_expiry_idx ON owner_sessions(expires_at);

CREATE TABLE IF NOT EXISTS owner_login_attempts (
  id BIGSERIAL PRIMARY KEY,
  ip_hash CHAR(64) NOT NULL,
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  success BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS owner_login_attempts_rate_idx ON owner_login_attempts(ip_hash, attempted_at DESC);
