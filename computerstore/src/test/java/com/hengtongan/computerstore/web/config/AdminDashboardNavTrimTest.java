package com.hengtongan.computerstore.web.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guards role-restricted and always-available destinations in the admin
 * navigation.
 */
class AdminDashboardNavTrimTest {

    private static final String HEADER = "src/main/webapp/WEB-INF/views/layouts/header.jspf";
    private static final String ADMIN_NAV = "src/main/webapp/WEB-INF/views/layouts/admin-nav.jspf";
    private static final String SHOP_GUARD = "<c:if test=\"${not sessionScope.user.isAdmin()}\">";
    private static final String CART_GUARD = "<c:if test=\"${not sessionScope.user.isAdmin()}\">";
    private static final String SHOP_MARKER = "nav-shop-btn";
    private static final String CART_MARKER = "nav-cart-btn";
    private static final int GUARD_WINDOW = 1_200;

    @Test
    void shopAndCartRemainHiddenForAdmins() throws IOException {
        String header = Files.readString(Path.of(HEADER));
        assertTrue(guardedBy(header, SHOP_MARKER, SHOP_GUARD),
                "The Shop button must be hidden for admins because storefront routes reject them.");
        assertTrue(guardedBy(header, CART_MARKER, CART_GUARD),
                "The Cart button must be hidden for admins because storefront routes reject them.");
    }

    @Test
    void performanceRemainsAvailableFromInsightsOnTheDashboard() throws IOException {
        String adminNav = Files.readString(Path.of(ADMIN_NAV));
        String menuStart = "<ul class=\"dropdown-menu admin-nav-menu dropdown-hover-menu\" "
                + "aria-labelledby=\"adminNavInsights\">";
        int start = adminNav.indexOf(menuStart);
        int performance = adminNav.indexOf("href=\"${_ctx}/admin/performance\"", start);
        int menuEnd = performance < 0 ? -1 : adminNav.indexOf("</ul>", performance);

        assertTrue(start >= 0 && performance > start && menuEnd > performance,
                "Insights must contain a Performance link on every admin page, including Dashboard.");
        assertFalse(adminNav.substring(start, menuEnd).contains("<c:if"),
                "The Insights items must not be conditionally hidden on Dashboard.");
    }

    private static boolean guardedBy(String source, String marker, String guard) {
        int item = source.indexOf(marker);
        if (item < 0) {
            return false;
        }

        int from = Math.max(0, item - GUARD_WINDOW);
        String before = source.substring(from, item);
        int guardIndex = before.lastIndexOf(guard);
        return guardIndex >= 0
                && !before.substring(guardIndex + guard.length()).contains("</c:if>");
    }
}
