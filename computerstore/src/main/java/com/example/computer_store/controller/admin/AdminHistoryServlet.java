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
import com.example.computer_store.model.AuditLog;
import com.example.computer_store.util.AuditLogger;
import com.example.computer_store.util.Flash;

/**
 * Admin History page: a read-only window into the audit_logs table.
 * Supports filtering by type / actor / keyword and simple pagination.
 */
@WebServlet("/admin/history")
public class AdminHistoryServlet extends BaseServlet {
    private static final int PAGE_SIZE = 20;
    private static final int EXPORT_LIMIT = 10_000;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String type = trimToNull(request.getParameter("type"));
        String actor = trimToNull(request.getParameter("actor"));
        String keyword = trimToNull(request.getParameter("keyword"));
        LocalDate from = parseDate(request.getParameter("from"));
        LocalDate to = parseDate(request.getParameter("to"));
        int page = parsePage(request.getParameter("page"));
        if (from != null && to != null && from.isAfter(to)) {
            Flash.error(request, "The start date must not be after the end date.");
            from = null;
            to = null;
        }
        int offset = (page - 1) * PAGE_SIZE;
        int total = app().auditLogService().count(type, actor, keyword, from, to);
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        if (page > totalPages) {
            page = totalPages;
            offset = (page - 1) * PAGE_SIZE;
        }
        request.setAttribute("entries", app().auditLogService().search(type, actor, keyword, from, to, offset, PAGE_SIZE));
        request.setAttribute("actors", app().auditLogService().distinctActors());
        request.setAttribute("total", total);
        request.setAttribute("page", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("filterType", type);
        request.setAttribute("filterActor", actor);
        request.setAttribute("filterKeyword", keyword);
        request.setAttribute("filterFrom", from == null ? null : from.toString());
        request.setAttribute("filterTo", to == null ? null : to.toString());
        request.setAttribute("exportLimit", EXPORT_LIMIT);
        request.getRequestDispatcher("/WEB-INF/views/admin/history.jsp").forward(request, response);
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
        if ("delete".equals(action)) {
            delete(request, response);
            return;
        }
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown history action.");
    }

    private void delete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id;
        try { id = Integer.parseInt(request.getParameter("auditId")); }
        catch (NumberFormatException e) { id = -1; }
        if (!"DELETE".equals(request.getParameter("confirmDelete"))) {
            Flash.error(request, "Deletion was not confirmed.");
        } else if (app().auditLogService().deleteById(id)) {
            AuditLogger.logAdminAction("AUDIT_DELETE", currentUsername(request), "audit_logs", "Deleted record #" + id);
            Flash.success(request, "History record deleted.");
        } else { Flash.error(request, "History record was not found."); }
        redirect(request, response, "/admin/history");
    }

    private void exportCsv(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LocalDate from = parseDate(request.getParameter("from"));
        LocalDate to = parseDate(request.getParameter("to"));
        if (from != null && to != null && from.isAfter(to)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid date range.");
            return;
        }
        List<AuditLog> logs = app().auditLogService().search(trimToNull(request.getParameter("type")),
                trimToNull(request.getParameter("actor")), trimToNull(request.getParameter("keyword")),
                from, to, 0, EXPORT_LIMIT);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"audit-history.csv\"");
        response.getWriter().write("ID,Created at,Type,Action,Actor,Resource type,Resource ID,Details,IP address\r\n");
        for (AuditLog log : logs) {
            response.getWriter().write(csv(log.getAuditId()) + "," + csv(log.getCreatedAt()) + ","
                    + csv(log.getActionType()) + "," + csv(log.getActionName()) + "," + csv(log.getActor()) + ","
                    + csv(log.getResourceType()) + "," + csv(log.getResourceId()) + "," + csv(log.getDetails()) + ","
                    + csv(log.getIpAddress()) + "\r\n");
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
