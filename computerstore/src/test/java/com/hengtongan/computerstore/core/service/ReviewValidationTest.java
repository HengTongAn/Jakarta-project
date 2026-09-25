package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.RatingSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic tests for the review submission rules (no database involved —
 * the pure validation lives in {@link ReviewService#validate(int, String, String)}).
 */
class ReviewValidationTest {

    @Test
    void acceptsAValidReview() {
        ReviewService.validate(5, "Great keyboard", "Solid build quality and fast switches.");
    }

    @Test
    void acceptsOptionalBlankTitle() {
        ReviewService.validate(4, "   ", "Solid build quality and fast switches.");
        ReviewService.validate(4, null, "Solid build quality and fast switches.");
    }

    @Test
    void rejectsRatingBelowOne() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> ReviewService.validate(0, null, "Solid build quality and fast switches."));
        assertTrue(e.getMessage().toLowerCase().contains("rating"));
    }

    @Test
    void rejectsRatingAboveFive() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> ReviewService.validate(6, null, "Solid build quality and fast switches."));
        assertTrue(e.getMessage().toLowerCase().contains("rating"));
    }

    @Test
    void rejectsBlankComment() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> ReviewService.validate(5, null, "   "));
        assertTrue(e.getMessage().toLowerCase().contains("review"));
    }

    @Test
    void rejectsCommentShorterThanTenCharacters() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> ReviewService.validate(5, null, "Nice!"));
        assertTrue(e.getMessage().contains("10"));
    }

    @Test
    void rejectsCommentLongerThanFourThousandCharacters() {
        String tooLong = "a".repeat(4001);
        ValidationException e = assertThrows(ValidationException.class,
                () -> ReviewService.validate(5, null, tooLong));
        assertTrue(e.getMessage().contains("4000"));
    }

    @Test
    void rejectsTitleLongerThanOneHundredFiftyCharacters() {
        String tooLongTitle = "t".repeat(151);
        ValidationException e = assertThrows(ValidationException.class,
                () -> ReviewService.validate(5, tooLongTitle, "Solid build quality and fast switches."));
        assertTrue(e.getMessage().contains("150"));
    }

    @Test
    void acceptsCommentExactlyAtTheMinimumLength() {
        ReviewService.validate(3, null, "0123456789");
    }

    @Test
    void ratingSummaryHasReviewsOnlyWhenCountIsPositive() {
        assertFalse(new RatingSummary(4.5, 0).hasReviews());
        assertTrue(new RatingSummary(4.5, 3).hasReviews());
        assertEquals(5, new RatingSummary(4.5, 3).displayStars());
        assertEquals(4, new RatingSummary(4.2, 3).displayStars());
        assertEquals(4.5, new RatingSummary(4.5, 3).average());
        assertEquals(3, new RatingSummary(4.5, 3).count());
    }
}