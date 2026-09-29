-- In-app mail: the mailbox behind "Contact support".
--
-- schema.sql creates this table for a fresh install, but schema.sql is never
-- re-run against an existing database - only migrations are. Without this
-- file an already-provisioned store boots fine and then fails on the first
-- /mail query with "table 'mail_messages' doesn't exist", which reads as a
-- code bug rather than a missing migration.
--
-- One row per message, with no thread or reply-to column. A reply is a new row
-- whose subject is the original's prefixed with "Re: " - the compose form
-- prefills from the original but stores no link back to it. That is what makes
-- the inbox and the sent folder the same table queried two ways instead of a
-- threads table plus a participants table.
--
-- read_flag is a flag rather than a read_at timestamp: the badge and the
-- mark-all-read button only ever ask "is this unread", and countUnread() is one
-- indexed COUNT.
--
-- idx_mail_inbox and idx_mail_sent are the two access paths MailRepository
-- actually has: findByRecipient() filters on recipient_id + read_flag and both
-- listings order by created_at DESC, message_id DESC.
--
-- No ON DELETE CASCADE: MailRepository inner-joins both users, so a message
-- whose sender or recipient was hard-deleted is already invisible to every
-- query, and users are soft-deleted (deleted_at) rather than removed.
--
-- Registered in DatabaseMigrationRunner.discoverMigrations().
CREATE TABLE IF NOT EXISTS mail_messages (
    message_id   INT AUTO_INCREMENT PRIMARY KEY,
    sender_id    INT NOT NULL,
    recipient_id INT NOT NULL,
    subject      VARCHAR(200) NOT NULL,
    body         TEXT NOT NULL,
    read_flag    TINYINT(1) NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mail_sender FOREIGN KEY (sender_id) REFERENCES users (user_id),
    CONSTRAINT fk_mail_recipient FOREIGN KEY (recipient_id) REFERENCES users (user_id),
    INDEX idx_mail_inbox (recipient_id, read_flag, created_at),
    INDEX idx_mail_sent (sender_id, created_at)
) ENGINE = InnoDB;
