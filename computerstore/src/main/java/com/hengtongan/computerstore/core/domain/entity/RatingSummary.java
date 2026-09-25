package com.hengtongan.computerstore.core.domain.entity;

/**
 * Aggregated rating for a product: the average of approved reviews and how
 * many there are. Used on catalogue cards and the product detail page.
 */
public record RatingSummary(double average, long count) {

    public boolean hasReviews() {
        return count > 0;
    }

    /** Number of whole stars to render (average rounded to the nearest star). */
    public int displayStars() {
        return (int) Math.round(average);
    }
}