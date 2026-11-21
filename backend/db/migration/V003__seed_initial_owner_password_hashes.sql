-- Initial Owner password set requested by the repository owner.
-- BCrypt cost 12; no plaintext passwords are stored.
-- These credentials are publicly known and must be rotated after initial setup.
INSERT INTO owner_passwords(password_hash, active)
VALUES
('$2a$12$TJsnhE23.6W7zaDgSv/5Pu/PVQqcaDRa/JPSX7RsqaguL.cO7jDPu', TRUE),
('$2a$12$gfS6Q5TGb7XAECC/KI3I1OrENsgBvzsyZ70VE2jFnmUb0HvOtMHFW', TRUE),
('$2a$12$tBXt6iXudUFMpznCBIAk/.EIFfmUkP6zCc/ZTuRFsKA9ABJ2fsw7S', TRUE)
ON CONFLICT DO NOTHING;
