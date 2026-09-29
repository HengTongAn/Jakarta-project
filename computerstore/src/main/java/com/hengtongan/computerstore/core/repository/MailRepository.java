package com.hengtongan.computerstore.core.repository;

import com.hengtongan.computerstore.core.domain.entity.MailMessage;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MailRepository {

    private static final String SELECT_WITH_NAMES =
            "SELECT m.message_id, m.sender_id, m.recipient_id, m.subject, m.body, "
                    + "m.read_flag, m.created_at, "
                    + "s.full_name AS sender_name, s.username AS sender_username, s.avatar_url AS sender_avatar_url, "
                    + "r.full_name AS recipient_name, r.username AS recipient_username, r.avatar_url AS recipient_avatar_url "
                    + "FROM mail_messages m "
                    + "JOIN users s ON s.user_id = m.sender_id "
                    + "JOIN users r ON r.user_id = m.recipient_id";

    private MailMessage mapRow(ResultSet rs) throws SQLException {
        MailMessage m = new MailMessage();
        m.setMessageId(rs.getInt("message_id"));
        m.setSenderId(rs.getInt("sender_id"));
        m.setRecipientId(rs.getInt("recipient_id"));
        m.setSubject(rs.getString("subject"));
        m.setBody(rs.getString("body"));
        m.setReadFlag(rs.getBoolean("read_flag"));
        m.setCreatedAt(rs.getTimestamp("created_at"));
        m.setSenderName(rs.getString("sender_name"));
        m.setSenderUsername(rs.getString("sender_username"));
        m.setSenderAvatarUrl(rs.getString("sender_avatar_url"));
        m.setRecipientName(rs.getString("recipient_name"));
        m.setRecipientUsername(rs.getString("recipient_username"));
        m.setRecipientAvatarUrl(rs.getString("recipient_avatar_url"));
        return m;
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    public int create(MailMessage message) {
        String sql = "INSERT INTO mail_messages (sender_id, recipient_id, subject, body) VALUES (?, ?, ?, ?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, message.getSenderId());
            ps.setInt(2, message.getRecipientId());
            ps.setString(3, message.getSubject());
            ps.setString(4, message.getBody());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    message.setMessageId(id);
                    return id;
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException("Error creating mail message", e);
        }
    }

    public MailMessage findById(int messageId) {
        String sql = SELECT_WITH_NAMES + " WHERE m.message_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding mail message", e);
        }
        return null;
    }

    public List<MailMessage> findBySender(int senderId) {
        String sql = SELECT_WITH_NAMES + " WHERE m.sender_id = ? ORDER BY m.created_at DESC, m.message_id DESC";
        List<MailMessage> messages = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, senderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing sent mail messages", e);
        }
        return messages;
    }

    public List<MailMessage> findByRecipient(int recipientId, Boolean readOnly) {
        StringBuilder sql = new StringBuilder(SELECT_WITH_NAMES)
                .append(" WHERE m.recipient_id = ?");
        if (readOnly != null) {
            sql.append(" AND m.read_flag = ").append(readOnly ? "1" : "0");
        }
        sql.append(" ORDER BY m.created_at DESC, m.message_id DESC");
        List<MailMessage> messages = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setInt(1, recipientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing inbox mail messages", e);
        }
        return messages;
    }

    public boolean setRead(int messageId, boolean read) {
        String sql = "UPDATE mail_messages SET read_flag = ? WHERE message_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, read);
            ps.setInt(2, messageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error updating mail read flag", e);
        }
    }

    public void markAllRead(int recipientId) {
        String sql = "UPDATE mail_messages SET read_flag = 1 WHERE recipient_id = ? AND read_flag = 0";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, recipientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error marking all mail read", e);
        }
    }

    public int countUnread(int recipientId) {
        String sql = "SELECT COUNT(*) FROM mail_messages WHERE recipient_id = ? AND read_flag = 0";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, recipientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting unread mail messages", e);
        }
        return 0;
    }
}
