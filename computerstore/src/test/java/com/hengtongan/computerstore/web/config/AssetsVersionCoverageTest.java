package com.hengtongan.computerstore.web.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the cache-busting contract for static assets.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * {@code header.jspf} computes {@code assetsVersion} as the newest last-modified
 * time across a hand-maintained array of asset paths, and every asset reference
 * appends it as {@code ?v=${assetsVersion}}. The value is cached in application
 * scope, so it is computed once per deploy and reused for every request after
 * that.
 *
 * <p>That only works if the array covers every file referenced with the token. It
 * did not, and the omission was invisible:
 *
 * <ul>
 *   <li>{@code admin-nav.js} and {@code pwa.js} were added in this change,
 *       referenced with {@code ?v=}, and absent from the array. A deploy that
 *       changed only one of them would leave the version value unchanged, so every
 *       page would keep requesting the same URL.</li>
 *   <li>Eight further assets were referenced with no token at all, so no deploy
 *       could ever invalidate them.</li>
 * </ul>
 *
 * <p>Either way the browser and the service worker both key on the full URL
 * including the query string, so the stale bytes keep being served. That is the
 * hardest kind of bug to read, because the file on disk is correct and the page is
 * wrong.
 *
 * <h2>What is checked</h2>
 *
 * Both directions, so neither the array nor a reference can drift alone:
 * <ol>
 *   <li>every {@code .js}/{@code .css} reference in a {@code src} or {@code href}
 *       attribute carries {@code ?v=${assetsVersion}};</li>
 *   <li>every such reference appears in the array.</li>
 *   <li>the array lists no path that does not exist on disk.</li>
 * </ol>
 *
 * <p>Only tag attributes are matched, so the array's own string literals in
 * {@code header.jspf} are not mistaken for references. Images are excluded on
 * purpose: they are served through a servlet by product id, and the icon set is
 * fetched by the browser's own manifest machinery, neither of which goes through
 * this token.
 */
class AssetsVersionCoverageTest {

    private static final Path VIEWS = Path.of("src/main/webapp/WEB-INF/views");
    private static final Path HEADER = Path.of("src/main/webapp/WEB-INF/views/layouts/header.jspf");

    /** The token the array exists to produce. */
    private static final String TOKEN = "?v=${assetsVersion}";

    /** An asset referenced from a tag attribute. Group 1 is the full attribute value. */
    private static final Pattern ASSET_REF = Pattern.compile(
            "(?:src|href)=\"([^\"]*?/assets/[A-Za-z0-9._/-]+\\.(?:js|css)[^\"]*)\"");

    /** The array literal itself, so it can be read back out of header.jspf. */
    private static final Pattern ARRAY =
            Pattern.compile("String\\[\\]\\s+appAssets\\s*=\\s*\\{(.*?)\\};", Pattern.DOTALL);

    @Test
    void everyReferencedScriptAndStylesheetIsVersioned() throws IOException {
        List<String> unversioned = new ArrayList<>();
        for (Path view : views()) {
            for (String attr : referencedAssets(read(view))) {
                if (!attr.endsWith(TOKEN)) {
                    unversioned.add(view.getFileName() + " -> " + attr);
                }
            }
        }

        assertTrue(unversioned.isEmpty(),
                () -> "these assets are served with no cache-busting token, so no deploy can "
                        + "invalidate them and a stale copy is served until a hard reload. "
                        + "Append " + TOKEN + " to the reference and add the path to the "
                        + "appAssets array in header.jspf:\n  "
                        + String.join("\n  ", unversioned));
    }

    @Test
    void everyVersionedAssetIsListedInTheVersionArray() throws IOException {
        List<String> missing = new ArrayList<>(unlistedReferences(views(), listedInArray()));

        assertTrue(missing.isEmpty(),
                () -> "these assets carry " + TOKEN + " but are not in the appAssets array, so the "
                        + "version value does not change when only they do and the stale file is "
                        + "served from the HTTP cache and the service worker indefinitely. Add each "
                        + "path to the array in header.jspf:\n  " + String.join("\n  ", missing));
    }

    @Test
    void theArrayListsNoAssetThatDoesNotExist() throws IOException {
        // A typo in the array is silent: the entry never matches a resource and so
        // contributes nothing to the max, leaving the check above green while the
        // intended file is still unprotected.
        List<String> missingOnDisk = new ArrayList<>();
        for (String ref : listedInArray()) {
            if (!Files.isRegularFile(Path.of("src/main/webapp" + ref))) {
                missingOnDisk.add(ref);
            }
        }

        assertTrue(missingOnDisk.isEmpty(),
                () -> "the appAssets array lists paths that do not exist on disk, so they never "
                        + "contribute to the version value:\n  " + String.join("\n  ", missingOnDisk));
    }

    @Test
    void droppingOneListedAssetIsDetected() throws IOException {
        // Self-test: the check must reject the exact shape that was in the tree -- a
        // versioned reference the array does not carry -- and accept the real array.
        Set<String> complete = listedInArray();
        assertTrue(unlistedReferences(views(), complete).isEmpty(),
                "the real array is expected to be complete, so this self-test's premise is wrong");

        Set<String> minusOne = new LinkedHashSet<>(complete);
        assertTrue(minusOne.remove("/assets/js/pwa.js"), "fixture assumed pwa.js is listed");
        assertTrue(!unlistedReferences(views(), minusOne).isEmpty(),
                "removing a single listed asset must make the array read as incomplete, "
                        + "otherwise the check cannot detect the defect it exists for");
    }

    @Test
    void theTokenIsRequiredNotAssumed() {
        // Self-test for the first check: an attribute value without the token must
        // not read as versioned, and one with it must.
        String bare = "<script src=\"/assets/js/app.js\"></script>";
        String versioned = "<script src=\"/assets/js/app.js" + TOKEN + "\"></script>";

        assertTrue(!referencedAssets(bare).get(0).endsWith(TOKEN),
                "a bare reference must not be treated as versioned");
        assertTrue(referencedAssets(versioned).get(0).endsWith(TOKEN),
                "a reference carrying the token must be treated as versioned");
    }

    // ------------------------------------------------------------------
    // Helpers. The predicates are separated from the @Test bodies so the
    // self-tests above can feed them source text directly.
    // ------------------------------------------------------------------

    /** Asset attribute values in a file, in source order, deduplicated. */
    private static List<String> referencedAssets(String source) {
        List<String> refs = new ArrayList<>();
        Matcher m = ASSET_REF.matcher(source);
        while (m.find()) {
            if (!refs.contains(m.group(1))) {
                refs.add(m.group(1));
            }
        }
        return refs;
    }

    /** Versioned references whose path is missing from the array. */
    private static List<String> unlistedReferences(List<Path> views, Set<String> listed) {
        List<String> missing = new ArrayList<>();
        for (Path view : views) {
            for (String attr : referencedAssets(read(view))) {
                if (!attr.endsWith(TOKEN)) {
                    continue;
                }
                String path = assetPath(attr);
                if (!listed.contains(path)) {
                    missing.add(path + "  (referenced by " + view.getFileName() + ")");
                }
            }
        }
        return missing;
    }

    /** The server-relative path of a reference, with the context-path EL stripped. */
    private static String assetPath(String attributeValue) {
        String withoutToken = attributeValue.endsWith(TOKEN)
                ? attributeValue.substring(0, attributeValue.length() - TOKEN.length())
                : attributeValue;
        int assets = withoutToken.indexOf("/assets/");
        return assets < 0 ? withoutToken : withoutToken.substring(assets);
    }

    private static List<Path> views() throws IOException {
        try (Stream<Path> files = Files.walk(VIEWS)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".jsp") || n.endsWith(".jspf");
                    })
                    .sorted()
                    .toList();
        }
    }

    private static Set<String> listedInArray() {
        String header = read(HEADER);
        Matcher m = ARRAY.matcher(header);
        if (!m.find()) {
            throw new IllegalStateException(
                    "header.jspf no longer declares a String[] appAssets = { ... } array; the "
                            + "cache-busting version is computed from it, so this test cannot "
                            + "check coverage until that array is restored under this name");
        }
        Set<String> paths = new LinkedHashSet<>();
        Matcher quoted = Pattern.compile("\"(/assets/[^\"]+)\"").matcher(m.group(1));
        while (quoted.find()) {
            paths.add(quoted.group(1));
        }
        return paths;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + p, e);
        }
    }
}
