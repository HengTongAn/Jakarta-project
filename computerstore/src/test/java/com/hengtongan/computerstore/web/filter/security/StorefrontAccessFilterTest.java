package com.hengtongan.computerstore.web.filter.security;

import com.hengtongan.computerstore.core.domain.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hengtongan.computerstore.web.filter.security.StorefrontAccessFilter.isShoppingPath;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link StorefrontAccessFilter}: the rule that keeps administrators out of the customer
 * storefront, and the wiring that decides whether the rule runs at all.
 *
 * <h2>What is being protected</h2>
 *
 * The store sells to customers; staff run it from {@code /admin}. An admin who can add to a cart
 * and check out can buy stock from their own shop and then be the person who edits, ships or
 * refunds that order. So the catalogue, cart, checkout, both payment paths and the purchase-history
 * account pages answer 403 for an admin session.
 *
 * <p>The account carve-out is the subtle half and gets the most cases below. {@code /account} is one
 * prefix holding both commerce pages ({@code /account/orders},
 * {@code /account/transactions}) and identity pages ({@code /account/profile},
 * {@code /account/settings}, {@code /account/avatar}, {@code /account/2fa}). Getting it wrong in
 * either direction is bad, and in opposite directions: refusing everything under {@code /account}
 * locks a signed-in admin out of changing the password on the account they are signing in with and
 * takes TOTP enrolment away from the two roles that can actually administer the store, while
 * refusing nothing lets the admin read their own order history.
 */
class StorefrontAccessFilterTest {

    private StorefrontAccessFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        filter = new StorefrontAccessFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.getRequestDispatcher(anyString())).thenReturn(mock(jakarta.servlet.RequestDispatcher.class));
    }

    private void atPath(String servletPath) {
        when(request.getServletPath()).thenReturn(servletPath);
    }

    private void asRole(User.Role role) {
        User user = new User();
        user.setRole(role);
        when(session.getAttribute("user")).thenReturn(user);
    }

    // ------------------------------------------------------------------
    // The rule: which paths are shopping.
    // ------------------------------------------------------------------

    @Test
    void theCatalogueCartAndCheckoutAreShoppingPaths() {
        for (String path : List.of("/products", "/cart", "/checkout", "/account")) {
            assertTrue(isShoppingPath(path), path + " is a storefront page and must be closed to admins");
        }
    }

    @Test
    void subPathsOfTheShoppingPrefixesAreAlsoShopping() {
        // /products/review posts a review and /cart/count mutates nothing but is a cart route;
        // both live under a prefix rather than being named individually.
        for (String path : List.of("/products/review",
                "/cart/count", "/cart/add", "/cart/update", "/cart/remove",
                "/payment/aba", "/payment/aba/return", "/payment/card", "/payment/card/retry")) {
            assertTrue(isShoppingPath(path), path + " is a storefront route and must be closed to admins");
        }
    }

    @Test
    void purchaseHistoryUnderAccountIsShopping() {
        // The reason the account prefix needs a carve-out at all: these two live under /account
        // but exist to show an admin what they bought, which is exactly what should be closed.
        assertTrue(isShoppingPath("/account/orders"), "order history is purchase history");
        assertTrue(isShoppingPath("/account/transactions"), "the transaction ledger is purchase history");
    }

    @Test
    void identityPagesUnderAccountStayOpenToAdmins() {
        // The other half of the carve-out. Refusing these would leave a signed-in admin unable to
        // change their own password or enrol in 2FA, and would deny those to the only roles that
        // can administer the store.
        for (String path : List.of("/account/profile", "/account/settings", "/account/avatar", "/account/2fa")) {
            assertFalse(isShoppingPath(path),
                    path + " is an identity page, not a purchase page, and must stay reachable for an admin");
        }
    }

    @Test
    void adminAndPublicPathsAreNotShopping() {
        for (String path : List.of("/admin", "/admin/products", "/admin/orders", "/admin/users",
                "/admin/history", "/admin/transactions", "/admin/payments",
                "/login", "/register", "/logout", "/forgot", "/reset", "/verify-code",
                "/health", "/metrics", "/realtime", "/assets/css/app/base.css", "")) {
            assertFalse(isShoppingPath(path), path + " is not a storefront page");
        }
    }

    @Test
    void anUnknownAccountPageIsRefusedByDefault() {
        // The allowlist is an allowlist. A future /account/whatever is commerce until someone
        // decides otherwise, rather than silently becoming reachable for admins on the strength
        // of not being in a list that predates it.
        assertTrue(isShoppingPath("/account/not-a-page-yet"),
                "an account page nobody has classified must default to refused");
    }

    @Test
    void aNullPathIsNotShopping() {
        // getServletPath() is not null in a servlet container, but the rule should not throw if
        // it ever is: refusing an unknown path would 403 a request that was never on the storefront.
        assertFalse(isShoppingPath(null));
    }

    // ------------------------------------------------------------------
    // The filter: who is actually refused.
    // ------------------------------------------------------------------

    @Test
    void anAdminIsRefusedTheCatalogue() throws Exception {
        atPath("/products");
        asRole(User.Role.ADMIN);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void aSuperAdminIsRefusedTheCart() throws Exception {
        atPath("/cart");
        asRole(User.Role.SUPER_ADMIN);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void aRefusalTellsThePageItCameFromTheAdminPanel() throws Exception {
        // errors/403.jsp keys off this attribute to offer "Go to admin panel" instead of
        // "Back to store". Without it an admin is handed a link to a page that will refuse
        // them again, which reads as a broken site rather than a deliberate rule.
        atPath("/cart");
        asRole(User.Role.ADMIN);

        filter.doFilter(request, response, chain);

        verify(request).setAttribute("deniedFromAdminPanel", true);
    }

    @Test
    void aCustomerReachesTheStorefront() throws Exception {
        atPath("/cart");
        asRole(User.Role.CUSTOMER);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void anAdminReachesItsIdentityPages() throws Exception {
        // The carve-out has to hold at the filter, not just in the path table: an admin changing
        // their password is the single most common thing a signed-in admin does in this app.
        for (String path : List.of("/account/profile", "/account/settings", "/account/avatar", "/account/2fa")) {
            setUp();
            atPath(path);
            asRole(User.Role.ADMIN);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    @Test
    void anAnonymousVisitorReachesThePublicCatalogue() throws Exception {
        // The catalogue is public. Refusing here would break the storefront for every logged-out
        // shopper, and the rule is about the page, not about who is standing on it.
        atPath("/products");
        when(session.getAttribute("user")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void aRequestWithNoSessionReachesTheCatalogue() throws Exception {
        // AuthenticationFilter has already redirected anonymous users away from /cart and
        // /checkout by the time this filter runs, so a missing session here means the catalogue,
        // which is public.
        atPath("/products");
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void anAdminIsNotRefusedOutsideTheStorefront() throws Exception {
        // The filter is mapped broadly on purpose (it has to see /account/* to carve out the
        // identity pages). It must still pass /admin/* straight through, or the admin panel
        // would lock its own owner out.
        atPath("/admin/products");
        asRole(User.Role.SUPER_ADMIN);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void theForwardTargetIsInsideWebInf() throws Exception {
        // A view outside WEB-INF is servable directly, which would make the refusal page a public
        // endpoint and let anyone reach it without passing the filter.
        atPath("/cart");
        asRole(User.Role.ADMIN);

        filter.doFilter(request, response, chain);

        verify(request).getRequestDispatcher(eq("/WEB-INF/views/errors/403.jsp"));
    }

    @Test
    void aRefusedRequestSetsTheStatusBeforeForwarding() throws Exception {
        // Forwarding without the status would render the 403 body with a 200 status code, which
        // reads as success to every client and to every monitoring check on the app.
        atPath("/checkout");
        asRole(User.Role.ADMIN);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN));
    }

    @Test
    void theFilterDoesNotCreateASession() throws Exception {
        // getSession(false) is the only session call: a filter that created one would pin an
        // empty session onto every anonymous catalogue request.
        atPath("/products");

        filter.doFilter(request, response, chain);

        verify(request, never()).getSession();
        verify(request, never()).getSession(true);
    }

    @Test
    void chainReceivesTheSameRequestAndResponse() throws Exception {
        // A wrapper would be fine, but a filter that silently dropped one of them would show up
        // as an empty page rather than a stack trace.
        atPath("/products");
        asRole(User.Role.CUSTOMER);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void theFilterIsAFilterAndNotAlsoAServlet() {
        // Registering the same object as both would have it initialised twice under two different
        // lifecycles. The check is a compile-time fact today, kept as an assertion so a future
        // refactor toward a generic base class cannot quietly introduce it.
        assertInstanceOf(jakarta.servlet.Filter.class, filter);
        assertFalse(jakarta.servlet.Servlet.class.isInstance(filter),
                "a filter that is also a servlet would be registered twice");
    }

    @Test
    void servletResponseAndRequestAreUnchangedByAPassThrough() throws Exception {
        atPath("/admin/orders");
        asRole(User.Role.ADMIN);

        filter.doFilter(request, response, chain);

        verify(request, never()).setAttribute(anyString(), any());
        verify(response, never()).setStatus(any(Integer.class));
    }
}
