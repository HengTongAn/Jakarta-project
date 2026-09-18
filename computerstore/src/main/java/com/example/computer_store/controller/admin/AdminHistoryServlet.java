package com.example.computer_store.controller.admin;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.example.computer_store.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import com.example.computer_store.model.AuditAlert;
import com.example.computer_store.model.AuditLog;
import com.example.computer_store.service.AuditLogService;
import com.example.computer_store.util.AuditLogger;
import com.example.computer_store.util.Flash;

/**
 * Admin History page: a read-only window into the audit_logs table with a
 * global search box, mini statistics, an alerts screen for suspicious
 * patterns, and drill-through links into the admin management pages.
 */
@WebServlet("/admin/history")
public class AdminHistoryServlet extends BaseServlet {
    private static final int PAGE_SIZE = 20;
    private static final int EXPORT_LIMIT = 10_000;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String view = request.getParameter("view");

        if ("alerts".equals(view)) {
            List<AuditAlert> alerts = app().auditLogService().findAlerts();
            request.setAttribute("alerts", alerts);
            request.setAttribute("viewMode", "alerts");
            request.getRequestDispatcher("/WEB-INF/views/admin/history.jsp").forward(request, response);
            return;
        }

        boolean archive = "archive".equals(view);
        String keyword = trimToNull(request.getParameter("keyword"));
        int page = parsePage(request.getParameter("page"));
        int offset = (page - 1) * PAGE_SIZE;
        int total = archive
                ? app().auditLogService().countArchive(null, null, keyword, null, null)
                : app().auditLogService().count(null, null, keyword, null, null);
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        if (page > totalPages) {
            page = totalPages;
            offset = (page - 1) * PAGE_SIZE;
        }
        List<AuditLog> entries = archive
                ? app().auditLogService().searchArchive(null, null, keyword, null, null, offset, PAGE_SIZE)
                : app().auditLogService().search(null, null, keyword, null, null, offset, PAGE_SIZE);
        for (AuditLog entry : entries) {
            decorate(entry, request.getContextPath());
        }
        request.setAttribute("entries", entries);
        request.setAttribute("total", total);
        request.setAttribute("page", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("keyword", keyword);
        request.setAttribute("viewArchive", archive);
        request.setAttribute("viewMode", archive ? "archive" : "active");

        if (!archive) {
            AuditLogService service = app().auditLogService();
            request.setAttribute("statsToday", service.countToday());
            request.setAttribute("statsWeek", service.countSinceDays(7));
            request.setAttribute("statsFailed24h", service.failedLoginsSinceHours(24));
            request.setAttribute("statsFailed7d", service.failedLoginsSinceHours(24 * 7));
            request.setAttribute("statsByType", service.countByType());
            request.setAttribute("alertsCount", service.findAlerts().size());
        }
        request.getRequestDispatcher("/WEB-INF/views/admin/history.jsp").forward(request, response);
    }

    /**
     * Computes the presentation helpers for drill-through (#6) and the detail
     * inspector (#1): a human label for the target, an admin page link when the
     * resource maps to one, and the raw JSON view of the record.
     */
    private void decorate(AuditLog entry, String contextPath) {
        String kind = entry.getResourceType();
        String id = entry.getResourceId();
        String label;
        if (kind != null && kind.trim().isEmpty()) {
            kind = null;
        }
        if (kind != null) {
            label = kind.toLowerCase() + (id != null && !id.isEmpty() ? " #" + id : "");
        } else if (id != null && id.contains("#")) {
            int hash = id.indexOf('#');
            kind = id.substring(0, hash).trim();
            id = id.substring(hash + 1).trim();
            label = id.isEmpty() ? kind.toLowerCase() : kind.toLowerCase() + " #" + id;
        } else if (id != null && !id.isEmpty()) {
            label = id;
        } else {
            label = null;
        }
        entry.setTargetLabel(label);
        entry.setDrillUrl(drillUrl(contextPath, kind, id));
        entry.setRawJson(entry.toRawJson());
    }

    private String drillUrl(String contextPath, String kind, String id) {
        if (kind == null) {
            return null;
        }
        switch (kind.toUpperCase()) {
            case "USER":
                return id == null ? null : contextPath + "/admin/users?action=profile&id=" + id;
            case "PRODUCT":
                return id == null ? null : contextPath + "/admin/products?edit=" + id;
            case "ORDER":
                return id == null ? null : contextPath + "/admin/orders?id=" + id;
            case "MESSAGE":
                return id == null ? null : contextPath + "/admin/mail/view?id=" + id;
            case "CATEGORY":
                return contextPath + "/admin/categories";
            case "BRAND":
                return contextPath + "/admin/brands";
            case "INVENTORY":
                return contextPath + "/admin/inventory";
            case "AUDIT_LOGS":
            case "AUDIT":
                return contextPath + "/admin/history";
            default:
                return null;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String action = request.getParameter("action");
        if ("export".equals(action)) {
            exportCsv(request, response);
            return;
        }
        if ("archive".equals(action)) {
            archive(request, response);
            return;
        }
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown history action.");
    }

    private void exportCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LocalDate from = parseDate(request.getParameter("from"));
        LocalDate to = parseDate(request.getParameter("to"));
        if (from != null && to != null && from.isAfter(to)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid date range.");
            return;
        }
        boolean archive = "archive".equals(request.getParameter("view"));
        List<AuditLog> logs;
        if (archive) {
            logs = app().auditLogService().searchArchive(trimToNull(request.getParameter("type")),
                    trimToNull(request.getParameter("actor")), trimToNull(request.getParameter("keyword")),
                    from, to, 0, EXPORT_LIMIT);
        } else {
            logs = app().auditLogService().search(trimToNull(request.getParameter("type")),
                    trimToNull(request.getParameter("actor")), trimToNull(request.getParameter("keyword")),
                    from, to, 0, EXPORT_LIMIT);
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + (archive ? "audit-archive.csv" : "audit-history.csv") + "\"");
        response.getWriter().write("ID,Created at,Type,Action,Actor,Resource type,Resource ID,Details,IP address"
                + (archive ? ",Archived at" : "") + "\r\n");
        for (AuditLog log : logs) {
            response.getWriter().write(csv(log.getAuditId()) + "," + csv(log.getCreatedAt()) + ","
                    + csv(log.getActionType()) + "," + csv(log.getActionName()) + "," + csv(log.getActor()) + ","
                    + csv(log.getResourceType()) + "," + csv(log.getResourceId()) + "," + csv(log.getDetails()) + ","
                    + csv(log.getIpAddress()) + (archive ? "," + csv(log.getArchivedAt()) : "") + "\r\n");
        }
    }

    private void archive(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int days;
        try {
            days = Integer.parseInt(request.getParameter("retentionDays"));
        } catch (NumberFormatException e) {
            days = -1;
        }
        if (days != 365 && days != 730 && days != 2555) {
            Flash.error(request, "Invalid retention period.");
        } else {
            int archived = app().auditLogService().archiveBefore(LocalDate.now().minusDays(days));
            AuditLogger.logAdminAction("AUDIT_ARCHIVE", currentUsername(request), "audit_logs",
                    "Archived " + archived + " record(s) older than " + days + " days");
            Flash.success(request, archived + " audit record" + (archived == 1 ? " was" : "s were") + " archived.");
        }
        redirect(request, response, "/admin/history");
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        // Prevent spreadsheet formula injection when the CSV is opened.
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private int parsePage(String raw) {
        try {
            int p = Integer.parseInt(raw);
            return p < 1 ? 1 : p;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private LocalDate parseDate(String value) {
        String date = trimToNull(value);
        if (date == null) return null;
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
