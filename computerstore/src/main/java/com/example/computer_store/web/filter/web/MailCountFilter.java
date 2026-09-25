package com.example.computer_store.web.filter.web;

import com.example.computer_store.core.config.AppContext;

import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.util.cache.CountCache;
import com.example.computer_store.util.web.RequestUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

public class MailCountFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!RequestUtil.isStaticOrStream(httpRequest)) {
            User sessionUser = sessionUser(httpRequest);
            if (sessionUser != null) {
                HttpSession session = httpRequest.getSession();
                request.setAttribute("mailCount", CountCache.get(
                        session, "mail",
                        () -> AppContext.get().mailService().countUnread(sessionUser.getUserId())));
            }
        }
        chain.doFilter(request, response);
    }

    private User sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }
}
