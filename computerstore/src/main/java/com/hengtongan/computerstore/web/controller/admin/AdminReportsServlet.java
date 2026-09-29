package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.service.ReportService;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@WebServlet("/admin/reports")
public class AdminReportsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Period selector: ?range=today|7d|30d|all (defaults to last 7 days).
        String range = normalizeRange(request.getParameter("range"));
        int days = daysFor(range);
        if ("1".equals(request.getParameter("json"))) {
            writeReportJson(response, days);
            return;
        }
        ReportService reportService = app().reportService();
        request.setAttribute("activeRange", range);
        request.setAttribute("periodLabel", labelFor(range));
        request.setAttribute("summary", reportService.getSalesSummary(days));
        request.setAttribute("series", reportService.getRevenueSeries(days));
        request.setAttribute("topSellers", reportService.getTopSellers(days));
        request.setAttribute("stockValues", reportService.getLowStockValues());
        request.getRequestDispatcher("/WEB-INF/views/admin/reports.jsp").forward(request, response);
    }

    /**
     * Live report data for {@code assets/js/reports-live.js}. Always computed
     * fresh (this page is never cached) so an SSE-triggered refresh shows
     * exactly the data that just committed.
     */
    private void writeReportJson(HttpServletResponse response, int days) throws IOException {
        ReportService reportService = app().reportService();
        Map<String, Object> summary = reportService.getSalesSummary(days);
        List<Map<String, Object>> series = reportService.getRevenueSeries(days);
        List<Map<String, Object>> topSellers = reportService.getTopSellers(days);
        Map<String, BigDecimal> stockValues = reportService.getLowStockValues();

        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        PrintWriter out = response.getWriter();
        out.write("{");
        out.write("\"summary\":{");
        out.write("\"totalOrders\":" + summary.get("totalOrders") + ",");
        out.write("\"totalRevenue\":" + toJsonNumber(summary.get("totalRevenue")) + ",");
        out.write("\"itemsSold\":" + summary.get("itemsSold") + ",");
        out.write("\"avgOrderValue\":" + toJsonNumber(summary.get("avgOrderValue")) + ",");
        out.write("\"pending\":" + summary.get("pending") + ",");
        out.write("\"processing\":" + summary.get("processing") + ",");
        out.write("\"shipped\":" + summary.get("shipped") + ",");
        out.write("\"completed\":" + summary.get("completed") + ",");
        out.write("\"cancelled\":" + summary.get("cancelled") + ",");
        out.write("\"refunded\":" + summary.get("refunded"));
        out.write("},");

        out.write("\"series\":[");
        for (int i = 0; i < series.size(); i++) {
            if (i > 0) {
                out.write(",");
            }
            Map<String, Object> point = series.get(i);
            out.write("{\"label\":\"" + esc(String.valueOf(point.get("label"))) + "\",");
            out.write("\"value\":" + toJsonNumber(point.get("value")) + "}");
        }
        out.write("],");

        out.write("\"topSellers\":[");
        for (int i = 0; i < topSellers.size(); i++) {
            if (i > 0) {
                out.write(",");
            }
            Map<String, Object> seller = topSellers.get(i);
            out.write("{\"name\":\"" + esc(String.valueOf(seller.get("name"))) + "\",");
            out.write("\"quantity\":" + seller.get("quantity") + ",");
            out.write("\"revenue\":" + toJsonNumber(seller.get("revenue")) + "}");
        }
        out.write("],");

        out.write("\"stockValues\":{");
        out.write("\"totalStockValue\":" + toJsonNumber(stockValues.get("totalStockValue")) + ",");
        out.write("\"lowStockValue\":" + toJsonNumber(stockValues.get("lowStockValue")));
        out.write("}");
        out.write("}");
    }

    private static String normalizeRange(String range) {
        if (range == null) {
            return "7d";
        }
        return switch (range) {
            case "today", "7d", "30d", "all" -> range;
            default -> "7d";
        };
    }

    private static int daysFor(String range) {
        return switch (range) {
            case "today" -> 1;
            case "30d" -> 30;
            case "all" -> 0;
            default -> 7;
        };
    }

    private static String labelFor(String range) {
        return switch (range) {
            case "today" -> "Today";
            case "30d" -> "Last 30 days";
            case "all" -> "All time";
            default -> "Last 7 days";
        };
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