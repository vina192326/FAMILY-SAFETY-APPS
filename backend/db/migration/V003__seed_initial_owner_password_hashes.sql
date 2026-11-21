-- Initial Owner password set requested by the repository owner.
-- Real BCrypt cost-12 hashes. No plaintext passwords are stored in this migration.
-- These credentials were disclosed in the conversation and should be rotated after setup.
INSERT INTO owner_passwords(password_hash, active)
VALUES
('$2a$12$GC7Xs8naoIn44rdLZXQwNe7u8FVbSBYVFTvlJxCvuYB/n4J1izFb6', TRUE),
('$2a$12$oPk7qMroEqX7PpLFmgkyHuPhxG7mtcQfKk8GIVBRSJFH/o4UYjS6i', TRUE),
('$2a$12$dCQt0mTBKmJ6yQ3X2WzpC.PqKPs9seD9Q8znpyisOH9sg6InzImV2', TRUE)
ON CONFLICT DO NOTHING;
