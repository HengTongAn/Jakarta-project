-- Adds the wrong-guess counter that makes the 6-digit reset code brute-force
-- resistant. Without it the code space (1,000,000) is small enough to grind
-- one guess at a time; PasswordResetService charges a failed attempt here and
-- burns the code once MAX_CODE_ATTEMPTS is reached.
--
-- DEFAULT 0 keeps the column additive for rows created before this migration:
-- an existing outstanding code simply starts its counter at zero.
ALTER TABLE password_reset_codes
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
