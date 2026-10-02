package com.hengtongan.computerstore.web.filter.security;

import com.hengtongan.computerstore.core.domain.entity.User;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Keeps the shopping side of the app closed to administrators.
 * <p>
 * The store sells to customers, and staff run the store through {@code /admin}.
 * An admin session therefore gets a 403 on the customer storefront instead of a
 * cart: browsing the catalogue, adding to cart, checking out, and paying are all
 * refused, so an admin cannot buy stock from their own shop and end up editing
 * or refunding their own order a few minutes later.
 *
 * <h2>What is and is not refused</h2>
 * <p>
 * Refused: the catalogue ({@code /products}, including review submission), the
 * cart, checkout, both payment paths, and the three account pages that exist to
 * show purchases — the account dashboard, order history, and the transaction
 * ledger.
 * <p>
 * <b>Still allowed for an admin: {@code /account/profile},
 * {@code /account/settings}, {@code /account/avatar} and {@code /account/2fa}.</b>
 * Those are identity, not commerce — name, email, avatar, password, and TOTP
 * enrolment. Blocking them would leave a signed-in admin unable to change the
 * password on the very account that is signing in, and would take 2FA
 * enrolment away from the two roles that can actually administer the store. They
 * sit under the same {@code /account} prefix as the shopping pages, which is why
 * {@link #isShoppingPath} has to make this decision by exact path rather than by
 * trusting a {@code /account/*} url-pattern.
 *
 * <h2>Why a filter and not a hidden nav</h2>
 * <p>
 * Hiding the Shop and Cart buttons is a courtesy, not a control: the URLs are
 * public and an admin reaches them by typing one, or by opening a link that was
 * already on screen. Gating at the filter makes the rule one source of truth, so
 * the UI can be hidden and the request still refused. {@code errors/403.jsp} is
 * the same view {@link AdminAuthorizationFilter} uses, which is deliberate: both
 * filters answer the same way, and the "Back to store" button on that view is
 * role-aware so an admin is never handed a link to a page that will refuse them.
 *
 * <h2>Ordering</h2>
 * <p>
 * The mapping sits <em>after</em> {@code AuthenticationFilter} in
 * {@code web.xml}. Order is mapping order, and the cart, checkout, payment and
 * account paths are all already behind {@code AuthenticationFilter}, so placing
 * this one first would answer an anonymous visitor with a 403 about being an
 * administrator instead of redirecting them to login. With this order an
 * anonymous request is redirected to login by {@code AuthenticationFilter} and
 * only a session that actually holds an admin role reaches the check below.
 *
 * <h2>Role freshness</h2>
 * <p>
 * The role is read from the session, which {@code SessionUserRefreshFilter}
 * re-reads from the database every ~15s, so demoting an admin to a customer
 * restores shopping access within one refresh interval without a logout. An
 * anonymous request is passed through untouched rather than refused: the
 * catalogue is public, and {@link #isShoppingPath} only answers a question about
 * the page, not about who is allowed to see it.
 */
public final class StorefrontAccessFilter implements Filter {

    /** The view every refused request is forwarded to, same as the admin filter's. */
    private static final String VIEW = "/WEB-INF/views/errors/403.jsp";

    /** Storefront paths that need no suffix: the catalogue, the cart, checkout, the account dashboard. */
    private static final Set<String> EXACT_PATHS = Set.of(
            "/products", "/cart", "/checkout", "/account");

    /**
     * Storefront prefixes whose every sub-path is shopping. {@code /products/}
     * covers review submission, {@code /payment/} covers both gateways, and
     * {@code /cart/} covers the JSON badge count — a route that is not a page but
     * still moves cart state, so it is refused with the rest.
     */
    private static final List<String> SHOPPING_PREFIXES = List.of(
            "/products/", "/cart/", "/payment/");

    /**
     * The {@code /account} sub-paths an admin keeps.
     * <p>
     * An allowlist rather than a denylist of the shopping pages: a new account
     * page added later is refused until someone decides it is an identity page,
     * instead of becoming reachable by admins by default.
     */
    private static final Set<String> ACCOUNT_ALLOWED_FOR_ADMIN = Set.of(
            "/account/profile", "/account/settings", "/account/avatar", "/account/2fa");

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // The page alone does not mean this is a refusal: the catalogue is public.
        if (!isShoppingPath(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null || !user.isAdmin()) {
            chain.doFilter(request, response);
            return;
        }

        // An admin that reached a storefront URL some other way. Refuse the page,
        // but keep the way back into the panel one click away, and point at the
        // panel rather than at the store in the refusal page's own copy.
        request.setAttribute("deniedFromAdminPanel", true);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.getRequestDispatcher(VIEW).forward(request, response);
    }

    /**
     * Whether a path belongs to the shopping side of the app.
     * <p>
     * Package-visible and static so it can be tested directly: it is the whole
     * decision, and the parts of {@link #ACCOUNT_ALLOWED_FOR_ADMIN} that carve
     * exceptions out of a prefix are exactly the kind of rule that should be
     * pinned by tests rather than by clicking through the site.
     *
     * @param servletPath the path within the context, e.g. {@code /account/profile}
     * @return true when an admin must be refused this path
     */
    static boolean isShoppingPath(String servletPath) {
        if (servletPath == null) {
            return false;
        }
        if (EXACT_PATHS.contains(servletPath)) {
            return true;
        }
        for (String prefix : SHOPPING_PREFIXES) {
            if (servletPath.startsWith(prefix)) {
                return true;
            }
        }
        if (!servletPath.startsWith("/account/")) {
            return false;
        }
        // Everything else under /account is refused: the order history and the
        // transaction ledger are purchase history, and anything added later is
        // refused until it is named above as an identity page.
        return !ACCOUNT_ALLOWED_FOR_ADMIN.contains(servletPath);
    }
}
