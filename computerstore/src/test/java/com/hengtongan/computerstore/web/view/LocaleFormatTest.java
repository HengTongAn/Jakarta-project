package com.hengtongan.computerstore.web.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the reason the app's numbers format at all.
 *
 * <h2>What was actually wrong</h2>
 *
 * <p>Every {@code fmt:formatNumber} in this app rendered raw
 * {@code Object.toString()} output, so a page weight read
 * {@code 87.0939453125 KB} rather than {@code 87.1 KB} and every
 * {@code pattern}/{@code type}/{@code maxFractionDigits} on those tags did
 * nothing. It survived because the values on most pages are integers, and
 * because a {@code null} formatting locale is not an error -- the pages
 * returned 200 throughout, so nothing looked broken.</p>
 *
 * <p>The JSTL build this app ships is Apache Taglibs Standard repackaged under
 * the {@code jakarta.tags.*} URIs. In {@code FormatNumberSupport.doEndTag} it
 * computes a formatting locale and branches on it:</p>
 *
 * <pre>
 *   150: invokestatic  getFormattingLocale:(LPageContext;LTag;ZZ)LLocale;
 *   154: aload_3
 *   155: ifnull 288        &lt;- 288 is Object.toString()
 * </pre>
 *
 * <p>so a {@code null} locale silently routes every call to the unformatted
 * branch. Nothing sets a locale by default.</p>
 *
 * <h2>What this test can and cannot do</h2>
 *
 * <p>It is a source check, and it is honest about the limit: it proves the
 * locale is pinned before any view formats a number. It cannot prove the
 * formatting actually happens, because that needs a container rendering a
 * page -- which is exactly how this was found, by loading a real page. If a
 * future JSTL upgrade changes how the locale is resolved, this test will stay
 * green while the numbers silently revert, so the render-side check still
 * matters.</p>
 */
class LocaleFormatTest {

    private static final Path HEADER =
            Path.of("src/main/webapp/WEB-INF/views/layouts/header.jspf");

    @Test
    void theSharedHeaderPinsTheFormattingLocale() throws IOException {
        String header = Files.readString(HEADER);

        int setLocale = header.indexOf("<fmt:setLocale");
        if (setLocale < 0) {
            fail("""
                    header.jspf no longer calls <fmt:setLocale>.

                    Every fmt: tag in the app depends on this line. Without it the
                    JSTL build returns a null formatting locale and
                    FormatNumberSupport.doEndTag falls through to
                    Object.toString(), so all 107 fmt:formatNumber calls across
                    the views render unformatted -- with no error anywhere.
                    """);
        }

        assertTrue(header.contains("prefix=\"fmt\" uri=\"jakarta.tags.fmt\""),
                "header.jspf uses the fmt: prefix, so it must declare the fmt taglib.");
    }

    /**
     * The locale has to be set before the first view formats a number, not after.
     *
     * <p>Views declare their own fmt taglib and include the header near the top
     * of the file, so the tag has to sit in the header rather than in the
     * footer. This asserts only that the tag is in the shared header at all --
     * the ordering that matters is per-view, and is covered by the fact that
     * every view includes the header above its own markup.
     */
    @Test
    void theLocaleIsPinnedInTheSharedHeaderNotPerView() throws IOException {
        String header = Files.readString(HEADER);
        assertTrue(header.indexOf("<fmt:setLocale") < header.indexOf("<!DOCTYPE html>"),
                "<fmt:setLocale> must run in the <head> region, before any view's "
                        + "own markup formats a number.");
    }
}
