package jsearch;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * The desktop reference UI: what the 2002 applet becomes when its AWT shell is
 * replaced by Swing, while the search core stays the one from {@code modern/}.
 *
 * <p>The mapping, element by element:
 *
 * <ul>
 *   <li>a {@code List} for results plus two label headers &rarr; a
 *       {@link JTable} with real columns (主题/位置/引擎) and row selection</li>
 *   <li>three {@code TextArea}s stacked at identical bounds and switched by
 *       clicking colored labels &rarr; a {@link JTabbedPane} (预览/状态/消息) -
 *       the manual CardLayout switching and the whole clickable-pill language
 *       collapse into one native component</li>
 *   <li>{@code CardLayout} for 引擎/选项/关于 &rarr; another tabbed pane</li>
 *   <li>the hand-rolled connection pool, the {@code _stop} flag, the sleep
 *       polling and the two static locks &rarr; one {@link SwingWorker} per
 *       search; cancellation is {@code cancel(true)}, row updates ride
 *       {@code publish}/{@code process} onto the event thread, and the
 *       {@link ResultCollector} keeps owning the dedup exactly as before</li>
 *   <li>the URL-keyed {@code Hashtable} &rarr; engines identified by
 *       (name, category) via {@link EngineRepository}, so two engines may
 *       share a URL and both survive</li>
 * </ul>
 *
 * <p>Default is the canned fetcher: no network, and the table fills with the
 * same fixture page {@link Demo} uses. Pass a JSEngines-format file as the
 * first argument to load real engines through {@link HttpPageFetcher} - e.g.
 * {@code ../runner/JSENGINES.TXT} with the demo server up.
 */
public final class SwingDemo {

    /** The exact shape that broke the original: two engines, one URL. */
    private static final String ENGINE_FILE = String.join("\n",
            "http://www.google.com/",
            "Google / Google",
            "English",
            "http://www.google.com/search?q=^&start=`0",
            "<r>",
            "</r>",
            "",
            "http://www.google.com/",
            "GB_Chinese Google",
            "Chinese",
            "http://www.google.com/search?q=^&lr=lang_zh-CN&start=`0",
            "<r>",
            "</r>",
            "");

    /** Three results, one URL appearing twice, to exercise dedup. */
    private static final String CANNED_PAGE = String.join("\n",
            "<html><body>",
            "<r>ref=\"http://example.com/a\"><b>First result</b></a>",
            "the first preview text</r>",
            "<r>ref=\"http://example.com/b\"><b>Second result</b></a>",
            "the second preview text</r>",
            "<r>ref=\"http://example.com/a\"><b>First result again</b></a>",
            "a duplicate url, must be dropped</r>",
            "</body></html>");

    private final List<Engine> engines;
    private final SearchService service;
    private final PageFetcher fetcher;
    /** Row order mirrors the table; one lookup instead of four parallel maps. */
    private final List<SearchResult> rows = new ArrayList<>();

    private DefaultTableModel model;
    private JTable table;
    private JTextArea preview;
    private JTextArea status;
    private JTextArea messages;
    private JLabel total;
    private JButton searchButton;
    private JSpinner levels;
    private List<JCheckBox> engineBoxes;
    private List<Future<?>> running;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SwingDemo needs a display; this JVM is headless.");
            return;
        }
        SwingUtilities.invokeLater(() -> new SwingDemo(args).show());
    }

    private SwingDemo(String[] args) {
        if (args.length > 0) {
            try {
                engines = EngineRepository.load(Path.of(args[0]));
                fetcher = new HttpPageFetcher();
            } catch (Exception e) {
                throw new IllegalArgumentException("cannot load engine file: " + args[0], e);
            }
        } else {
            engines = EngineRepository.parse(ENGINE_FILE);
            fetcher = url -> CANNED_PAGE;
        }
        service = new SearchService(4, fetcher, new HtmlBlockScraper());
    }

    private void show() {
        JFrame frame = new JFrame("JSearch 2.1 - Swing reference (modern core)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { service.close(); }
        });

        // Top: the query row (2002: searchPa - TextField, three buttons).
        JTextField query = new JTextField("java", 24);
        searchButton = new JButton("开始搜索");
        JButton stopButton = new JButton("停止搜索");
        JButton newButton = new JButton("新建搜索");
        levels = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));
        JPanel row = new JPanel();
        row.add(new JLabel("查询内容:"));
        row.add(query);
        row.add(searchButton);
        row.add(stopButton);
        row.add(newButton);
        row.add(new JLabel("页数:"));
        row.add(levels);

        // Engine checkboxes (2002: category list + engine list; with identity
        // fixed to (name, category), one flat selectable list is enough).
        JPanel engineRow = new JPanel();
        engineRow.add(new JLabel("引擎:"));
        List<JCheckBox> boxes = new ArrayList<>();
        for (Engine engine : engines) {
            JCheckBox box = new JCheckBox(engine.name(), true);
            boxes.add(box);
            engineRow.add(box);
        }
        engineBoxes = boxes;

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(row);
        top.add(engineRow);

        // Center-left: the results table (2002: List + two label headers).
        model = new DefaultTableModel(new Object[] {"主题", "位置", "引擎"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow >= 0 && viewRow < rows.size()) {
                preview.setText(rows.get(table.convertRowIndexToModel(viewRow)).preview());
            }
        });

        // Center-right: 预览/状态/消息 (2002: three TextAreas + clickable pills).
        preview = new JTextArea();
        preview.setEditable(false);
        preview.setLineWrap(true);
        preview.setWrapStyleWord(true);
        status = new JTextArea();
        status.setEditable(false);
        messages = new JTextArea();
        messages.setEditable(false);
        JTabbedPane psm = new JTabbedPane();
        psm.addTab(">>预览", new JScrollPane(preview));
        psm.addTab(">>状态", new JScrollPane(status));
        psm.addTab(">>消息", new JScrollPane(messages));

        // Bottom tabs: 引擎/选项/关于 (2002: CardLayout switched by radio pills).
        JTabbedPane eoa = new JTabbedPane();
        JPanel engineInfo = new JPanel(new GridLayout(0, 1));
        for (Engine engine : engines) {
            engineInfo.add(new JLabel(engine.name()
                    + "   [" + engine.category() + "]   " + engine.urlTemplate()));
        }
        eoa.addTab("引擎", new JScrollPane(engineInfo));
        eoa.addTab("选项", new JLabel("  2002 年的 Choice 选项在 Swing 里是 JSpinner/JCheckBox - 从略"));
        eoa.addTab("关于", new JLabel("  JSearch modern/ reference UI - Swing edition"));

        total = new JLabel("总计: 0");
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(table), psm);
        split.setResizeWeight(0.62);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(total, BorderLayout.WEST);
        bottom.add(eoa, BorderLayout.CENTER);

        frame.add(top, BorderLayout.NORTH);
        frame.add(split, BorderLayout.CENTER);
        frame.add(bottom, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> search(query.getText().trim()));
        stopButton.addActionListener(e -> {
            if (running != null) service.cancelAll(running);
            messages.append("[ui] stop requested\n");
        });
        newButton.addActionListener(e -> {
            rows.clear();
            model.setRowCount(0);
            total.setText("总计: 0");
            preview.setText("");
            messages.append("[ui] new search\n");
        });
        query.addActionListener(e -> search(query.getText().trim()));

        frame.setPreferredSize(new Dimension(860, 560));
        frame.pack();
        frame.setLocationRelativeTo(null);
        if (System.getenv("JSEARCH_DUMP") != null) {
            // One-shot component-tree dump 3s after showing, for layout
            // forensics against a screenshot. Off unless the env var is set.
            new java.util.Timer(true).schedule(new java.util.TimerTask() {
                @Override public void run() {
                    dump(frame, 0);
                }
            }, 3000);
        }
        frame.setVisible(true);
        messages.append("JSearch 2.1 Swing reference - engines loaded: " + engines.size() + "\n");
        messages.append("two engines share one URL and both survive - identity is (name, category)\n");
    }

    private void search(String text) {
        String query = text.isEmpty() ? "java" : text;
        List<Engine> selected = new ArrayList<>();
        for (int i = 0; i < engines.size(); i++) {
            if (engineBoxes.get(i).isSelected()) selected.add(engines.get(i));
        }
        int levelCount = (Integer) levels.getValue();

        rows.clear();
        model.setRowCount(0);
        total.setText("总计: 0");
        searchButton.setEnabled(false);
        String started = "[ui] search started for '" + query + "' across "
                + selected.size() + " engine(s), " + levelCount + " page(s) each\n";
        messages.append(started);
        status.append(started);

        SwingWorker<Void, SearchResult> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                ResultCollector collector = new ResultCollector();
                // The collector's callback fires on the scraping thread;
                // publish() is the only sanctioned bridge onto the event
                // thread. The 2002 code bridged with setText-then-flag races.
                collector.onResult(this::publish);
                collector.onError(cause -> SwingUtilities.invokeLater(() ->
                        messages.append("[error] " + cause + "\n")));
                running = service.search(selected, query, levelCount, collector);
                service.awaitAll(running, collector);
                return null;
            }

            @Override protected void process(List<SearchResult> chunks) {
                for (SearchResult result : chunks) {
                    rows.add(result);
                    model.addRow(new Object[] {
                            result.title(), result.url(), result.engine().name()});
                    total.setText("总计: " + rows.size());
                }
            }

            @Override protected void done() {
                searchButton.setEnabled(true);
                messages.append("[ui] search complete - " + rows.size()
                        + " unique result(s) (duplicates dropped by the collector)\n");
                status.append("Search complete. Results: " + rows.size() + "\n");
                if (!rows.isEmpty()) {
                    // The 2002 applet pre-selected the first engine result the
                    // same way; without this the preview starts empty.
                    table.setRowSelectionInterval(0, 0);
                    table.scrollRectToVisible(table.getCellRect(0, 0, true));
                }
            }
        };
        worker.execute();
    }

    private static void dump(Container c, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");
        Rectangle b = c.getBounds();
        System.out.printf("%s%s %dx%d @%d,%d%n",
                indent, c.getClass().getSimpleName(), b.width, b.height, b.x, b.y);
        for (Component k : c.getComponents()) {
            if (k instanceof Container) dump((Container) k, depth + 1);
            else System.out.printf("%s  %s %dx%d @%d,%d%n",
                    indent, k.getClass().getSimpleName(),
                    k.getWidth(), k.getHeight(), k.getX(), k.getY());
        }
        if (depth == 0) {
            JTable t = (JTable) find(c, JTable.class);
            if (t != null) {
                for (int i = 0; i < t.getColumnCount(); i++) {
                    System.out.printf("  column '%s' width=%d%n",
                            t.getColumnName(i), t.getColumnModel().getColumn(i).getWidth());
                }
            }
        }
    }

    private static Component find(Container c, Class<?> type) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) return k;
            if (k instanceof Container) {
                Component hit = find((Container) k, type);
                if (hit != null) return hit;
            }
        }
        return null;
    }
}
