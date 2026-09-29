package com.hengtongan.computerstore.web.controller.monitoring;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.infrastructure.monitoring.MetricsCollector;
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
