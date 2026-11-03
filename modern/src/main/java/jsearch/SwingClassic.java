package jsearch;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * The 2002 screen, re-laid-out: the same two columns, the same vocabulary and
 * the same order of operations as the applet - 搜索 and 结果 on the left,
 * 预览/状态/消息 above 引擎/选项/关于 on the right - built with standard Swing
 * layouts instead of absolute coordinates. Nothing is copied from {@code controlSetting()} except the
 * Chinese labels and the shape of the flow.
 *
 * <p>This line answers a different question than {@link SwingReplica} ("can
 * Swing paint 2002 exactly") and {@link SwingDemo} ("what would the migration
 * look like"): here the 2002 information architecture stays, but the screen
 * behaves like a modern desktop window - it resizes, the query field grows,
 * the result list is a table with columns, and the clickable pills are real
 * tabs.
 *
 * <p>The habits the applet taught its users are kept: categories filter the
 * engine list, the engine list is multi-select, the second category is the
 * default, the first engine of that category is preselected, and the three
 * action buttons follow the same busy/idle states. The search itself runs on
 * one {@link SwingWorker} over the same {@link SearchService}/{@link
 * ResultCollector} as the rest of {@code modern/}. The look is Metal, the
 * JDK's cross-platform theme, rather than a custom skin.
 *
 * <p>Default is the canned fetcher (no network); pass a JSEngines-format file
 * as the first argument to go live through {@link HttpPageFetcher}.
 */
public final class SwingClassic {

    private static final String ENGINE_FILE = String.join("\n",
            "http://localhost:8901/",
            "LocalDemo / 本地演示引擎",
            "Chinese",
            "http://localhost:8901/search?q=^&page=`0",
            "<p><",
            "</p>",
            "",
            "http://www.google.com/",
            "Google / Google",
            "English",
            "http://www.google.com/search?q=^&start=`0",
            "<r>",
            "</r>",
            "");

    private static final String CANNED_PAGE = String.join("\n",
            "<html><body>",
            "<r>ref=\"http://example.com/en\"><b>English engine result</b></a>",
            "A preview scraped by the English engine.</r>",
            "<p><ref=\"http://example.com/zh\"><b>中文引擎结果</b></a>",
            "一条由中文引擎抓取的预览。</p>",
            "</body></html>");

    private static final String COPYING_TEXT =
            "JSearch version 2.0.0.0 [huntlin@public.xm.fj.cn], "
            + "Copyright (C) 1999-2002 Hunt Lin\n"
            + "JSearch comes with ABSOLUTELY NO WARRANTY; for details see COPYING.\n"
            + "This edition feeds the text inline - the original fetched COPYING.TXT "
            + "when _readTxt=Yes.";

    private static final String CREDITS_TEXT =
            "CREDITS.TXT is fetched from currUrl only when _readTxt=Yes.";

    private final List<Engine> engines;
    private final SearchService service;
    private final PageFetcher fetcher;
    /** Row order mirrors the table. */
    private final List<SearchResult> rows = new ArrayList<>();
    private final boolean demo;

    private List<Future<?>> running;

    private DefaultTableModel model;
    private JTable table;
    private JTextArea preview;
    private JTextArea messages;
    private DefaultListModel<String> statusModel;
    private JList<String> statusList;
    private JLabel total;
    private JTextField query;
    private JButton findNowBu;
    private JButton stopBu;
    private JButton newSearchBu;
    private JList<String> categoryList;
    private JList<String> searchEngineList;
    private JSpinner pages;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SwingClassic needs a display; this JVM is headless.");
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // keep the JVM default when Metal is unavailable
        }
        SwingUtilities.invokeLater(() -> new SwingClassic(args).show());
    }

    private SwingClassic(String[] args) {
        if (args.length > 0) {
            demo = false;
            try {
                engines = EngineRepository.load(Path.of(args[0]));
                fetcher = new HttpPageFetcher();
            } catch (Exception e) {
                throw new IllegalArgumentException("cannot load engine file: " + args[0], e);
            }
        } else {
            demo = true;
            engines = EngineRepository.parse(ENGINE_FILE);
            fetcher = url -> CANNED_PAGE;
        }
        service = new SearchService(4, fetcher, new HtmlBlockScraper());
    }

    private void show() {
        JFrame frame = new JFrame("JSearch - 2002 UI, Swing layout");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { service.close(); }
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // The applet's two columns: 搜索/结果 on the left, 预览/状态/消息 over
        // 引擎/选项/关于 on the right.
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.add(buildQueryPanel(), BorderLayout.NORTH);
        left.add(buildResultsPanel(), BorderLayout.CENTER);

        JTabbedPane lower = new JTabbedPane();
        lower.addTab("引擎", buildEnginesPanel());
        lower.addTab("选项", buildOptionsPanel());
        lower.addTab("关于", buildAboutPanel());

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildSidePanel(), lower);
        // The applet gave the preview a thin strip and the engine list most of
        // the right column; keep that imbalance.
        right.setResizeWeight(0.25);

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        main.setResizeWeight(0.66);
        root.add(main, BorderLayout.CENTER);

        findNowBu.addActionListener(e -> search(query.getText().trim()));
        query.addActionListener(e -> search(query.getText().trim()));
        stopBu.addActionListener(e -> {
            if (running != null) service.cancelAll(running);
            addStatus("已请求停止……");
        });
        newSearchBu.addActionListener(e -> newSearch());
        categoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) rebuildEngineList();
        });

        initCategories();

        frame.setContentPane(root);
        // Do not open taller than the usable desktop: on a 1366x768 screen at
        // 125% scaling the engine/options/关于 tabs would sit behind the taskbar.
        java.awt.Rectangle screen =
                GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.min(1000, screen.width - 60);
        int height = Math.min(720, screen.height - 60);
        frame.setSize(width, height);
        frame.setMinimumSize(new Dimension(Math.min(820, width), Math.min(560, height)));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        main.setDividerLocation(0.66);
        right.setDividerLocation(0.25);

        messages.append("JSearch - 2002 UI, Swing layout (modern core)\n");
        messages.append("engines: " + engines.size() + "\n");

        // Canned mode is a demo: run the bundled query once so the window
        // opens with the same kind of content as the archive screenshots.
        if (demo) {
            SwingUtilities.invokeLater(findNowBu::doClick);
        }
    }

    // ===== sections =====

    private JPanel buildQueryPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("搜索"));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 4, 3, 4);

        c.gridx = 0;
        panel.add(new JLabel("查询内容:"), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(query = new JTextField("java"), c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JLabel hint = new JLabel("关键词之间以空格分隔，且均为“与”的关系");
        hint.setForeground(new java.awt.Color(0x666666));
        panel.add(hint, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(findNowBu = new JButton("开始搜索"));
        buttons.add(stopBu = new JButton("停止搜索"));
        stopBu.setEnabled(false);
        buttons.add(newSearchBu = new JButton("新建搜索"));
        c.gridy = 2;
        panel.add(buttons, c);

        return panel;
    }

    private JPanel buildResultsPanel() {
        model = new DefaultTableModel(new Object[] {"主题", "位置", "引擎"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = table.getSelectedRow();
            if (viewRow >= 0) {
                preview.setText(rows.get(table.convertRowIndexToModel(viewRow)).preview());
            }
        });

        total = new JLabel("总计：0");
        JPanel header = new JPanel(new BorderLayout());
        header.add(total, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createTitledBorder("结果"));
        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JTabbedPane buildSidePanel() {
        preview = area();
        statusModel = new DefaultListModel<>();
        statusList = new JList<>(statusModel);
        messages = area();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("预览", new JScrollPane(preview));
        tabs.addTab("状态", new JScrollPane(statusList));
        tabs.addTab("消息", new JScrollPane(messages));
        return tabs;
    }

    private JPanel buildEnginesPanel() {
        categoryList = new JList<>(new DefaultListModel<>());
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setVisibleRowCount(2);

        searchEngineList = new JList<>(new DefaultListModel<>());
        searchEngineList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // 2002 stacks 类别 over 搜索引擎 in one narrow right column.
        JPanel categories = titledPanel("类别", new JScrollPane(categoryList));

        JPanel both = new JPanel(new BorderLayout(0, 6));
        both.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        both.add(categories, BorderLayout.NORTH);
        both.add(titledPanel("搜索引擎", new JScrollPane(searchEngineList)), BorderLayout.CENTER);
        return both;
    }

    private Component buildOptionsPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        int row = 0;
        row = addOption(form, row, "最大显示数", new JSpinner(new SpinnerNumberModel(4, 1, 16, 1)));
        row = addOption(form, row, "搜索页数", pages = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1)));
        row = addOption(form, row, "最快连接数", new JSpinner(new SpinnerNumberModel(10, 1, 10, 1)));
        row = addOption(form, row, "验证URL", new JComboBox<>(new String[] {"Yes", "No"}));
        row = addOption(form, row, "语言", new JComboBox<>(new String[] {"English", "Chinese"}));
        row = addOption(form, row, "系统浏览器", new JComboBox<>(new String[] {
                "IE (Windows)", "NS (Windows)", "NS (Linux)", "Other browsers"}));
        row = addOption(form, row, "浏览器路径", new JTextField(24));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        panel.add(form, BorderLayout.NORTH);
        // The form is taller than the tab at the minimum window size; scroll
        // instead of clipping the last rows.
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private JPanel buildAboutPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        panel.add(titledPanel("COPYING", new JScrollPane(area(COPYING_TEXT))));
        panel.add(titledPanel("CREDITS", new JScrollPane(area(CREDITS_TEXT))));
        return panel;
    }

    // ===== behavior =====

    private void initCategories() {
        List<String> categories = new ArrayList<>();
        DefaultListModel<String> categoryModel = new DefaultListModel<>();
        for (Engine engine : engines) {
            if (!categories.contains(engine.category())) {
                categories.add(engine.category());
                categoryModel.addElement(engine.category());
            }
        }
        categoryList.setModel(categoryModel);
        // The applet's own default: the second category, not the first
        // (JSApplet.java:595). On the shipped table that is English.
        if (categories.size() > 1) categoryList.setSelectedIndex(1);
        rebuildEngineList();
    }

    /** The engine list always mirrors the selected category, like the original. */
    private void rebuildEngineList() {
        String category = categoryList.getSelectedValue();
        DefaultListModel<String> model = new DefaultListModel<>();
        for (Engine engine : engines) {
            if (category == null || category.equals(engine.category())) {
                model.addElement(engine.name());
            }
        }
        searchEngineList.setModel(model);
        // categorySelect() preselects the first engine of the category.
        if (!model.isEmpty()) searchEngineList.setSelectedIndex(0);
    }

    private void search(String text) {
        String q = text.isEmpty() ? "java" : text;
        Set<String> chosen = new LinkedHashSet<>(searchEngineList.getSelectedValuesList());
        List<Engine> selected = new ArrayList<>();
        for (Engine engine : engines) {
            if (chosen.isEmpty() || chosen.contains(engine.name())) selected.add(engine);
        }
        int levelCount = (Integer) pages.getValue();

        rows.clear();
        model.setRowCount(0);
        total.setText("总计：0");
        query.setEnabled(false);
        findNowBu.setEnabled(false);
        stopBu.setEnabled(true);
        newSearchBu.setEnabled(false);

        String started = "搜索“" + q + "”： " + selected.size() + " 个引擎，每个 "
                + levelCount + " 页\n";
        addStatus(started);
        messages.append(started);

        SwingWorker<Void, SearchResult> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                ResultCollector collector = new ResultCollector();
                collector.onResult(this::publish);
                collector.onError(cause -> SwingUtilities.invokeLater(() ->
                        messages.append("错误：" + cause + "\n")));
                running = service.search(selected, q, levelCount, collector);
                service.awaitAll(running, collector);
                return null;
            }

            @Override protected void process(List<SearchResult> chunks) {
                for (SearchResult result : chunks) {
                    rows.add(result);
                    model.addRow(new Object[] {
                            result.title(), result.url(), result.engine().name()});
                }
                total.setText("总计：" + rows.size());
            }

            @Override protected void done() {
                query.setEnabled(true);
                findNowBu.setEnabled(true);
                stopBu.setEnabled(false);
                newSearchBu.setEnabled(true);
                addStatus("搜索完成。结果：" + rows.size());
                messages.append("search complete - " + rows.size()
                        + " unique result(s) (duplicates dropped by the collector)\n");
                if (!rows.isEmpty()) {
                    table.setRowSelectionInterval(0, 0);
                    table.scrollRectToVisible(table.getCellRect(0, 0, true));
                }
            }
        };
        worker.execute();
    }

    private void newSearch() {
        rows.clear();
        model.setRowCount(0);
        total.setText("总计：0");
        preview.setText("");
        findNowBu.setEnabled(true);
        stopBu.setEnabled(false);
        newSearchBu.setEnabled(true);
        addStatus("新建搜索。");
    }

    private void addStatus(String line) {
        statusModel.addElement(line);
        statusList.ensureIndexIsVisible(statusModel.size() - 1);
    }

    // ===== factories =====

    private static JPanel titledPanel(String title, Component content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea area() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setMargin(new Insets(6, 8, 6, 8));
        return area;
    }

    private static JTextArea area(String text) {
        JTextArea area = area();
        area.setText(text);
        return area;
    }

    private static int addOption(JPanel panel, int row, String label, Component control) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 4, 10);

        c.gridx = 0;
        panel.add(new JLabel(label + ":"), c);
        c.gridx = 1;
        panel.add(control, c);
        return row + 1;
    }
}
