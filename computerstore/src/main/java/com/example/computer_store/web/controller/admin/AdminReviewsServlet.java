package com.example.computer_store.web.controller.admin;

import com.example.computer_store.web.controller.base.BaseServlet;
import com.example.computer_store.core.exception.NotFoundException;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.Review;
import com.example.computer_store.core.service.ReviewService;
import com.example.computer_store.util.web.AuditLogger;
import com.example.computer_store.util.web.Flash;
import com.example.computer_store.util.validation.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Admin review moderation:
 *   GET  /admin/reviews                -> review list (filter by status)
 *   POST /admin/reviews                -> approve / reject / delete a review
 *
 * <p>Protected by {@code AuthenticationFilter} + {@code AdminAuthorizationFilter}
 * (all /admin/* paths). Every decision is written to the audit log.</p>
 */
@WebServlet("/admin/reviews")
public class AdminReviewsServlet extends BaseServlet {

    private static final int PAGE_SIZE = 25;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ReviewService reviewService = app().reviewService();

        Review.Status status = parseStatus(request.getParameter("status"));
        int page = parsePage(request.getParameter("page"));
        int total = reviewService.count(status);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        if (page > totalPages) {
            page = totalPages;
        }
        int offset = (page - 1) * PAGE_SIZE;

        request.setAttribute("reviews", reviewService.getPage(status, offset, PAGE_SIZE));
        request.setAttribute("selectedStatus", status);
        request.setAttribute("page", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalReviews", total);
        request.setAttribute("pendingCount", reviewService.count(Review.Status.PENDING));
        request.setAttribute("approvedCount", reviewService.count(Review.Status.APPROVED));
        request.setAttribute("rejectedCount", reviewService.count(Review.Status.REJECTED));

        forward(request, response, "admin/reviews.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        Integer reviewId = ValidationUtil.parseInt(request.getParameter("reviewId"));
        if (reviewId == null) {
            Flash.error(request, "Invalid request.");
            response.sendRedirect(request.getContextPath() + "/admin/reviews");
            return;
        }

        String actor = currentUsername(request);
        try {
            switch (action == null ? "" : action) {
                case "approve" -> {
                    app().reviewService().approve(reviewId);
                    AuditLogger.logAdminAction("REVIEW_APPROVE", actor,
                            "review #" + reviewId, "Review approved");
                    Flash.success(request, "Review #" + reviewId + " approved and published.");
                }
                case "reject" -> {
                    app().reviewService().reject(reviewId);
                    AuditLogger.logAdminAction("REVIEW_REJECT", actor,
                            "review #" + reviewId, "Review rejected");
                    Flash.success(request, "Review #" + reviewId + " rejected.");
                }
                case "delete" -> {
                    if (!app().reviewService().delete(reviewId)) {
                        throw new NotFoundException("Review not found.");
                    }
                    AuditLogger.logAdminAction("REVIEW_DELETE", actor,
                            "review #" + reviewId, "Review removed");
                    Flash.success(request, "Review #" + reviewId + " deleted.");
                }
                default -> Flash.error(request, "Unknown action.");
            }
        } catch (NotFoundException | ValidationException e) {
            Flash.error(request, e.getMessage());
        }

        String status = request.getParameter("status");
        String target = "/admin/reviews";
        if (status != null && !status.isEmpty()) {
            target += "?status=" + status;
        }
        response.sendRedirect(request.getContextPath() + target);
    }

    private static Review.Status parseStatus(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Review.Status.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parsePage(String raw) {
        Integer page = ValidationUtil.parseInt(raw);
        return page == null || page < 1 ? 1 : page;
    }
}