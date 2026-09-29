package com.hengtongan.computerstore.core.repository;

import com.hengtongan.computerstore.core.domain.entity.RatingSummary;
import com.hengtongan.computerstore.core.domain.entity.Review;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import com.hengtongan.computerstore.util.web.ErrorHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for customer reviews.
 *
 * <p>Public reads return only {@code APPROVED} reviews so a pending or rejected
 * submission can never leak onto the storefront. Aggregates are batched
 * ({@link #ratingSummaries(Collection)}) so catalogue cards never run one query
 * per product.</p>
 */
public class ReviewRepository {

    private static final String COLUMNS =
            "r.review_id, r.product_id, r.user_id, r.rating, r.title, r.review_text, "
            + "r.status, r.is_verified, r.created_at, r.updated_at, "
            + "p.name AS product_name, p.sku AS product_sku, "
            + "u.full_name AS user_name, u.username AS user_username";

    private static final String FROM_JOINS =
            "FROM reviews r "
            + "JOIN products p ON p.product_id = r.product_id "
            + "JOIN users u ON u.user_id = r.user_id ";

    private Review mapRow(ResultSet rs) throws SQLException {
        Review review = new Review();
        review.setReviewId(rs.getInt("review_id"));
        review.setProductId(rs.getInt("product_id"));
        review.setUserId(rs.getInt("user_id"));
        review.setRating(rs.getInt("rating"));
        review.setTitle(rs.getString("title"));
        review.setReviewText(rs.getString("review_text"));
        review.setStatus(Review.Status.valueOf(rs.getString("status")));
        review.setVerified(rs.getBoolean("is_verified"));
        review.setCreatedAt(rs.getTimestamp("created_at"));
        review.setUpdatedAt(rs.getTimestamp("updated_at"));
        review.setProductName(rs.getString("product_name"));
        review.setProductSku(rs.getString("product_sku"));
        review.setUserName(rs.getString("user_name"));
        review.setUserUsername(rs.getString("user_username"));
        return review;
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    /**
     * Inserts a review. The caller passes the connection it also used for the
     * verified-purchase check so both happen atomically.
     */
    public int create(Connection c, Review review) throws SQLException {
        String sql = "INSERT INTO reviews (product_id, user_id, rating, title, review_text, status, is_verified) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, review.getProductId());
            ps.setInt(2, review.getUserId());
            ps.setInt(3, review.getRating());
            ps.setString(4, review.getTitle());
            ps.setString(5, review.getReviewText());
            ps.setString(6, review.getStatus().name());
            ps.setBoolean(7, review.isVerified());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            return -1;
        }
    }

    /** Approved reviews for a product, newest first (what customers see). */
    public List<Review> findApprovedByProduct(int productId) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS
                + "WHERE r.product_id = ? AND r.status = 'APPROVED' "
                + "ORDER BY r.created_at DESC, r.review_id DESC";
        return queryList(sql, List.of((Object) productId));
    }

    /** The review a specific customer submitted for a product, or null. */
    public Review findByUserAndProduct(int userId, int productId) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS
                + "WHERE r.user_id = ? AND r.product_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("finding review by user and product", e);
        }
        return null;
    }

    public Review findById(int reviewId) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + "WHERE r.review_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reviewId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("finding review", e);
        }
        return null;
    }

    /**
     * True when the customer has a non-cancelled / non-refunded order that
     * contains the product. Run on the caller's connection so the purchase
     * check and the review insert commit together.
     */
    public boolean hasPurchased(Connection c, int userId, int productId) throws SQLException {
        String sql = "SELECT 1 FROM order_items oi "
                + "JOIN orders o ON o.order_id = oi.order_id "
                + "WHERE oi.product_id = ? AND o.user_id = ? "
                + "AND o.status NOT IN ('CANCELLED', 'REFUNDED') LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Average + count of approved reviews per product, one query for many
     * products (avoids the N+1 problem on catalogue pages).
     */
    public Map<Integer, RatingSummary> ratingSummaries(Collection<Integer> productIds) {
        Map<Integer, RatingSummary> summaries = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return summaries;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT product_id, ROUND(AVG(rating), 1) AS avg_rating, COUNT(*) AS review_count "
                        + "FROM reviews WHERE status = 'APPROVED' AND product_id IN (");
        for (int i = 0; i < productIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(") GROUP BY product_id");

        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            for (Integer id : productIds) {
                ps.setInt(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    summaries.put(rs.getInt("product_id"),
                            new RatingSummary(rs.getDouble("avg_rating"), rs.getLong("review_count")));
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("loading rating summaries", e);
        }
        return summaries;
    }

    public RatingSummary ratingSummary(int productId) {
        return ratingSummaries(List.of(productId)).getOrDefault(productId, new RatingSummary(0, 0));
    }

    /** Approved reviews for the home page (used once real reviews exist). */
    public List<Review> recentApproved(int limit) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS
                + "WHERE r.status = 'APPROVED' ORDER BY r.created_at DESC, r.review_id DESC LIMIT ?";
        return queryList(sql, List.of((Object) Math.max(1, limit)));
    }

    /** Admin list with an optional status filter. */
    public List<Review> findPage(Review.Status status, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " " + FROM_JOINS);
        if (status != null) {
            sql.append("WHERE r.status = ? ");
        }
        sql.append("ORDER BY r.created_at DESC, r.review_id DESC LIMIT ? OFFSET ?");
        List<Object> params = new ArrayList<>();
        if (status != null) {
            params.add(status.name());
        }
        params.add(Math.max(1, limit));
        params.add(Math.max(0, offset));
        return queryList(sql.toString(), params);
    }

    public int count(Review.Status status) {
        String sql = status == null
                ? "SELECT COUNT(*) FROM reviews"
                : "SELECT COUNT(*) FROM reviews WHERE status = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (status != null) {
                ps.setString(1, status.name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("counting reviews", e);
        }
        return 0;
    }

    public void updateStatus(int reviewId, Review.Status status) {
        String sql = "UPDATE reviews SET status = ? WHERE review_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, reviewId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("updating review status", e);
        }
    }

    public boolean delete(int reviewId) {
        String sql = "DELETE FROM reviews WHERE review_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, reviewId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("deleting review", e);
        }
    }

    private List<Review> queryList(String sql, List<Object> params) {
        List<Review> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("executing review query", e);
        }
        return list;
    }
}