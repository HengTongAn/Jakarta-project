package com.example.computer_store.filter;

import com.example.computer_store.config.AppContext;

import com.example.computer_store.model.User;
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
        User sessionUser = sessionUser(request);
        if (sessionUser != null) {
            request.setAttribute("mailCount", AppContext.get().mailService().countUnread(sessionUser.getUserId()));
        }
        chain.doFilter(request, response);
    }

    private User sessionUser(ServletRequest request) {
        HttpSession session = ((HttpServletRequest) request).getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }
}