package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.repository.MailRepository;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.MailMessage;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.infrastructure.cache.CacheManager;
import com.hengtongan.computerstore.infrastructure.messaging.EmailUtil;
import com.hengtongan.computerstore.util.validation.ValidationUtil;

import java.util.List;

/**
 * Gmail-style in-app mail: inbox, sent, compose, reply and read/unread toggling.
 */
public class MailService {

    public static final int SUBJECT_MAX = 200;

    private final MailRepository mailDAO = new MailRepository();
    private final UserService userService = new UserService();

    public MailMessage send(int senderId, int recipientId, String subject, String body) {
        if (senderId == recipientId) {
            throw new ValidationException("You cannot send a message to yourself.");
        }
        if (ValidationUtil.isBlank(subject) || subject.trim().length() > SUBJECT_MAX) {
            throw new ValidationException("Subject is required and must be under " + SUBJECT_MAX + " characters.");
        }
        if (ValidationUtil.isBlank(body)) {
            throw new ValidationException("Message body is required.");
        }
        User sender = userService.get(senderId);
        User recipient = userService.get(recipientId);

        MailMessage message = new MailMessage();
        message.setSenderId(senderId);
        message.setRecipientId(recipientId);
        message.setSubject(subject.trim());
        message.setBody(body.trim());
        mailDAO.create(message);

        invalidateUnread(recipientId);
        notifyByEmail(recipient, message);
        return message;
    }

    public MailMessage replyTo(int userId, int originalId, String body) {
        MailMessage original = getMessageForUser(originalId, userId);
        if (ValidationUtil.isBlank(body)) {
            throw new ValidationException("Message body is required.");
        }
        int peerId = original.getSenderId() == userId ? original.getRecipientId() : original.getSenderId();
        if (peerId == userId) {
            throw new ValidationException("You cannot reply to your own message.");
        }

        MailMessage message = new MailMessage();
        message.setSenderId(userId);
        message.setRecipientId(peerId);
        message.setSubject("Re: " + original.getSubject());
        message.setBody(body.trim());
        mailDAO.create(message);

        invalidateUnread(peerId);
        notifyByEmail(userService.get(peerId), message);
        return message;
    }

    public List<MailMessage> getInbox(int userId) {
        return getInbox(userId, null);
    }

    public List<MailMessage> getInbox(int userId, Boolean readOnly) {
        return mailDAO.findByRecipient(userId, readOnly);
    }

    public List<MailMessage> getSent(int userId) {
        return mailDAO.findBySender(userId);
    }

    public MailMessage toggleRead(int id, int userId) {
        MailMessage message = getMessageForUser(id, userId);
        // Only the recipient owns the unread/read state.
        if (message.getRecipientId() != userId) {
            throw new ValidationException("Only the recipient can change read status.");
        }
        message.setReadFlag(!message.isReadFlag());
        mailDAO.setRead(id, message.isReadFlag());
        invalidateUnread(userId);
        return message;
    }

    public void markAllRead(int userId) {
        mailDAO.markAllRead(userId);
        invalidateUnread(userId);
    }

    public MailMessage getMessageForUser(int id, int userId) {
        MailMessage message = mailDAO.findById(id);
        if (message == null || (message.getSenderId() != userId && message.getRecipientId() != userId)) {
            throw new NotFoundException("Message not found.");
        }
        return message;
    }

    /**
     * Unread inbox count for nav badges. Memoized (single-flight) so rapid
     * page clicks do not each open a DB connection.
     */
    public int countUnread(int userId) {
        if (userId <= 0) {
            return 0;
        }
        if (CacheManager.isCacheEnabled()) {
            Object memo = CacheManager.getOrLoadCart(mailUnreadKey(userId),
                    k -> mailDAO.countUnread(userId));
            return memo instanceof Integer ? (Integer) memo : 0;
        }
        return mailDAO.countUnread(userId);
    }

    private static void invalidateUnread(int userId) {
        CacheManager.invalidateCart(mailUnreadKey(userId));
    }

    private static String mailUnreadKey(int userId) {
        return "mail_unread_" + userId;
    }

    private void notifyByEmail(User recipient, MailMessage message) {
        if (!EmailUtil.isConfigured()) {
            return;
        }
        EmailUtil.send(recipient.getEmail(), message.getSubject(), message.getBody());
    }
}
