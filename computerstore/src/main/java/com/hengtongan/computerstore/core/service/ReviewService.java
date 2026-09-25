package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.infrastructure.cache.CacheManager;
import com.hengtongan.computerstore.core.repository.ProductRepository;
import com.hengtongan.computerstore.core.repository.ReviewRepository;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.domain.entity.RatingSummary;
import com.hengtongan.computerstore.core.domain.entity.Review;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import com.hengtongan.computerstore.infrastructure.realtime.EventHub;
import com.hengtongan.computerstore.util.web.ErrorHandler;
import com.hengtongan.computerstore.util.validation.ValidationUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Customer reviews: submission, moderation, and the aggregates shown on
 * product cards and the product page.
 *
 * <p>Submission validation lives in the static {@link #validate(int, String, String)}
 * so it can be tested hermetically without a database.</p>
 */
public class ReviewService {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MIN_TEXT_LENGTH = 10;
    public static final int MAX_TEXT_LENGTH = 4000;
    public static final int MAX_TITLE_LENGTH = 150;

    private final ReviewRepository reviewDAO = new ReviewRepository();
    private final ProductRepository productDAO = new ProductRepository();

    /**
     * Submits a review as {@code PENDING} (admins moderate what is published).
     * Marks it verified when the customer actually purchased the product, and
     * enforces one review per customer per product.
     */
    public Review submit(int userId, int productId, int rating, String title, String reviewText) {
        validate(rating, title, reviewText);

        Product product = productDAO.findById(productId);
        if (product == null || product.isDeleted()
                || product.getStatus() == Product.Status.DISCONTINUED) {
            throw new ValidationException("Product not found.");
        }
        if (reviewDAO.findByUserAndProduct(userId, productId) != null) {
            throw new ValidationException("You have already reviewed this product.");
        }

        Review review = new Review();
        review.setProductId(productId);
        review.setUserId(userId);
        review.setRating(rating);
        review.setTitle(title == null ? null : title.trim());
        review.setReviewText(reviewText.trim());
        review.setStatus(Review.Status.PENDING);

        try (Connection c = DBConnection.getConnection()) {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                review.setVerified(reviewDAO.hasPurchased(c, userId, productId));
                reviewDAO.create(c, review);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("submitting review", e);
        }

        // Pending count shown to admins changed.
        if (CacheManager.isCacheEnabled()) {
            CacheManager.invalidateAllDashboard();
        }
        publishPendingReviewCount();
        return review;
    }

    /** Pure validation rules for a review submission (no database access). */
    public static void validate(int rating, String title, String reviewText) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new ValidationException("Please choose a rating between 1 and 5 stars.");
        }
        if (ValidationUtil.isBlank(reviewText)) {
            throw new ValidationException("Please write a short review.");
        }
        String text = reviewText.trim();
        if (text.length() < MIN_TEXT_LENGTH) {
            throw new ValidationException("Your review must be at least " + MIN_TEXT_LENGTH
                    + " characters long (currently " + text.length() + ").");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ValidationException("Your review can be at most " + MAX_TEXT_LENGTH + " characters.");
        }
        if (!ValidationUtil.isBlank(title) && title.trim().length() > MAX_TITLE_LENGTH) {
            throw new ValidationException("The review title can be at most " + MAX_TITLE_LENGTH + " characters.");
        }
    }

    /** Approved reviews for a product, newest first. */
    public List<Review> findApprovedByProduct(int productId) {
        return reviewDAO.findApprovedByProduct(productId);
    }

    /** The current customer's review of a product (any status), or null. */
    public Review findByUserAndProduct(int userId, int productId) {
        return reviewDAO.findByUserAndProduct(userId, productId);
    }

    public RatingSummary getRatingSummary(int productId) {
        return reviewDAO.ratingSummary(productId);
    }

    /** Average + count for many products in one query (catalogue cards). */
    public Map<Integer, RatingSummary> getRatingSummaries(Collection<Integer> productIds) {
        return reviewDAO.ratingSummaries(productIds);
    }

    /** Latest approved reviews for the storefront home section. */
    public List<Review> recentApproved(int limit) {
        return reviewDAO.recentApproved(limit);
    }

    /** Admin list, optionally filtered by status. */
    public List<Review> getPage(Review.Status status, int offset, int limit) {
        return reviewDAO.findPage(status, offset, limit);
    }

    public int count(Review.Status status) {
        return reviewDAO.count(status);
    }

    public void approve(int reviewId) {
        changeStatus(reviewId, Review.Status.APPROVED);
    }

    public void reject(int reviewId) {
        changeStatus(reviewId, Review.Status.REJECTED);
    }

    private void changeStatus(int reviewId, Review.Status status) {
        if (reviewDAO.findById(reviewId) == null) {
            throw new NotFoundException("Review not found.");
        }
        reviewDAO.updateStatus(reviewId, status);
        if (CacheManager.isCacheEnabled()) {
            CacheManager.invalidateAllDashboard();
            CacheManager.invalidateAllCatalog(); // approved testimonials on the storefront home
        }
        publishPendingReviewCount();
    }

    public boolean delete(int reviewId) {
        boolean removed = reviewDAO.delete(reviewId);
        if (removed && CacheManager.isCacheEnabled()) {
            CacheManager.invalidateAllDashboard();
            CacheManager.invalidateAllCatalog(); // approved testimonials on the storefront home
        }
        if (removed) {
            publishPendingReviewCount();
        }
        return removed;
    }

    /**
     * Broadcasts the fresh pending-review count so every open admin UI (nav
     * badge, dashboard card) updates in realtime; the payload carries the
     * figure so clients do not need a follow-up request.
     */
    private void publishPendingReviewCount() {
        EventHub.publish("reviews", "{\"pending\":" + reviewDAO.count(Review.Status.PENDING) + "}");
    }
}