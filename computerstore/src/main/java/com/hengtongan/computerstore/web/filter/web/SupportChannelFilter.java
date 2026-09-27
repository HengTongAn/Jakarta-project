package com.hengtongan.computerstore.web.filter.web;

import com.hengtongan.computerstore.core.config.AppContext;
import com.hengtongan.computerstore.util.web.RequestUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * Puts the contact-support channels on every renderable request as the
 * {@code supportChannels} attribute, which the site footer iterates.
 * <p>
 * A filter rather than a scriptlet in {@code footer.jspf}: the JSP is a static
 * include with no access to {@link AppContext}, and the channels have to be
 * resolved once per request rather than per footer. The service caches the
 * underlying rows, so this is a map lookup on the hot path.
 */
public class SupportChannelFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!RequestUtil.isStaticOrStream(httpRequest)) {
            request.setAttribute("supportChannels",
                    AppContext.get().supportChannelService().channels());
        }
        chain.doFilter(request, response);
    }
}
