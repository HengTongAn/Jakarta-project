package com.hengtongan.computerstore.web.filter.web;

import com.hengtongan.computerstore.core.config.AppContext;

import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.util.cache.CountCache;
import com.hengtongan.computerstore.util.web.RequestUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

public class CartCountFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!RequestUtil.isStaticOrStream(httpRequest)) {
            User sessionUser = sessionUser(httpRequest);
            if (sessionUser != null) {
                HttpSession session = httpRequest.getSession();
                request.setAttribute("cartCount", CountCache.get(
                        session, "cart",
                        () -> AppContext.get().cartService().countItems(sessionUser.getUserId())));
            }
        }
        chain.doFilter(request, response);
    }

    private User sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }
}
