package jsearch;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;

/**
 * A 1:1 visual replica of the 2002 AWT applet, rebuilt in Swing on the
 * {@code modern/} core. Every coordinate, color and font below is copied from
 * {@code Sources/JSApplet.java} {@code controlSetting()} - the same constants,
 * the same absolute layout, the same quirks (the 类别/搜索引擎 tabs wearing the
 * background color, the English category selected by default).
 *
 * <p>What cannot be pixel-identical is the chrome of the native widgets - the
 * 2002 buttons were painted by Windows, the scrollbars by the AWT peer. On
 * Linux the AWT peers were Metal-style anyway, so the replica sits close.
 * Everything Java paints itself - labels, panels, backgrounds - is exact.
 *
 * <p>Mapping notes: AWT {@code Label} &rarr; {@link JLabel} with the same
 * alignment constants (Swing has the same three); {@code CardLayout} &rarr;
 * the same {@link CardLayout} (Swing ships it); {@code List} &rarr;
 * {@link JList}; {@code Choice} &rarr; {@link JComboBox}; the CheckboxGroup
 * radios &rarr; {@link JCheckBox}es used as radios. The search runs on one
 * {@link SwingWorker} over the same {@link SearchService}/{@link
 * ResultCollector} as the other reference UIs. Default is the canned fetcher
 * (no network); pass a JSEngines-format file as the first argument to go live
 * through {@link HttpPageFetcher} - e.g. {@code ../runner/JSENGINES.TXT} with
 * the demo server up.
 */
public final class SwingReplica {

    // Colors copied verbatim from JSApplet.java.
    private static final Color PAGE = new Color(6724095);      // :348 applet background
    private static final Color TAB_BLUE = new Color(3355647);  // 搜索/结果/>>消息 tabs
    private static final Color TAB_GREEN = new Color(25600);   // >>预览/>>状态/选项/关于
    private static final Color DARK_TEXT = new Color(25600);

    // Fonts copied verbatim. The original's "Tab Style Font"/"Little Font"
    // families do not exist, so every JVM resolves them to Dialog anyway.
    private static final Font TAB_FONT = new Font("Dialog", Font.BOLD, 14);
    private static final Font LITTLE_FONT = new Font("Dialog", Font.PLAIN, 10);
    private static final Font COMMON_FONT = new Font("Dialog", Font.PLAIN, 12);

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
            "<p><ref=\"http://example.com/a\"><b>二十四年后的第一条结果</b></a>",
            "预览一：这条结果由一个2002年的Java Applet抓取，解析代码没有改动过一行。</p>",
            "<p><ref=\"http://example.com/b\"><b>第二条结果</b></a>",
            "预览二：去重与线程安全由 modern/ 的 ResultCollector 负责。</p>",
            "</body></html>");

    private final List<Engine> engines;
    private final SearchService service;
    private final PageFetcher fetcher;
    private final List<SearchResult> rows = new java.util.ArrayList<>();
    private List<Future<?>> running;

    private javax.swing.DefaultListModel<String> resultModel;
    private javax.swing.DefaultListModel<String> statusModel;
    private JList<String> resultList;
    private JList<String> categoryList;
    private JList<String> searchEngineList;
    private JTextArea preview;
    private JTextArea messages;
    private JLabel totalNum;
    private JButton findNowBu;
    private JButton stopBu;
    private JButton newSearchBu;
    private JTextField containingTf;
    private JComboBox<String> pagesCombo;

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("SwingReplica needs a display; this JVM is headless.");
            return;
        }
        SwingUtilities.invokeLater(() -> new SwingReplica(args).show());
    }

    private SwingReplica(String[] args) {
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
        JFrame frame = new JFrame("JSearch SR - Swing replica (1:1)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { service.close(); }
        });

        JPanel page = new JPanel(null);
        page.setBackground(PAGE);
        page.setPreferredSize(new Dimension(758, 403));
        frame.setContentPane(page);

        // ===== SEARCH (searchTLa 6,6; searchPa 6,26,350,80) =====
        page.add(tab("搜索", 6, 6, 80, 20, TAB_BLUE));
        JPanel searchPa = panel(6, 26, 350, 80, Color.cyan);
        page.add(searchPa);
        searchPa.add(label("查询内容", 5, 6, 70, 20, null, SwingConstants.RIGHT));
        containingTf = field("", 81, 6, 260, 20);
        searchPa.add(containingTf);
        JLabel keyLogicLa = label("关键词之间以空格分隔，且均为\"与\"的关系", 5, 31, 335, 20,
                null, SwingConstants.CENTER);
        keyLogicLa.setForeground(DARK_TEXT);
        searchPa.add(keyLogicLa);
        findNowBu = button("开始搜索", 6, 56, 105, 20);
        searchPa.add(findNowBu);
        stopBu = button("停止搜索", 121, 56, 105, 20);
        stopBu.setEnabled(false); // idle state, JSApplet.java:676-679
        searchPa.add(stopBu);
        newSearchBu = button("新建搜索", 236, 56, 105, 20);
        searchPa.add(newSearchBu);

        // ===== RESULTS (resultTLa 6,116; headers 6/272,136; resultLi 6,156,525,240) =====
        page.add(tab("结果", 6, 116, 80, 20, TAB_BLUE));
        page.add(label("主题", 6, 136, 265, 20, Color.cyan, SwingConstants.LEFT));
        page.add(label("位置", 272, 136, 259, 20, Color.cyan, SwingConstants.LEFT));
        resultModel = new javax.swing.DefaultListModel<>();
        resultList = list(COMMON_FONT);
        resultList.setModel(resultModel);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.addListSelectionListener(e -> {
            int i = resultList.getSelectedIndex();
            if (i >= 0 && i < rows.size()) preview.setText(rows.get(i).preview());
        });
        page.add(scroller(resultList, 6, 156, 525, 240));
        JLabel totalLa = label("总计:", 451, 116, 40, 20, null, SwingConstants.LEFT);
        totalLa.setForeground(DARK_TEXT);
        page.add(totalLa);
        totalNum = label("0", 491, 116, 40, 20, null, SwingConstants.LEFT);
        totalNum.setForeground(DARK_TEXT);
        page.add(totalNum);

        // ===== PREVIEW/STATUS/MESSAGES (three stacked areas, pills on top) =====
        preview = area(361, 26, 390, 80);
        page.add(preview);
        statusModel = new javax.swing.DefaultListModel<>();
        JList<String> statusList = list(LITTLE_FONT);
        page.add(scroller(statusList, 361, 26, 390, 80));
        messages = area(361, 26, 390, 80);
        messages.setFont(LITTLE_FONT);
        page.add(messages);
        // The original's switchPSM(): exactly one area visible at a time, and
        // the active pill turns blue while the others stay green
        // (JSApplet.java:781-789) - reproduced.
        JLabel[] psmTabs = {
                tab(">>预览", 361, 6, 81, 20, TAB_GREEN),
                tab(">>状态", 449, 6, 80, 20, TAB_GREEN),
                tab(">>消息", 536, 6, 80, 20, TAB_GREEN)
        };
        Runnable[] psmSwitch = {
                () -> { preview.setVisible(true); statusList.setVisible(false);
                        messages.setVisible(false); recolorPsm(psmTabs, 0); },
                () -> { preview.setVisible(false); statusList.setVisible(true);
                        messages.setVisible(false); recolorPsm(psmTabs, 1); },
                () -> { preview.setVisible(false); statusList.setVisible(false);
                        messages.setVisible(true); recolorPsm(psmTabs, 2); }
        };
        for (int i = 0; i < psmTabs.length; i++) {
            JLabel psmTab = psmTabs[i];
            int index = i;
            psmTab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            psmTab.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { psmSwitch[index].run(); }
            });
            page.add(psmTab);
        }
        psmSwitch[0].run(); // preview visible first, like the original

        // ===== 引擎/选项/关于 (radios at 536/611/686,116; CardLayout panel) =====
        JPanel eoaCards = new JPanel(null);
        eoaCards.setBackground(Color.cyan);
        eoaCards.setBounds(536, 136, 215, 260);
        CardLayout eoaLayout = new CardLayout();
        eoaCards.setLayout(eoaLayout);
        page.add(eoaCards);

        JPanel enginesPa = new JPanel(null);
        enginesPa.setBackground(Color.cyan);
        eoaCards.add(enginesPa, "engines");
        // The authentic quirk: these two tabs wear the page color
        // (docs/ANALYSIS.md, "two folder-tab labels wear the background
        // color") - reproduced on purpose. Swap PAGE for TAB_BLUE to see
        // what the author probably meant.
        enginesPa.add(label("类别", 8, 4, 60, 15, PAGE, SwingConstants.CENTER));
        categoryList = list(COMMON_FONT);
        enginesPa.add(scroller(categoryList, 8, 19, 200, 85));
        enginesPa.add(label("搜索引擎", 8, 108, 100, 15, PAGE, SwingConstants.CENTER));
        searchEngineList = list(COMMON_FONT);
        searchEngineList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        enginesPa.add(scroller(searchEngineList, 8, 123, 200, 132));

        JPanel optionsPa = new JPanel(null);
        optionsPa.setBackground(Color.cyan);
        eoaCards.add(optionsPa, "options");
        optionsPa.add(label("最大显示数", 8, 26, 140, 20, null, SwingConstants.LEFT));
        optionsPa.add(combo(new String[] {"1","2","3","4","5","6","7","8","9","10",
                "11","12","13","14","15","16"}, "4", 158, 26, 50, 20));
        optionsPa.add(label("搜索页数", 8, 46, 140, 20, null, SwingConstants.LEFT));
        optionsPa.add(pagesCombo = combo(new String[] {"1","2","3","4","5","6","7","8","9","10"},
                "1", 158, 46, 50, 20));
        optionsPa.add(label("最快连接数", 8, 66, 140, 20, null, SwingConstants.LEFT));
        optionsPa.add(combo(new String[] {"1","2","3","4","5","6","7","8","9","10"},
                "10", 158, 66, 50, 20));
        optionsPa.add(label("验证URL", 8, 86, 140, 20, null, SwingConstants.LEFT));
        optionsPa.add(combo(new String[] {"Yes","No"}, "No", 158, 86, 50, 20));
        optionsPa.add(label("语言", 8, 146, 80, 20, null, SwingConstants.LEFT));
        optionsPa.add(combo(new String[] {"English","Chinese"}, "Chinese", 118, 146, 90, 20));
        optionsPa.add(label("系统浏览器", 8, 166, 110, 20, null, SwingConstants.LEFT));
        optionsPa.add(combo(new String[] {"IE (Windows)","NS (Windows)","NS (Linux)",
                "Other browsers"}, "Other browsers", 118, 166, 90, 20));
        optionsPa.add(label("浏览器路径", 8, 186, 110, 20, null, SwingConstants.LEFT));
        optionsPa.add(field("", 118, 186, 90, 20));

        JPanel aboutPa = new JPanel(null);
        aboutPa.setBackground(Color.cyan);
        eoaCards.add(aboutPa, "about");
        aboutPa.add(label("COPYING", 8, 6, 80, 15, PAGE, SwingConstants.CENTER));
        JTextArea copyingTe = area(8, 21, 200, 120);
        copyingTe.setFont(LITTLE_FONT);
        copyingTe.setText("JSearch version 2.0.0.0 [huntlin@public.xm.fj.cn], "
                + "Copyright (C) 1999-2002 Hunt Lin\nJSearch comes with ABSOLUTELY NO "
                + "WARRANTY; for details see COPYING.\n(This replica feeds the text "
                + "inline - the original fetched COPYING.TXT when _readTxt=Yes.)");
        aboutPa.add(copyingTe);
        aboutPa.add(label("CREDITS", 8, 146, 80, 15, PAGE, SwingConstants.CENTER));
        JTextArea creditsTe = area(8, 161, 200, 95);
        creditsTe.setFont(LITTLE_FONT);
        creditsTe.setText("CREDITS.TXT is fetched from currUrl only when _readTxt=Yes.");
        aboutPa.add(creditsTe);

        // The three radios (2002: CheckboxGroup, 引擎 selected first).
        JRadioButton enginesCb = radio("引擎", 536, 116, 74, 20, TAB_BLUE, true);
        JRadioButton optionsCb = radio("选项", 611, 116, 74, 20, TAB_GREEN, false);
        JRadioButton aboutCb = radio("关于", 686, 116, 65, 20, TAB_GREEN, false);
        java.util.function.Consumer<String> showCard = name -> {
            eoaLayout.show(eoaCards, name);
            boolean e = "engines".equals(name);
            boolean o = "options".equals(name);
            boolean a = "about".equals(name);
            enginesCb.setSelected(e);
            optionsCb.setSelected(o);
            aboutCb.setSelected(a);
            // The original's switchEOA(): the selected radio wears blue, the
            // others green (JSApplet.java:792-796) - reproduced.
            enginesCb.setBackground(e ? TAB_BLUE : TAB_GREEN);
            optionsCb.setBackground(o ? TAB_BLUE : TAB_GREEN);
            aboutCb.setBackground(a ? TAB_BLUE : TAB_GREEN);
        };
        enginesCb.addActionListener(e -> showCard.accept("engines"));
        optionsCb.addActionListener(e -> showCard.accept("options"));
        aboutCb.addActionListener(e -> showCard.accept("about"));
        page.add(enginesCb);
        page.add(optionsCb);
        page.add(aboutCb);
        showCard.accept("engines");

        // ===== behavior: categories, search, stop, new =====
        java.util.Vector<String> categories = new java.util.Vector<>();
        for (Engine engine : engines) {
            if (!categories.contains(engine.category())) categories.add(engine.category());
        }
        javax.swing.DefaultListModel<String> categoryModel =
                new javax.swing.DefaultListModel<>();
        for (String category : categories) categoryModel.addElement(category);
        categoryList.setModel(categoryModel);
        // The applet's own default, reproduced: categoryLi.select(1) - the
        // second category, not the first (JSApplet.java:595). On the shipped
        // table that is English. (The shipped runner then overrides this to
        // Chinese for deterministic demos, JSearchRunner.java:156-158 - that
        // override belongs to the runner, not to the applet.)
        if (categories.size() > 1) categoryList.setSelectedIndex(1);
        rebuildEngineList();

        categoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) rebuildEngineList();
        });

        findNowBu.addActionListener(e -> search(containingTf.getText().trim()));
        containingTf.addActionListener(e -> search(containingTf.getText().trim()));
        stopBu.addActionListener(e -> {
            if (running != null) service.cancelAll(running);
            statusModel.addElement("Stop requested ...");
        });
        newSearchBu.addActionListener(e -> {
            rows.clear();
            resultModel.clear();
            totalNum.setText("0");
            preview.setText("");
            stopBu.setEnabled(false); // JSApplet.java:889
            statusModel.addElement("New search.");
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        messages.append("JSearch SR - Swing replica, 1:1 layout from controlSetting()\n");
        messages.append("engines: " + engines.size() + ", categories: " + categories + "\n");
    }

    // ===== behavior =====

    /** The 2002 flow, on one SwingWorker over the modern/ core. */
    private void search(String text) {
        String query = text.isEmpty() ? "java" : text;
        java.util.Set<String> chosen =
                new java.util.HashSet<>(searchEngineList.getSelectedValuesList());
        List<Engine> selected = new java.util.ArrayList<>();
        for (Engine engine : engines) {
            if (chosen.contains(engine.name())) selected.add(engine);
        }
        if (selected.isEmpty()) selected.addAll(engines);
        int levelCount = Integer.parseInt((String) pagesCombo.getSelectedItem());

        rows.clear();
        resultModel.clear();
        totalNum.setText("0");
        // The original's busy state, verbatim (JSApplet.java:670-673).
        containingTf.setEnabled(false);
        findNowBu.setEnabled(false);
        stopBu.setEnabled(true);
        newSearchBu.setEnabled(false);
        statusModel.addElement("Search '" + query + "' across " + selected.size()
                + " engine(s), " + levelCount + " page(s) each");
        messages.append("search started: '" + query + "', " + selected.size()
                + " engine(s), " + levelCount + " page(s) each\n");

        SwingWorker<Void, SearchResult> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                ResultCollector collector = new ResultCollector();
                // publish() is protected: bridge from the scraping threads
                // onto the event thread from inside the worker, exactly as
                // SwingDemo does.
                collector.onResult(this::publish);
                collector.onError(cause -> SwingUtilities.invokeLater(() ->
                        messages.append("error: " + cause + "\n")));
                running = service.search(selected, query, levelCount, collector);
                service.awaitAll(running, collector);
                return null;
            }

            @Override protected void process(List<SearchResult> chunks) {
                for (SearchResult result : chunks) {
                    rows.add(result);
                    resultModel.addElement(format(result));
                }
                totalNum.setText(String.valueOf(rows.size()));
            }

            @Override protected void done() {
                // The original's idle state, verbatim (JSApplet.java:676-679).
                containingTf.setEnabled(true);
                findNowBu.setEnabled(true);
                stopBu.setEnabled(false);
                newSearchBu.setEnabled(true);
                statusModel.addElement("Search complete. Results: " + rows.size());
                messages.append("search complete - " + rows.size()
                        + " unique result(s) (duplicates dropped by the collector)\n");
                if (!rows.isEmpty()) resultList.setSelectedIndex(0);
            }
        };
        worker.execute();
    }

    /** 主题/位置 columns squeezed into one AWT-List line, like the original. */
    private static String format(SearchResult result) {
        String title = result.title();
        if (title.length() > 26) title = title.substring(0, 26);
        return String.format("%-26s %s", title, result.url());
    }

    /** The engine list always mirrors the selected category, like the original. */
    private void rebuildEngineList() {
        String category = categoryList.getSelectedValue();
        javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
        for (Engine engine : engines) {
            if (category == null || category.equals(engine.category())) {
                model.addElement(engine.name());
            }
        }
        searchEngineList.setModel(model);
        // The original's categorySelect() preselects the first smcCh engines
        // (JSApplet.java:693); under the shipped runner that narrows to the
        // demo engine alone, which is what the runner screenshot shows and
        // what the replica reproduces.
        if (!model.isEmpty()) searchEngineList.setSelectedIndex(0);
    }

    // ===== factories: the 2002 controlSetting(), one helper per AWT shape =====

    private static void recolorPsm(JLabel[] tabs, int active) {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setBackground(i == active ? TAB_BLUE : TAB_GREEN);
        }
    }

    private static JLabel label(String text, int x, int y, int w, int h,
            Color bg, int alignment) {
        JLabel l = new JLabel(text, alignment);
        l.setFont(COMMON_FONT);
        if (bg != null) {
            l.setOpaque(true);
            l.setBackground(bg);
        }
        l.setBounds(x, y, w, h);
        return l;
    }

    private static JLabel tab(String text, int x, int y, int w, int h, Color bg) {
        JLabel l = label(text, x, y, w, h, bg, SwingConstants.CENTER);
        l.setFont(TAB_FONT);
        l.setForeground(Color.yellow);
        return l;
    }

    private static JPanel panel(int x, int y, int w, int h, Color bg) {
        JPanel p = new JPanel(null);
        p.setBackground(bg);
        p.setBounds(x, y, w, h);
        return p;
    }

    private static JTextField field(String text, int x, int y, int w, int h) {
        JTextField f = new JTextField(text);
        f.setBackground(Color.white);
        f.setBounds(x, y, w, h);
        return f;
    }

    private static JTextArea area(int x, int y, int w, int h) {
        JTextArea a = new JTextArea();
        a.setBackground(Color.white);
        a.setEditable(false);
        a.setBounds(x, y, w, h);
        return a;
    }

    private static JList<String> list(Font font) {
        JList<String> l = new JList<>(new javax.swing.DefaultListModel<>());
        l.setFont(font);
        l.setBackground(Color.white);
        return l;
    }

    private static JScrollPane scroller(java.awt.Component c, int x, int y, int w, int h) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBounds(x, y, w, h);
        return sp;
    }

    private static JComboBox<String> combo(String[] items, String selected,
            int x, int y, int w, int h) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setSelectedItem(selected);
        c.setBounds(x, y, w, h);
        return c;
    }

    private static JButton button(String text, int x, int y, int w, int h) {
        JButton b = new JButton(text);
        b.setBackground(PAGE);
        b.setBounds(x, y, w, h);
        return b;
    }

    private static JRadioButton radio(String text, int x, int y, int w, int h,
            Color bg, boolean selected) {
        // JRadioButton, not JCheckBox: the 2002 control was a CheckboxGroup
        // Checkbox, i.e. a round indicator. Exclusion is handled by showCard.
        JRadioButton cb = new JRadioButton(text, selected);
        cb.setFont(TAB_FONT);
        cb.setForeground(Color.yellow);
        cb.setBackground(bg);
        cb.setOpaque(true);
        cb.setFocusPainted(false);
        cb.setBounds(x, y, w, h);
        return cb;
    }
}
