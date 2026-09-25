package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.domain.entity.InventoryLog;
import com.hengtongan.computerstore.core.domain.entity.Order;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Both "/admin" and "/admin/" (trailing slash) must land here; the header
// link can emit either depending on how the URL was entered.
@WebServlet({"/admin", "/admin/"})
public class DashboardServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("1".equals(request.getParameter("json"))) {
            writeStatsJson(response);
            return;
        }
        request.setAttribute("stats", app().dashboardService().getAdminStats());
        request.getRequestDispatcher("/WEB-INF/views/admin/dashboard.jsp").forward(request, response);
    }

    /**
     * Live stats for {@code assets/js/dashboard-live.js}. Always recomputed
     * fresh (never the cached 5-minute snapshot) so an SSE-triggered refresh
     * shows exactly the data that just committed.
     */
    private void writeStatsJson(HttpServletResponse response) throws IOException {
        Map<String, Object> stats = app().dashboardService().getFreshAdminStats();
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        PrintWriter out = response.getWriter();
        out.write("{");
        out.write("\"counters\":{");
        out.write("\"totalProducts\":" + stats.get("totalProducts") + ",");
        out.write("\"totalCustomers\":" + stats.get("totalCustomers") + ",");
        out.write("\"totalOrders\":" + stats.get("totalOrders") + ",");
        out.write("\"totalRevenue\":" + toJsonNumber(stats.get("totalRevenue")) + ",");
        out.write("\"lowStockCount\":" + stats.get("lowStockCount") + ",");
        out.write("\"outOfStockCount\":" + stats.get("outOfStockCount") + ",");
        out.write("\"pendingOrders\":" + stats.get("pendingOrders") + ",");
        out.write("\"pendingReviews\":" + stats.get("pendingReviews"));
        out.write("},");

        out.write("\"recentOrders\":[");
        @SuppressWarnings("unchecked")
        List<Order> orders = (List<Order>) stats.get("recentOrders");
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                out.write(",");
            }
            Order o = orders.get(i);
            out.write("{");
            out.write("\"orderId\":" + o.getOrderId() + ",");
            out.write("\"customerName\":\"" + esc(o.getCustomerName()) + "\",");
            out.write("\"orderDate\":" + o.getOrderDate().getTime() + ",");
            out.write("\"totalAmount\":" + o.getTotalAmount().toPlainString() + ",");
            out.write("\"status\":\"" + o.getStatus().name() + "\"");
            out.write("}");
        }
        out.write("],");

        out.write("\"recentLogs\":[");
        @SuppressWarnings("unchecked")
        List<InventoryLog> logs = (List<InventoryLog>) stats.get("recentLogs");
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) {
                out.write(",");
            }
            InventoryLog log = logs.get(i);
            out.write("{");
            out.write("\"productName\":\"" + esc(log.getProductName()) + "\",");
            out.write("\"action\":\"" + esc(log.getAction()) + "\",");
            out.write("\"oldQuantity\":" + log.getOldQuantity() + ",");
            out.write("\"newQuantity\":" + log.getNewQuantity());
            out.write("}");
        }
        out.write("]");
        out.write("}");
    }

    private static String toJsonNumber(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd.toPlainString();
        }
        return String.valueOf(value);
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}