package com.example.computer_store.infrastructure.monitoring;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.example.computer_store.core.domain.entity.User;
import java.io.IOException;

/**
 * Servlet for exporting metrics in Prometheus format.
 * Accessible at /metrics endpoint for monitoring systems.
 */
@WebServlet("/metrics")
public class MetricsServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null || !user.isAdmin()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        response.setContentType("text/plain; version=0.0.4; charset=utf-8");
        response.getWriter().write(MetricsCollector.exportPrometheusFormat());
    }
}
