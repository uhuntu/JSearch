package jsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The archive's own files as test fixtures: the four captured pages in
 * {@code ENGINES/} and the shipped engine database {@code Releases/JSEngines.txt}.
 *
 * <p>These are the pages {@code HtmlBlockScraper} was written to parse, so they
 * are worth more as fixtures than any inline sample: they are real markup, saved
 * from the live engines on 2001-12-27, and they are the place where the shipped
 * block markers can be checked against reality.
 *
 * <p>Everything here is read as GBK. The archive is GBK throughout; decoding it
 * as anything else produces mojibake, which is exactly what one of the tests
 * checks for. If the GBK charset is unavailable on this JVM the fixtures are
 * reported as unavailable and the tests that need them are skipped.
 *
 * <p>The archive root is found by walking up from the working directory and from
 * the location of this class, looking for a directory holding both
 * {@code ENGINES/} and {@code Releases/}. Set {@code -Djsearch.archive=<dir>} to
 * point at it directly. When it cannot be found — the {@code modern/} tree copied
 * out on its own — the fixture tests skip rather than fail.
 */
final class ArchiveFixtures {

    private static final String ARCHIVE_PROPERTY = "jsearch.archive";
    private static final String GBK = "GBK";

    private static final Path root = findRoot();
    private static final Map<String, String> pages = new HashMap<>();
    private static List<Engine> shippedEngines;

    /** True when the archive was found and can be decoded. */
    static boolean available() {
        return root != null && charset().isPresent();
    }

    /** The reason {@link #available()} is false, for a skip message. */
    static String unavailableReason() {
        if (root == null) {
            return "archive root not found (ENGINES/ and Releases/); "
                    + "set -D" + ARCHIVE_PROPERTY + "=<repo root>";
        }
        return "charset " + GBK + " not available on this JVM";
    }

    /**
     * One of the captured pages, decoded from GBK.
     *
     * @param name a file name under {@code ENGINES/}, e.g. {@code google_en.html}
     */
    static String page(String name) {
        requireAvailable();
        return pages.computeIfAbsent(name, n -> {
            try {
                return new String(Files.readAllBytes(root.resolve("ENGINES").resolve(n)), charset().get());
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read ENGINES/" + n, e);
            }
        });
    }

    /** The engines as the archive shipped them, parsed from {@code JSEngines.txt}. */
    static List<Engine> shippedEngines() {
        requireAvailable();
        if (shippedEngines == null) {
            String text;
            try {
                text = new String(
                        Files.readAllBytes(root.resolve("Releases").resolve("JSEngines.txt")),
                        charset().get());
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read Releases/JSEngines.txt", e);
            }
            shippedEngines = EngineRepository.parse(text);
        }
        return shippedEngines;
    }

    /** The shipped engine whose name starts with {@code prefix}. */
    static Engine shippedEngine(String prefix) {
        for (Engine engine : shippedEngines()) {
            if (engine.name().startsWith(prefix)) {
                return engine;
            }
        }
        throw new AssertionError("no shipped engine named '" + prefix + "*' in "
                + shippedEngines());
    }

    private static void requireAvailable() {
        if (!available()) {
            throw new AssertionError("archive fixtures unavailable: " + unavailableReason());
        }
    }

    private static Optional<Charset> charset() {
        try {
            return Optional.of(Charset.forName(GBK));
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            return Optional.empty();
        }
    }

    private static Path findRoot() {
        List<Path> starts = new ArrayList<>();
        String property = System.getProperty(ARCHIVE_PROPERTY);
        if (property != null) {
            starts.add(Paths.get(property));
        }
        starts.add(Paths.get("").toAbsolutePath());
        Path codeLocation = codeLocation();
        if (codeLocation != null) {
            starts.add(codeLocation);
        }
        for (Path start : starts) {
            Path candidate = start.toAbsolutePath().normalize();
            while (candidate != null) {
                if (Files.isDirectory(candidate.resolve("ENGINES"))
                        && Files.isDirectory(candidate.resolve("Releases"))) {
                    return candidate;
                }
                candidate = candidate.getParent();
            }
        }
        return null;
    }

    private static Path codeLocation() {
        try {
            return Paths.get(ArchiveFixtures.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }

    private ArchiveFixtures() {
    }
}
