package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.service.SupportChannelService;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.web.Flash;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contact-support channel settings:
 *   GET  /admin/support -> configuration form
 *   POST /admin/support -> save all four destinations
 * <p>
 * Writes go to {@code app_settings} and take effect on the next page load, with
 * no rebuild and no Tomcat restart. The set is validated as a whole and nothing
 * is written unless every field is acceptable, so a typo in one field cannot
 * leave the other three half-applied.
 */
@WebServlet("/admin/support")
public class AdminSupportServlet extends BaseServlet {

    private static final String VIEW = "admin/support.jsp";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        render(request, response, app().supportChannelService().currentUrls(), Map.of());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        SupportChannelService service = app().supportChannelService();
        List<ChannelForm> submitted = readForm(request);

        Map<String, String> errors = new LinkedHashMap<>();
        for (ChannelForm field : submitted) {
            String problem = service.validate(field.value());
            if (problem != null) {
                errors.put(field.key(), problem);
            }
        }

        Map<String, String> urls = new LinkedHashMap<>();
        for (ChannelForm field : submitted) {
            urls.put(field.key(), field.value());
        }

        if (!errors.isEmpty()) {
            render(request, response, urls, errors);
            return;
        }

        service.save(urls);
        AuditLogger.logAdminAction("SUPPORT_CHANNELS_UPDATE", sessionUsername(request),
                "contact support", describe(urls));
        Flash.success(request, "Contact support channels updated.");
        redirect(request, response, "/admin/support");
    }

    private void render(HttpServletRequest request, HttpServletResponse response,
                        Map<String, String> urls, Map<String, String> errors)
            throws ServletException, IOException {
        request.setAttribute("channels", app().supportChannelService().channels());
        request.setAttribute("urls", urls);
        request.setAttribute("errors", errors);
        forward(request, response, VIEW);
    }

    /** One submitted text field, paired with the channel it belongs to. */
    private record ChannelForm(String key, String value) {
    }

    private List<ChannelForm> readForm(HttpServletRequest request) {
        List<ChannelForm> fields = new ArrayList<>();
        for (SupportChannelService.Channel channel : app().supportChannelService().channels()) {
            fields.add(new ChannelForm(channel.key(), request.getParameter(channel.key())));
        }
        return fields;
    }

    /** Audit-trail summary: names the channels that are live, not their URLs. */
    private String describe(Map<String, String> urls) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : urls.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey());
        }
        return sb.length() == 0 ? "Cleared every channel" : "Set: " + sb;
    }

    private String sessionUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute("user");
        if (user instanceof User) {
            return ((User) user).getUsername();
        }
        return "UNKNOWN";
    }
}
