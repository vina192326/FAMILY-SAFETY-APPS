ALTER TABLE parent_history_preferences
  ADD COLUMN IF NOT EXISTS offline_retention_hours INTEGER NOT NULL DEFAULT 168
  CHECK (offline_retention_hours IN (24, 168));
