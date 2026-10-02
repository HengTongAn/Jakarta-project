package com.hengtongan.computerstore.web.monitoring;

import com.hengtongan.computerstore.core.repository.PageExperienceRepository.Bucket;
import com.hengtongan.computerstore.core.repository.PageExperienceRepository.ExperienceReport;
import com.hengtongan.computerstore.core.repository.PageExperienceRepository.Percentiles;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks that every EL property chain in {@code performance.jsp} resolves to a
 * real getter on the real bean.
 *
 * <h2>Why this exists</h2>
 *
 * The page returned HTTP 500 on its first live load. The cause was a one-character
 * difference between two names that agreed with each other and disagreed with the
 * bean: the servlet writes the JSON key {@code avg}, the JSP asked EL for
 * {@code ${ept.interactive.avg}}, and the bean's getter is {@code getAverage()}.
 * Because the JSON key and the JSP expression used the same word, every review of
 * the code found them consistent -- and the mismatch was only ever visible by
 * asking a container to render the page.
 *
 * <p>A missing property is not a compile error in JSP, not a failure in
 * {@code mvn test}, and not a failure in the repository's own tests. EL resolves
 * properties reflectively at request time and throws
 * {@code PropertyNotFoundException} into the response. The only automated check
 * that reaches it without a container is to resolve the chains here.
 *
 * <h2>What this does not cover</h2>
 *
 * <p>Only property chains whose root this page binds by hand; a chain rooted at a
 * variable absent from {@link #roots()} is skipped, since {@code param},
 * {@code sessionScope} and {@code fmt} prefixes are not beans and guessing at them
 * produces false alarms. That is the whole of the gap: a chain on a bean this page
 * does not bind would pass unchecked. Chains are located <em>inside</em>
 * compound expressions rather than only as whole expressions, because the second
 * defect above arrived wearing arithmetic.</p>
 */
class PerformanceJspPropertyTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/views/admin/performance.jsp");

    /** Request attributes and loop variables this page binds, with their types. */
    private static Map<String, Class<?>> roots() {
        Map<String, Class<?>> roots = new LinkedHashMap<>();
        roots.put("ept", ExperienceReport.class);
        // Both breakdown tables loop over a Bucket under the same variable name.
        roots.put("b", Bucket.class);
        return roots;
    }

    /** An EL expression, captured whole. */
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^{}]*)\\}");

    /**
     * A property chain appearing anywhere inside an expression.
     *
     * <p>Chains are found inside compound expressions rather than only as whole
     * expressions. That gap was not hypothetical: {@code ${ept.transfer.avg / 1024}}
     * is exactly the form that shipped and 500'd, because a chain had been silently
     * renamed from {@code average} to {@code avg} and then had arithmetic appended
     * to it. A whole-expression matcher would have skipped it as "not a pure chain"
     * and reported a clean page while the one figure an operator acts on was the
     * one throwing.</p>
     */
    private static final Pattern CHAIN =
            Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    @Test
    void everyPropertyChainInThePageResolvesToAGetter() throws IOException {
        String jsp = Files.readString(JSP);
        Map<String, Class<?>> roots = roots();

        List<String> checked = new ArrayList<>();
        List<String> broken = new ArrayList<>();

        Matcher expressions = EXPRESSION.matcher(jsp);
        while (expressions.find()) {
            Matcher chains = CHAIN.matcher(expressions.group(1));
            while (chains.find()) {
                String chain = chains.group();
                String root = chain.substring(0, chain.indexOf('.'));
                Class<?> type = roots.get(root);
                if (type == null) {
                    continue; // not one of ours: a scope, a param, a fmt prefix
                }
                if (!checked.add(chain)) {
                    continue; // the same chain appears in several cells
                }
                String failure = resolve(type, chain);
                if (failure != null) {
                    broken.add(expressions.group() + "  ->  " + failure);
                }
            }
        }

        if (!broken.isEmpty()) {
            fail("These EL expressions name a property that does not exist on the bean. "
                    + "Tomcat throws PropertyNotFoundException and the page returns 500, but "
                    + "nothing in mvn test can see it:\n  " + String.join("\n  ", broken));
        }
        assertTrue(checked.size() >= 15,
                () -> "only " + checked.size() + " chain(s) were checked: " + checked
                        + ". The pattern or the root map has drifted, so this test is no "
                        + "longer guarding the page.");
    }

    /**
     * Walks a chain against the bean, EL-style: {@code p50} means {@code getP50()}.
     *
     * @return null when every segment resolves, otherwise a description of the first
     *         segment that does not
     */
    private static String resolve(Class<?> root, String chain) {
        String[] segments = chain.split("\\.");
        Class<?> type = root;
        for (int i = 1; i < segments.length; i++) {
            Method getter = getter(type, segments[i]);
            if (getter == null) {
                return "no public getter for '" + segments[i] + "' on "
                        + type.getSimpleName() + " (looked for " + getterName(segments[i]) + ")";
            }
            type = getter.getReturnType();
        }
        return null;
    }

    private static Method getter(Class<?> type, String property) {
        String name = getterName(property);
        // Parameterless only. A zero-argument chain segment in EL cannot call
        // anything that takes arguments, so a matching signature with parameters is
        // not a match and must not be reported as one.
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String getterName(String property) {
        return "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
    }
}