package com.example.computer_store.web.filter.web;

import com.example.computer_store.core.config.AppContext;
import com.example.computer_store.core.domain.entity.Review;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.util.web.RequestUtil;
import com.example.computer_store.util.cache.CountCache;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Puts the number of pending (unmoderated) reviews in request scope for admin
 * pages so the admin nav can surface the moderation queue. Follows the
 * MailCountFilter pattern but only fires for admins and only on /admin routes.
 */
public class ReviewCountFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!RequestUtil.isStaticOrStream(httpRequest)) {
            HttpSession session = httpRequest.getSession(false);
            User user = session == null ? null : (User) session.getAttribute("user");
            if (user != null && user.isAdmin()) {
                request.setAttribute("pendingReviewCount", CountCache.get(session, "pendingReviews",
                        () -> AppContext.get().reviewService().count(Review.Status.PENDING)));
            }
        }
        chain.doFilter(request, response);
    }
}
