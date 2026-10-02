package com.hengtongan.computerstore.web.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the product detail page against the failure that EL property errors
 * are invisible to the compiler and to every other test in this project: a
 * JSP can reference a property the view model does not have, compile cleanly,
 * pass 315 tests, and then return HTTP 500 for every product in the catalog.
 *
 * <p>That is exactly what happened. {@code detail.jsp} linked the brand name
 * to {@code ${product.brandId}}, but {@code ProductDetailVM} carried only
 * {@code brandName}. EL resolves unknown properties at request time, so all 150
 * product detail pages returned 500 while the build stayed green.
 *
 * <p>Jasper can only catch this by rendering, and the deploy used for review
 * renders exactly the pages it serves. So this test does the cheap half of
 * that job statically: every {@code ${product.<prop>}} and
 * {@code ${rp.<prop>}} reference in the detail page must resolve to a real
 * getter on the class actually bound to {@code product}, and to a real method
 * on whatever type {@code relatedProducts} holds.
 *
 * <p>Source files are read as text rather than loaded, so the check runs
 * without a servlet container or a database.
 */
class ProductDetailBrandLinkTest {

    private static final Path DETAIL_JSP = Path.of(
            "src/main/webapp/WEB-INF/views/customer/products/detail.jsp");
    private static final Path DETAIL_VM = Path.of(
            "src/main/java/com/hengtongan/computerstore/core/domain/dto/ProductDetailVM.java");

    /** {@code ${product.foo}} / {@code ${rp.foo}} / {@code ${product.foo.bar}} */
    private static final Pattern EL_PROPERTY = Pattern.compile(
            "\\$\\{(?:product|rp)\\.([A-Za-z_][A-Za-z0-9_]*)");

    /**
     * Any public no-arg method EL can call. Deliberately not restricted to
     * get/is: a boolean such as {@code hasImage()} is read as the property
     * {@code hasImage}, and a parser that only understood get/is would report
     * it missing -- which is the very mistake this test exists to catch.
     */
    private static final Pattern GETTER = Pattern.compile(
            "\\bpublic\\s+(?!class\\b|static\\b|final\\b)[\\w.<>\\[\\], ?]+\\s+([a-z]\\w*)\\s*\\(\\s*\\)");

    @Test
    @DisplayName("every ${product.x} in detail.jsp resolves to a getter on ProductDetailVM")
    void detailPageOnlyReferencesPropertiesTheViewModelHas() throws IOException {
        assertTrue(Files.exists(DETAIL_JSP), "detail.jsp moved; update this test");
        assertTrue(Files.exists(DETAIL_VM), "ProductDetailVM moved; update this test");

        String jsp = Files.readString(DETAIL_JSP, StandardCharsets.UTF_8);
        String vm = Files.readString(DETAIL_VM, StandardCharsets.UTF_8);
        List<String> available = gettersOf(vm);

        // A silent zero here would make this test vacuously pass, which is the
        // exact failure mode it exists to catch.
        assertTrue(available.size() > 5,
                "parsed only " + available.size() + " getters out of ProductDetailVM; "
                        + "the parser is broken, not the page");

        // ${rp.x} belongs to the related-products type, not this VM, so it is
        // checked by relatedProductPropertiesResolve instead.
        List<String> missing = new ArrayList<>();
        for (String property : referencedProperties(jsp, "product")) {
            if (!available.contains(property)) {
                missing.add(property);
            }
        }
        assertTrue(missing.isEmpty(),
                "detail.jsp references properties ProductDetailVM does not expose, "
                        + "so every product detail page returns HTTP 500: " + missing);
    }

    @Test
    @DisplayName("every ${rp.x} in detail.jsp resolves on the related-products type")
    void relatedProductPropertiesResolve() throws IOException {
        Path cardVm = Path.of(
                "src/main/java/com/hengtongan/computerstore/core/domain/dto/ProductCardVM.java");
        assertTrue(Files.exists(cardVm), "ProductCardVM moved; update this test");

        List<String> jspRefs = referencedProperties(
                Files.readString(DETAIL_JSP, StandardCharsets.UTF_8), "rp");
        if (jspRefs.isEmpty()) {
            // No related-products block on the page: nothing to check, and saying
            // so is better than passing on an empty list.
            return;
        }
        List<String> available = gettersOf(Files.readString(cardVm, StandardCharsets.UTF_8));
        assertTrue(available.size() > 5,
                "parsed only " + available.size() + " getters out of ProductCardVM");

        List<String> missing = new ArrayList<>();
        for (String property : jspRefs) {
            if (!available.contains(property)) {
                missing.add(property);
            }
        }
        assertTrue(missing.isEmpty(),
                "detail.jsp's related-products block references properties "
                        + "ProductCardVM does not expose: " + missing);
    }

    @Test
    @DisplayName("the brand link resolves to a real id, not a silent zero")
    void brandLinkCarriesTheBrandId() throws IOException {
        String vm = Files.readString(DETAIL_VM, StandardCharsets.UTF_8);
        assertTrue(gettersOf(vm).contains("brandId"),
                "ProductDetailVM has no getBrandId, so /products?brand=${product.brandId} "
                        + "cannot work. The VM must carry brandId the way it carries categoryId.");
        assertTrue(Files.readString(DETAIL_JSP, StandardCharsets.UTF_8).contains("product.brandId"),
                "detail.jsp no longer links the brand; if that was deliberate, drop this test "
                        + "rather than leaving it asserting a link that is gone");
    }

    @Test
    @DisplayName("the parser finds the getters this test depends on")
    void parserFindsGetters() throws IOException {
        List<String> available = gettersOf(Files.readString(DETAIL_VM, StandardCharsets.UTF_8));
        // Spot-check a few names the page uses. If the getter regex silently
        // stopped matching, available would be empty or tiny and every other
        // assertion here would pass for the wrong reason.
        for (String expected : List.of("productId", "name", "price", "status",
                "imageUrl", "stockQuantity", "hasImage", "brandId")) {
            assertTrue(available.contains(expected),
                    "getter parser missed " + expected + "; found " + available);
        }
    }

    private static List<String> gettersOf(String javaSource) {
        List<String> names = new ArrayList<>();
        Matcher m = GETTER.matcher(javaSource);
        while (m.find()) {
            // EL reads getFoo() as "foo" but calls hasImage() as "hasImage",
            // so the get/is prefix is stripped and anything else is kept as is.
            String name = m.group(1);
            if (name.startsWith("get") && name.length() > 3
                    || name.startsWith("is") && name.length() > 2) {
                name = name.substring(name.startsWith("get") ? 3 : 2);
                name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
            }
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Distinct property names EL pulls off one binding, e.g. {@code product}. */
    private static List<String> referencedProperties(String jsp, String binding) {
        List<String> names = new ArrayList<>();
        Matcher m = EL_PROPERTY.matcher(jsp);
        while (m.find()) {
            // group(1) is the property; re-check the binding via the full match
            // so "product" and "rp" stay separate.
            String full = m.group();
            if (full.startsWith("${" + binding + ".") && !names.contains(m.group(1))) {
                names.add(m.group(1));
            }
        }
        return names;
    }
}
