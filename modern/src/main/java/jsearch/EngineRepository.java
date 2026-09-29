package jsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads engines from the 7-line record format JSearch used.
 *
 * <p>The format is preserved so the archive's own {@code JSEngines.txt} files can
 * still be read: six fields &mdash; key, name, category, URL template, block
 * start, block end &mdash; followed by a blank line, repeated.
 *
 * <p>Two changes from the original {@code getEngData()}, both deliberate:
 *
 * <ol>
 *   <li>Results are a {@code List<Engine>}, not a {@code Hashtable} keyed on the
 *       URL. The URL is not unique &mdash; the shipped file contains two engines
 *       both keyed {@code http://www.google.com/} &mdash; and the original
 *       silently dropped one of them.</li>
 *   <li>Duplicate <em>identities</em> are rejected loudly. Identity is
 *       {@code (name, category)}, so a genuinely duplicated engine is a data
 *       error worth failing on rather than overwriting.</li>
 * </ol>
 */
public final class EngineRepository {

    private static final int FIELDS_PER_RECORD = 6;

    private EngineRepository() {
    }

    public static List<Engine> load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return parse(lines);
    }

    public static List<Engine> parse(List<String> lines) {
        List<Engine> engines = new ArrayList<>();
        Map<Engine, Integer> seenIdentities = new LinkedHashMap<>();

        int index = 0;
        while (index < lines.size()) {
            if (lines.get(index).trim().isEmpty()) {
                index++;
                continue;
            }
            if (index + FIELDS_PER_RECORD > lines.size()) {
                throw new IllegalArgumentException(
                        "truncated record at line " + (index + 1)
                                + ": expected " + FIELDS_PER_RECORD + " fields");
            }

            List<String> fields = lines.subList(index, index + FIELDS_PER_RECORD);
            Engine engine = new Engine(
                    fields.get(1).trim(),                       // name
                    fields.get(2).trim(),                       // category
                    fields.get(3),                              // url template (kept raw: markers are significant)
                    new Engine.Block(fields.get(4), fields.get(5)));

            if (seenIdentities.containsKey(engine)) {
                throw new IllegalArgumentException(
                        "duplicate engine '" + engine.name() + "' [" + engine.category()
                                + "] at lines " + (seenIdentities.get(engine) + 1)
                                + " and " + (index + 1));
            }
            seenIdentities.put(engine, index);
            engines.add(engine);
            index += FIELDS_PER_RECORD;
        }

        if (engines.isEmpty()) {
            throw new IllegalArgumentException("no engines found");
        }
        return engines;
    }

    /** Reads an engine file straight out of a string, for fixtures and tests. */
    public static List<Engine> parse(String content) {
        List<String> lines = new ArrayList<>();
        for (String line : content.split("\n", -1)) {
            lines.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }
        return parse(lines);
    }
}
