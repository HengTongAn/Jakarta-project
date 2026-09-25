package com.example.computer_store.web.controller.customer;

import com.example.computer_store.web.controller.admin.AdminReviewsServlet;
import com.example.computer_store.web.controller.customer.ReviewServlet;
import jakarta.servlet.annotation.WebServlet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Hermetic smoke tests for the review endpoints: verify the servlets are
 * registered on the expected URL patterns and expose the expected handlers.
 */
class ReviewServletsTest {

    @Test
    void customerServletRegisteredOnProductsReview() {
        WebServlet annotation = ReviewServlet.class.getAnnotation(WebServlet.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{"/products/review"}, annotation.value());
    }

    @Test
    void customerServletHasDoPostHandler() {
        assertDoesNotThrow(() -> {
            var method = ReviewServlet.class.getDeclaredMethod("doPost",
                    jakarta.servlet.http.HttpServletRequest.class,
                    jakarta.servlet.http.HttpServletResponse.class);
            assertNotNull(method);
        });
    }

    @Test
    void adminServletRegisteredOnAdminReviews() {
        WebServlet annotation = AdminReviewsServlet.class.getAnnotation(WebServlet.class);
        assertNotNull(annotation);
        assertArrayEquals(new String[]{"/admin/reviews"}, annotation.value());
    }

    @Test
    void adminServletHasGetAndPostHandlers() {
        assertDoesNotThrow(() -> {
            var get = AdminReviewsServlet.class.getDeclaredMethod("doGet",
                    jakarta.servlet.http.HttpServletRequest.class,
                    jakarta.servlet.http.HttpServletResponse.class);
            var post = AdminReviewsServlet.class.getDeclaredMethod("doPost",
                    jakarta.servlet.http.HttpServletRequest.class,
                    jakarta.servlet.http.HttpServletResponse.class);
            assertNotNull(get);
            assertNotNull(post);
        });
    }

    @Test
    void pageSizeIsTwentyFive() throws Exception {
        // Reflectively read the constant to keep the admin pagination honest.
        assertEquals(25, adminPageSize());
    }

    private static int adminPageSize() throws Exception {
        var field = AdminReviewsServlet.class.getDeclaredField("PAGE_SIZE");
        field.setAccessible(true);
        return field.getInt(null);
    }
}