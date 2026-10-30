package jsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The executable half of the archive's prose: every number a document states
 * about the tree, recomputed from the tree.
 *
 * <p>{@link EncodingGuard} stops the archive's bytes being rewritten. This class
 * exists because a second, quieter kind of damage showed up in an audit of the
 * documentation: the measured claims were all true when written and had simply
 * stopped being recomputed. {@code docs/COMPARISON.md} described {@code modern/}
 * as thirteen files and ~1,800 lines with a largest class of 175 — accurate
 * before {@code WebServer} and the live mode landed, and wrong by a factor of
 * two afterwards. Two line-number citations in {@code docs/ANALYSIS.md} pointed
 * at nothing. A web demo was documented on port 8081 while the code listens on
 * 8080.
 *
 * <p>None of those errors is visible to a compiler, and all of them are cheap to
 * test, so they are tested here. Each check derives a value from a file and then
 * requires the document that quotes it to quote the derived value. Editing the
 * code without editing the prose now fails the build, which is the only way a
 * number in a document stays honest.
 *
 * <p>Run as {@code java -cp out jsearch.DocsGuard}; exits non-zero on the first
 * mismatching claim. Skips, like the archive fixtures, when the repository root
 * is not beside {@code modern/}, so a standalone copy of {@code modern/} still
 * builds. The suite deliberately lives outside {@link Tests}: it checks the
 * documented size of that suite, and a test cannot count itself.
 */
public final class DocsGuard {

    private static final String README = "README.md";
    private static final String COMPARISON = "docs/COMPARISON.md";
    private static final String GENERATIONS = "docs/GENERATIONS.md";
    private static final String ANALYSIS = "docs/ANALYSIS.md";
    private static final String MODERN_README = "modern/README.md";
    private static final String JSEARXNG_README = "jsearxng/README.md";
    private static final String RUNNER_README = "runner/README.md";

    private final Path root;
    private final List<String> failures = new ArrayList<>();
    private final List<String> passed = new ArrayList<>();

    private DocsGuard(Path root) {
        this.root = root;
    }

    public static void main(String[] args) {
        Optional<Path> root = ArchiveFixtures.root();
        if (!root.isPresent()) {
            System.out.println("skip: no archive root (ENGINES/ and Releases/); "
                    + "set -Djsearch.archive=<repo root>");
            return;
        }
        DocsGuard guard = new DocsGuard(root.get());
        System.out.println("Documentation guard: " + root.get());
        System.out.println();
        guard.run();
        for (String name : guard.passed) {
            System.out.println("  ok   " + name);
        }
        System.out.println();
        System.out.println(guard.passed.size() + " claims verified, "
                + guard.failures.size() + " stale");
        guard.failures.forEach(f -> System.out.println("  FAIL " + f));
        System.exit(guard.failures.isEmpty() ? 0 : 1);
    }

    private void run() {
        checkModernSize();
        checkSuiteSizes();
        checkPorts();
        checkJsearxng();
        checkGenerations();
        checkLineCitations();
        checkInventory();
    }

    // ---- modern/ as COMPARISON.md measures it ---------------------------

    /**
     * The whole code-structure table, rebuilt from {@code modern/src}. The file
     * count, the two line totals, the largest class and the average all move
     * whenever a class is added, which is precisely why the table drifted.
     */
    private void checkModernSize() {
        List<Path> main = javaFiles("modern/src/main");
        List<Path> test = javaFiles("modern/src/test");
        long mainLines = linesOf(main);
        long testLines = linesOf(test);

        String comparison = text(COMPARISON);
        claim("COMPARISON file counts", comparison,
                "| " + main.size() + " (" + (main.size() + test.size()) + " with tests) |");
        claim("COMPARISON line totals", comparison,
                "| " + number(mainLines) + " main (" + number(mainLines + testLines) + " with tests) |");

        Path largest = main.get(0);
        for (Path file : main) {
            if (lines(file) > lines(largest)) {
                largest = file;
            }
        }
        claim("COMPARISON largest class", comparison,
                "| " + number(lines(largest)) + " lines (" + nameOf(largest) + ") |");
        claim("COMPARISON average class", comparison,
                "| " + Math.round((double) mainLines / main.size()) + " lines |");
    }

    /**
     * The two suite sizes the documents quote. {@code Tests} registers one
     * lambda per test, so counting registrations counts the suite; the
     * descendant's suite is three assertion helpers, whose definitions are
     * subtracted because a definition is not a check.
     */
    private void checkSuiteSizes() {
        int tests = occurrences(text("modern/src/test/java/jsearch/Tests.java"),
                Pattern.compile("^\\s*(test|testArchive)\\(\"", Pattern.MULTILINE));
        claim("modern/README test count", text(MODERN_README), tests + " tests");
        claim("COMPARISON test count", text(COMPARISON), "| **Tests** | 0 | " + tests + " |");

        String suite = text("jsearxng/JSearXNGTest.java");
        int assertions = occurrences(suite, Pattern.compile("\\bok\\("))
                + occurrences(suite, Pattern.compile("\\beq\\("))
                + occurrences(suite, Pattern.compile("\\bthrowsWith\\(")) - 3;
        claim("jsearxng/README check count", text(JSEARXNG_README), assertions + " checks");
        claim("README check count", text(README), assertions + " checks");
    }

    /** Ports are a classic copy-paste casualty: 8080 became 8081 in prose. */
    private void checkPorts() {
        claim("WebServer default port", text("modern/src/main/java/jsearch/WebServer.java"),
                "int port = 8080;");
        claim("README web demo port", text(README), "http://localhost:8080");
        for (String doc : Arrays.asList(README, MODERN_README, COMPARISON)) {
            expect("no stray 8081 in " + doc, !text(doc).contains("8081"),
                    "document still points at port 8081");
        }
    }

    /** The descendant's constants, against what its README and the root one say. */
    private void checkJsearxng() {
        String app = text("jsearxng/JSearXNG.java");
        int engines = occurrences(app, Pattern.compile("new EngineEntry\\("));
        claim("README engine count", text(README), numberWord(engines) + " engines");
        claim("jsearxng worker and queue sizes", app, "new SearchRunner(8, 64)");
        claim("jsearxng fetch timeout", app, "Duration.ofSeconds(6)");
        claim("jsearxng search budget", app, "Duration.ofSeconds(8)");
        claim("jsearxng listen port", app, "int port = 8092;");
        claim("README jsearxng URL", text(README), "http://127.0.0.1:8092/");

        int records = occurrences(text("runner/JSENGINES.TXT"),
                Pattern.compile("^http://", Pattern.MULTILINE)) / 2;
        claim("runner engine records", text(RUNNER_README), records + " of " + records);
    }

    /**
     * GENERATIONS.md's four measured rows, rebuilt line by line and byte by
     * byte. These were the claims that survived the audit intact; keeping them
     * under test costs nothing and protects the strongest page in the archive.
     */
    private void checkGenerations() {
        String generations = text(GENERATIONS);
        String[][] rows = {
                {"versions/2000-08/JSApplet.java", null,
                        "| `versions/2000-08/` | 1.2.3 | Aug 2000 | "},
                {"versions/2001-12/JSApplet/JSApplet.java", null,
                        "| `versions/2001-12/JSApplet/` | 1.2.3 | Dec 2001 | "},
                {"versions/2002-01/Sources/JSApplet.java", "versions/2002-01/Sources/SearchThread.java",
                        "| `versions/2002-01/Sources/` | 2.0.0.0 | Jan 2002 | "},
                {"Sources/JSApplet.java", "Sources/SearchThread.java",
                        "| `Sources/` (live) | 2.0.0.0 + fix | Mar 2002 | "},
        };
        for (String[] row : rows) {
            StringBuilder expected = new StringBuilder(row[2])
                    .append(size(row[0])).append(" | ");
            expected.append(row[1] == null ? "— (monolith)" : size(row[1])).append(" |");
            claim("GENERATIONS row for " + row[0], generations, expected.toString());
        }
        expect("live JSApplet is the January bytes",
                Arrays.equals(bytes("Sources/JSApplet.java"),
                        bytes("versions/2002-01/Sources/JSApplet.java")),
                "Sources/JSApplet.java no longer matches the 2002-01 snapshot");
    }

    /**
     * Line-number citations rot faster than any other claim, because every edit
     * above the cited line moves it. Both references here were wrong when the
     * audit found them.
     */
    private void checkLineCitations() {
        String analysis = text(ANALYSIS);
        claim("ANALYSIS valurlCh citation", analysis,
                "Sources/SearchThread.java:" + lineOf("Sources/SearchThread.java", "valurlCh"));
        claim("ANALYSIS JSApplet finalize citation", analysis,
                "JSApplet.java:" + lineOf("Sources/JSApplet.java", "public void finalize()"));
        claim("ANALYSIS SearchThread finalize citation", analysis,
                "SearchThread.java:" + lineOf("Sources/SearchThread.java", "public void finalize()"));
    }

    /** Small inventory facts the README and the comparison table state outright. */
    private void checkInventory() {
        int classes = 0;
        try (Stream<Path> files = Files.list(root.resolve("artifacts/Classes"))) {
            classes = (int) files.filter(f -> f.getFileName().toString().endsWith(".class")).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        claim("README stale class count", text(README), "of the " + classes + " compiled");

        int high = 0;
        for (byte b : bytes("ENGINES/lycos_en.html")) {
            if ((b & 0xFF) > 0x7F) {
                high++;
            }
        }
        expect("lycos_en.html has ten high bytes", high == 10, "found " + high);
        claim("README lycos encoding", text(README), "ENGINES/lycos_en.html` is ISO-8859-1");

        String[] buildSections = {"**PowerShell (Windows):**", "**Bash (Linux/Mac):**",
                "**Make (any platform with make):**", "**Maven (any platform with mvn):**",
                "**Docker:**"};
        int options = 0;
        String modern = text(MODERN_README);
        for (String section : buildSections) {
            if (modern.contains(section)) {
                options++;
            }
        }
        claim("COMPARISON build paths", text(COMPARISON),
                options + " options (PowerShell/Bash/Make/Maven/Docker)");
    }

    // ---- plumbing -------------------------------------------------------

    private void claim(String name, String document, String expected) {
        expect(name, document.contains(expected),
                "document does not contain: " + expected);
    }

    private void expect(String name, boolean ok, String detail) {
        if (ok) {
            passed.add(name);
        } else {
            failures.add(name + " — " + detail);
        }
    }

    /** A path's line count and byte count, the way GENERATIONS.md prints them. */
    private String size(String rel) {
        return number(lines(path(rel))) + " lines · " + number(bytes(rel).length) + " B";
    }

    /** The 1-based number of the first line containing {@code needle}. */
    private int lineOf(String rel, String needle) {
        String[] lines = new String(bytes(rel), StandardCharsets.ISO_8859_1).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(needle)) {
                return i + 1;
            }
        }
        throw new AssertionError(rel + " no longer contains: " + needle);
    }

    private List<Path> javaFiles(String rel) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root.resolve(rel))) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + rel, e);
        }
        return files;
    }

    private long linesOf(List<Path> files) {
        long total = 0;
        for (Path file : files) {
            total += lines(file);
        }
        return total;
    }

    /** Newline count, matching {@code wc -l} and independent of encoding. */
    private static int lines(Path file) {
        int count = 0;
        for (byte b : readBytes(file)) {
            if (b == '\n') {
                count++;
            }
        }
        return count;
    }

    private static int occurrences(String haystack, Pattern pattern) {
        int count = 0;
        Matcher matcher = pattern.matcher(haystack);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String numberWord(int value) {
        String[] words = {"zero", "one", "two", "three", "four", "five", "six",
                "seven", "eight", "nine", "ten", "eleven", "twelve"};
        return value < words.length ? words[value] : Integer.toString(value);
    }

    private static String nameOf(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".java".length());
    }

    private Path path(String rel) {
        return root.resolve(rel);
    }

    private String text(String rel) {
        return new String(bytes(rel), StandardCharsets.UTF_8);
    }

    private byte[] bytes(String rel) {
        return readBytes(path(rel));
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}
