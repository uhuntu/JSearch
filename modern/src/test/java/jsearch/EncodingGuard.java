package jsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Encoding guard for the archive: the executable half of the warning in
 * {@code README.md}.
 *
 * <p>Every original file here is GBK. Opened in an editor set to the wrong
 * encoding and then saved, it comes back as UTF-8 — plausible-looking Chinese,
 * permanently corrupted bytes, and nothing on screen to tell the two apart. Git
 * shows the difference; this class says it in words.
 *
 * <p>Detection is by <em>strict</em> decoding, which is the part that matters.
 * GBK-encoded Chinese is almost never valid UTF-8, and vice versa, so trying
 * both decoders and seeing which one rejects the bytes separates them reliably.
 * Decoding with the default (replacing) fallback does not: every byte sequence
 * decodes to <em>something</em>, so a file that was already converted passes a
 * check written that way.
 *
 * <p>Chinese that is UTF-8 on disk is often <em>also</em> well-formed GBK,
 * because UTF-8 continuation bytes fall inside GBK's trail-byte range, so the
 * two decoders alone are not always enough. The tie-breaker is that GB2312
 * keeps both bytes of every Chinese character at 0xA1 or above, while UTF-8
 * spends two bytes in 0x80–0xBF on each one: a file containing bytes in
 * 0x80–0xA0 cannot be GB2312. Across this archive the split is absolute — every
 * GBK file has zero such bytes, every UTF-8 file has dozens.
 *
 * <p>Three groups of original files are checked, and whatever falls outside them
 * is reported as unlisted rather than silently ignored:
 *
 * <ul>
 *   <li>{@code GBK_FILES} — the Chinese sources, pages and engine databases.
 *       ASCII is accepted too: a license file has no bytes a conversion could
 *       destroy, so nothing is lost either way.</li>
 *   <li>{@code LATIN1_FILES} — the one page that was never Chinese. {@code
 *       ENGINES/lycos_en.html} has exactly ten bytes above 0x7F, and they are
 *       Western European punctuation, so it is ISO-8859-1 rather than GBK. The
 *       README hedges with "almost every original file"; this is the exception,
 *       and naming it is cheaper than pretending it away.</li>
 *   <li>{@code modern/src/**}{@code / *.java} and the {@code *.md} files — written
 *       during the archival pass, so they must be UTF-8: the mirror image of the
 *       first group, stopping GBK from spreading into new writing.</li>
 * </ul>
 *
 * <p>Run it as {@code java -cp out jsearch.EncodingGuard}; it exits non-zero if
 * any file is in the wrong encoding, and reports files it cannot find. The
 * archive root is located the same way {@link ArchiveFixtures} locates it.
 */
public final class EncodingGuard {

    /** What a file's bytes decode as, judged with strict decoders. */
    public enum Kind {
        /** No byte above 0x7F: every encoding agrees, nothing can be lost. */
        ASCII,
        /** Valid GBK, invalid UTF-8 — the state the archive is preserved in. */
        GBK,
        /** Valid UTF-8, invalid GBK — an original file has been converted. */
        UTF8,
        /** Valid UTF-8 carrying a byte-order mark. */
        UTF8_BOM,
        /**
         * Neither valid UTF-8 nor valid GBK, and no NUL bytes, so it is some eight-bit
         * Western page. Named for ISO-8859-1, which maps every byte and so can
         * never fail; whether a given page was Latin-1 or cp1252 is unknowable
         * unless it uses the 0x80–0x9F block the two disagree about.
         */
        LATIN1,
        /** Contains NUL bytes. */
        BINARY
    }

    private static final String GBK_NAME = "GBK";
    private static final String UTF8_NAME = "UTF-8";
    private static final String LATIN1_NAME = "ISO-8859-1";

    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".java", ".html", ".htm", ".txt", ".md", ".dat"));

    private static final List<String> ORIGINAL_ROOTS = Arrays.asList(
            "Sources", "ENGINES", "Releases", "versions", "docs");

    /** The original artifacts, relative to the archive root. */
    private static final List<String> GBK_FILES = Arrays.asList(
            "Sources/JSApplet.java",
            "Sources/SearchThread.java",
            "Sources/codebase.dat",
            "ENGINES/baidu_cn.html",
            "ENGINES/engines.txt",
            "ENGINES/google_cn.html",
            "ENGINES/google_en.html",
            "Releases/COPYING.TXT",
            "Releases/CREDITS.TXT",
            "Releases/JSEngines.txt",
            "Releases/JSearch.html",
            "versions/2000-08/CODEBASE.DAT",
            "versions/2000-08/COPYING.TXT",
            "versions/2000-08/CREDITS.TXT",
            "versions/2000-08/JSApplet.java",
            "versions/2000-08/JSENGINES.TXT",
            "versions/2001-12/JSApplet/codebase.dat",
            "versions/2001-12/JSApplet/COPYING.TXT",
            "versions/2001-12/JSApplet/CREDITS.TXT",
            "versions/2001-12/JSApplet/JSApplet.java",
            "versions/2001-12/JSApplet/JSEngines.txt",
            "versions/2001-12/JSApplet/JSearch.htm",
            "versions/2002-01/Releases/COPYING.TXT",
            "versions/2002-01/Releases/CREDITS.TXT",
            "versions/2002-01/Releases/JSEngines.txt",
            "versions/2002-01/Releases/JSearch.html",
            "versions/2002-01/Sources/codebase.dat",
            "versions/2002-01/Sources/JSApplet.java",
            "versions/2002-01/Sources/SearchThread.java",
            "docs/txts/1.软件可行性分析报告.txt",
            "docs/txts/2.软件计划说明书.txt",
            "docs/txts/3.软件需求分析规格说明书.txt",
            "docs/txts/4.软件设计概要说明书.txt",
            "docs/txts/5.软件设计详细说明书.txt",
            "docs/txts/6.软件源代码清单.txt",
            "docs/txts/7.软件测试说明书.txt",
            "docs/txts/8.软件操作说明书.txt");

    /**
     * The page that was always Western European. Ten bytes above 0x7F in
     * 22,755 — {@code A0} eight times, {@code AB}, {@code BB} — and none of them
     * in the 0x80–0x9F where Latin-1 and cp1252 part company, so either reading
     * gives the same text.
     */
    private static final List<String> LATIN1_FILES = Arrays.asList(
            "ENGINES/lycos_en.html");

    /**
     * What each original file list is allowed to decode as. ASCII is allowed
     * everywhere because a pure-ASCII file has no bytes that depend on the
     * answer.
     */
    private static final List<Expectation> EXPECTED = Arrays.asList(
            new Expectation(GBK_NAME, GBK_FILES,
                    Kind.GBK, Kind.ASCII),
            new Expectation(LATIN1_NAME, LATIN1_FILES,
                    Kind.LATIN1, Kind.ASCII));

    /** What the files written during the archival pass must decode as. */
    private static final Expectation UTF8_EXPECTATION = new Expectation(UTF8_NAME,
            Collections.<String>emptyList(),
            "files written during the archival pass are UTF-8",
            Kind.UTF8, Kind.UTF8_BOM, Kind.ASCII);

    /**
     * An encoding a group of files is expected to still be in, with the kinds
     * that satisfy it and a sentence saying why it matters.
     */
    private static final class Expectation {
        private final String encoding;
        private final List<String> files;
        private final String why;
        private final Set<Kind> allowed;

        private Expectation(String encoding, List<String> files, Kind... allowed) {
            this(encoding, files, "the bytes were rewritten, not merely misread on screen", allowed);
        }

        private Expectation(String encoding, List<String> files, String why, Kind... allowed) {
            this.encoding = encoding;
            this.files = files;
            this.why = why;
            this.allowed = EncodingGuard.kinds(allowed);
        }
    }

    private static Set<Kind> kinds(Kind... kinds) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(kinds)));
    }

    private final Path root;
    private final Charset gbk;
    private final List<String> inventory = new ArrayList<>();

    private EncodingGuard(Path root, Charset gbk) {
        this.root = root;
        this.gbk = gbk;
    }

    /**
     * Opens the guard for whichever copy of the archive this JVM is looking at.
     * Empty when the root cannot be found or the JVM has no GBK, which are the
     * same two conditions under which {@link ArchiveFixtures} skips its tests.
     */
    public static Optional<EncodingGuard> open() {
        Optional<Path> root = ArchiveFixtures.root();
        Optional<Charset> gbk = gbk();
        if (!root.isPresent() || !gbk.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new EncodingGuard(root.get(), gbk.get()));
    }

    /**
     * Problems among the original artifacts: files that no longer decode as
     * they should, and files that have gone missing, which for an archive costs
     * the same as a rewrite. Empty means the archive survived intact.
     */
    public List<String> checkArchive() {
        List<String> found = new ArrayList<>();
        for (Expectation expectation : EXPECTED) {
            for (String rel : expectation.files) {
                report(rel, expectation, found);
            }
        }
        return found;
    }

    /** Problems among the files the archival pass wrote, which must be UTF-8. */
    public List<String> checkUtf8Files() {
        List<String> found = new ArrayList<>();
        checkTree("modern/src", ".java", true, found);
        checkTree(".", ".md", false, found);
        checkTree("docs", ".md", false, found);
        return found;
    }

    /**
     * Original text files that no expectation covers yet: an artifact someone
     * added without deciding what encoding preserves it. Reported, not judged —
     * a new file is news, not damage.
     */
    public List<String> unlistedFiles() {
        Set<String> known = new HashSet<>(GBK_FILES);
        known.addAll(LATIN1_FILES);
        List<String> found = new ArrayList<>();
        for (String dir : ORIGINAL_ROOTS) {
            for (Path file : textFiles(root.resolve(dir), true)) {
                String rel = rel(file);
                if (!known.contains(rel) && !writtenDuringArchivalPass(rel)) {
                    found.add(rel);
                }
            }
        }
        return found;
    }

    /** True for the Markdown and Java files {@link #checkUtf8Files()} already owns. */
    private static boolean writtenDuringArchivalPass(String rel) {
        return rel.endsWith(".md") || (rel.startsWith("modern/src/") && rel.endsWith(".java"));
    }

    /** Everything currently known to be wrong, as one list of sentences. */
    public List<String> allProblems() {
        List<String> found = checkArchive();
        found.addAll(checkUtf8Files());
        return found;
    }

    /**
     * One {@code "<encoding>  <path>"} line per file classified so far, most
     * recent least; what {@link #main} prints.
     */
    public List<String> inventory() {
        return inventory;
    }

    public int checked() {
        return inventory.size();
    }

    public static void main(String[] args) {
        Optional<EncodingGuard> maybe = open();
        if (!maybe.isPresent()) {
            System.out.println("skip: no archive root with ENGINES/ and Releases/, "
                    + "or charset " + GBK_NAME + " unavailable on this JVM; "
                    + "set -Djsearch.archive=<repo root>");
            return;
        }
        EncodingGuard guard = maybe.get();
        System.out.println("Encoding guard: " + guard.root);
        System.out.println();

        List<String> problems = guard.allProblems();
        for (String line : guard.inventory()) {
            System.out.println("  " + line);
        }
        for (String rel : guard.unlistedFiles()) {
            System.out.println("  info  " + rel + " (unlisted: add it to GBK_FILES)");
        }

        System.out.println();
        System.out.println(guard.checked() + " files checked, " + problems.size() + " wrong");
        problems.forEach(p -> System.out.println("  FAIL " + p));
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    // ---- checking -------------------------------------------------------

    /**
     * Classifies one expected file and appends to {@code found} when it has left
     * the encoding it belongs in, whether by being rewritten or by going away.
     */
    private void report(String rel, Expectation expectation, List<String> found) {
        Path file = root.resolve(rel);
        if (!Files.isRegularFile(file)) {
            found.add(rel + " expected " + expectation.encoding + " but is missing");
            return;
        }
        Kind kind = classify(read(file), gbk);
        inventory.add(pad(describe(kind)) + rel);
        if (!expectation.allowed.contains(kind)) {
            found.add(rel + " expected " + expectation.encoding + " but is " + describe(kind)
                    + " (" + expectation.why + ")");
        }
    }

    /** Checks every matching file under {@code dir}, assuming nothing about which exist. */
    private void checkTree(String dir, String extension, boolean recursive, List<String> found) {
        Path base = root.resolve(dir);
        if (!Files.isDirectory(base)) {
            return;
        }
        for (Path file : textFiles(base, recursive)) {
            String rel = rel(file);
            if (rel.endsWith(extension)) {
                report(rel, UTF8_EXPECTATION, found);
            }
        }
    }

    private static List<Path> textFiles(Path base, boolean recursive) {
        if (!Files.isDirectory(base)) {
            return Collections.emptyList();
        }
        try (Stream<Path> walk = recursive ? Files.walk(base) : Files.list(base)) {
            List<Path> files = new ArrayList<>();
            walk.filter(Files::isRegularFile).forEach(file -> {
                String name = file.getFileName().toString().toLowerCase();
                if (TEXT_EXTENSIONS.stream().anyMatch(name::endsWith)) {
                    files.add(file);
                }
            });
            Collections.sort(files);
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + base, e);
        }
    }

    // ---- decoding -------------------------------------------------------

    /**
     * Judges a file's encoding. Order matters: the BOM is unambiguous, a NUL
     * byte means binary whatever else is true, and ASCII is settled before the
     * two decoders get to agree or disagree about bytes that are below 0x7F
     * either way.
     */
    static Kind classify(byte[] bytes, Charset gbk) {
        if (hasBom(bytes)) {
            return Kind.UTF8_BOM;
        }
        for (byte b : bytes) {
            if (b == 0) {
                return Kind.BINARY;
            }
        }
        if (!hasNonAscii(bytes)) {
            return Kind.ASCII;
        }
        boolean utf8 = decodesAs(bytes, StandardCharsets.UTF_8);
        boolean looksGbk = decodesAs(bytes, gbk);
        if (utf8 && looksGbk) {
            return hasContinuationBytes(bytes) ? Kind.UTF8 : Kind.GBK;
        }
        if (utf8) {
            return Kind.UTF8;
        }
        if (looksGbk) {
            return Kind.GBK;
        }
        // Anything left is eight-bit Western text: it has bytes above 0x7F that
        // are neither UTF-8 continuation bytes nor GBK pairs.
        return Kind.LATIN1;
    }

    private static boolean decodesAs(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean hasBom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    /**
     * True when some byte sits in 0x80–0xA0: the range UTF-8 uses for its
     * continuation bytes and GB2312 never uses for either half of a Chinese
     * character, which keeps everything at 0xA1 or above. See the class comment.
     */
    private static boolean hasContinuationBytes(byte[] bytes) {
        for (byte b : bytes) {
            int value = b & 0xFF;
            if (value >= 0x80 && value <= 0xA0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonAscii(byte[] bytes) {
        for (byte b : bytes) {
            if ((b & 0xFF) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    // ---- plumbing -------------------------------------------------------

    static Optional<Charset> gbk() {
        try {
            return Optional.of(Charset.forName(GBK_NAME));
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            return Optional.empty();
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    private String rel(Path file) {
        StringBuilder sb = new StringBuilder();
        for (Path part : root.relativize(file)) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private static String describe(Kind kind) {
        switch (kind) {
            case GBK: return "GBK";
            case UTF8: return "UTF-8";
            case UTF8_BOM: return "UTF-8+BOM";
            case ASCII: return "ASCII";
            case LATIN1: return "ISO-8859-1";
            case BINARY: return "binary";
            default: throw new AssertionError("undescribed kind " + kind);
        }
    }

    private static String pad(String kind) {
        StringBuilder sb = new StringBuilder(kind);
        while (sb.length() < 14) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
