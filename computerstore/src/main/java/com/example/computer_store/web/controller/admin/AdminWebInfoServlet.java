package com.example.computer_store.web.controller.admin;

import com.example.computer_store.web.controller.base.BaseServlet;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.service.WebProductInfoService;
import com.example.computer_store.core.service.WebProductInfoService.ExtractResult;
import com.example.computer_store.core.service.WebProductInfoService.SearchResult;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Admin-only web lookup powering the product form's "auto-fill from the web"
 * panel.
 * <pre>
 *   GET /admin/products/webinfo?action=search&amp;q=...   -> {"results":[{title,url,snippet},...]}
 *   GET /admin/products/webinfo?action=fetch&amp;url=...   -> {pageTitle,description,sourceUrl,manufacturerPart,specs,highlights,boxContents,warrantyInfo}
 * </pre>
 *
 * <p>Both endpoints are read-only GETs (no side effects) which keeps them safe
 * under the CSRF filter. The path is under {@code /admin/*} so the admin
 * authorization filter protects them, and {@link WebProductInfoService} applies
 * its own SSRF guards (http/https only, no local/private addresses) before any
 * outbound request. Extracted values are suggestions the admin reviews in the
 * form before saving.
 */
@WebServlet("/admin/products/webinfo")
public class AdminWebInfoServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try {
            String action = request.getParameter("action");
            if ("search".equals(action)) {
                writeSearch(request, response);
                return;
            }
            if ("fetch".equals(action)) {
                writeFetch(request, response);
                return;
            }
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing or unknown action (expected \"search\" or \"fetch\").");
        } catch (ValidationException e) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "Could not reach the web service: " + e.getMessage());
        }
    }

    private void writeSearch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<SearchResult> results = new WebProductInfoService().search(request.getParameter("q"));
        StringBuilder sb = new StringBuilder("{\"results\":[");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"title\":\"").append(esc(r.title())).append("\",")
              .append("\"url\":\"").append(esc(r.url())).append("\",")
              .append("\"snippet\":\"").append(esc(r.snippet())).append("\"}");
        }
        sb.append("]}");
        response.getWriter().write(sb.toString());
    }

    private void writeFetch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String url = request.getParameter("url");
        ExtractResult result = new WebProductInfoService().fetchAndExtract(url);

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"pageTitle\":\"").append(esc(result.pageTitle())).append("\",");
        sb.append("\"description\":").append(jsonNullable(result.description())).append(",");
        sb.append("\"sourceUrl\":\"").append(esc(url)).append("\",");
        sb.append("\"manufacturerPart\":").append(jsonNullable(result.manufacturerPart())).append(",");
        sb.append("\"boxContents\":").append(jsonNullable(result.boxContents())).append(",");
        sb.append("\"warrantyInfo\":").append(jsonNullable(result.warrantyInfo())).append(",");
        sb.append("\"specs\":[");
        List<WebProductInfoService.SpecSuggestion> specs = result.specs();
        for (int i = 0; i < specs.size(); i++) {
            WebProductInfoService.SpecSuggestion s = specs.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"key\":\"").append(esc(s.key())).append("\",")
              .append("\"value\":\"").append(esc(s.value())).append("\"}");
        }
        sb.append("],\"highlights\":[");
        List<String> highlights = result.highlights();
        for (int i = 0; i < highlights.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(esc(highlights.get(i))).append("\"");
        }
        sb.append("]}");
        response.getWriter().write(sb.toString());
    }

    private static String jsonNullable(String s) {
        return s == null ? "null" : "\"" + esc(s) + "\"";
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().write("{\"error\":\"" + esc(message) + "\"}");
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