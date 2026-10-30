import java.applet.Applet;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.applet.AudioClip;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * Local runner for the original JSApplet (JSearch 2.0.0.0, March 2002).
 *
 * Plays the part Internet Explorer + the Java Plug-in used to play: provides
 * the AppletStub/AppletContext and the applet parameters the code reads, most
 * importantly currUrl, which points at this directory so JSENGINES.TXT loads.
 * Everything the applet itself does is untouched original code from Sources/.
 *
 * The engine table here is the shipped Releases/JSEngines.txt converted to
 * UTF-8 (the code reads with the platform charset) with two records appended:
 * LocalDemo, served by DemoServer.py on 127.0.0.1:8901 so a live search is
 * possible at all, and SearXNG, served by SearxngBridge.py on 127.0.0.1:8902
 * (start it with ./run-searxng.sh) so the applet can search the live web.
 * See README.md.
 *
 * Usage: java -cp classes JSearchRunner [file:/...runner/] [--go] [--snap=FILE]
 *                            [--engine=NAME[,NAME...]] [--levels=N]
 *   --go         auto-run one search for "JSearch" a moment after startup
 *   --snap=FILE  save a PNG of the applet 6 seconds after startup
 *   --engine=    which engines to select in the Chinese category, matched by
 *                prefix and comma-separated (default: LocalDemo)
 *   --levels=N   how many pages the applet's own level loop fetches per engine
 * With no file: argument the base URL is derived from this class's location.
 */
public class JSearchRunner {

    public static void main(String[] args) throws Exception {
        String base = null;
        boolean autoSearch = false;
        String snapPath = null;
        String engineSpec = "LocalDemo";
        int levels = 0;
        for (String a : args) {
            if ("--go".equals(a)) autoSearch = true;
            else if (a.startsWith("--snap=")) snapPath = a.substring(7);
            else if (a.startsWith("--engine=")) engineSpec = a.substring(9);
            else if (a.startsWith("--levels=")) levels = Integer.parseInt(a.substring(9));
            else if (a.startsWith("file:")) base = a;
        }
        if (base == null) {
            // classes/ -> runner/, with the trailing slash toURI() provides.
            File classesDir = new File(JSearchRunner.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            base = classesDir.getParentFile().toURI().toString();
        }
        final String baseUrl = base;
        final boolean go = autoSearch;
        final String snap = snapPath;
        final String engines = engineSpec;
        final int levelCount = levels;

        final Frame frame = new Frame("JSearch 2.0.0.0 - JSApplet (local runner)");
        final JSApplet applet = new JSApplet();
        final URL baseURL = new URL(baseUrl);

        AppletContext ctx = new AppletContext() {
            public Applet getApplet(String name) { return null; }
            public Enumeration<Applet> getApplets() { return new java.util.Vector<Applet>().elements(); }
            public AudioClip getAudioClip(URL url) { return null; }
            public java.awt.Image getImage(URL url) { return java.awt.Toolkit.getDefaultToolkit().getImage(url); }
            public void showDocument(URL url) { open(url); }
            public void showDocument(URL url, String target) { open(url); }
            public void showStatus(String status) { frame.setTitle(status); }
            public java.io.InputStream getStream(String key) { return null; }
            public java.util.Iterator<String> getStreamKeys() { return new java.util.Vector<String>().elements().asIterator(); }
            public void setStream(String key, java.io.InputStream stream) { }
            void open(URL url) {
                try {
                    new ProcessBuilder((browserCommand() + " " + url).split("\\s+")).start();
                }
                catch (Exception ex) { System.out.println("[runner] browser open failed: " + url); }
            }
        };

        applet.setStub(new AppletStub() {
            public boolean isActive() { return true; }
            public URL getDocumentBase() { return baseURL; }
            public URL getCodeBase() { return baseURL; }
            public String getParameter(String name) {
                if ("currUrl".equals(name)) return baseUrl;
                if ("_readTxt".equals(name)) return "No";
                if ("smcCh".equals(name)) return "4";
                if ("smlCh".equals(name)) return "1";
                if ("smsCh".equals(name)) return "10";
                if ("valurlCh".equals(name)) return "No";
                if ("languageCh".equals(name)) return "Chinese";
                if ("webBrowCh".equals(name)) return "Other browsers";
                if ("webBrowPTf".equals(name)) return browserCommand();
                return null;
            }
            public AppletContext getAppletContext() { return ctx; }
            public void appletResize(int w, int h) { frame.setSize(w, h); }
        });

        frame.setLayout(new BorderLayout());
        frame.add(applet, BorderLayout.CENTER);
        frame.setSize(790, 450);
        frame.setMenuBar(buildMenuBar(applet));
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { System.exit(0); }
        });

        applet.init();
        keepAuthoredGeometry(applet);

        EventQueue.invokeLater(new Runnable() {
            public void run() {
                frame.setVisible(true);
                applet.start();

                // Deterministic setup, the same a user could do by hand:
                // pick the Chinese category, then exactly the engines --engine
                // names (default: the demo engine, the only one that answers
                // out of the box). The applet's own default selects the first
                // smcCh engines of the category, which here would spend the run
                // waiting on Baidu and Google.
                for (int i = 0; i < applet.categoryLi.getItemCount(); i++) {
                    if ("Chinese".equals(applet.categoryLi.getItem(i))) applet.categoryLi.select(i);
                }
                applet.categorySelect();
                selectEngines(applet, engines);
                if (levelCount > 0) selectLevels(applet, levelCount);
                applet.containingTf.requestFocus();

                System.out.println("[runner] engines in table = " + applet.engDataTable.size()
                        + (isLegacyGeneration()
                           ? " (a 2000-generation engine file — the shipped one, unchanged)"
                           : " (the file defines 6 and all 6 load: record 1's URL key has a"
                             + " trailing space, which accidentally keeps the URL-keyed table"
                             + " from colliding)"));
                for (int i = 0; i < applet.searchEnginesLi.getItemCount(); i++) {
                    System.out.println("[runner] engine[" + i + "] = " + applet.searchEnginesLi.getItem(i)
                            + (applet.searchEnginesLi.isIndexSelected(i) ? "  [selected]" : ""));
                }

                if (go) {
                    new Thread(new Runnable() {
                        public void run() {
                            try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                            EventQueue.invokeLater(new Runnable() {
                                public void run() {
                                    applet.containingTf.setText("JSearch");
                                    applet.startSearch();
                                    System.out.println("[runner] search started for 'JSearch'");
                                }
                            });
                        }
                    }).start();
                }

                watchForResults(applet);

                if (snap != null) {
                    new Thread(new Runnable() {
                        public void run() {
                            try { Thread.sleep(6000); } catch (InterruptedException e) { return; }
                            EventQueue.invokeLater(new Runnable() {
                                public void run() {
                                    try {
                                        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                                                applet.getWidth(), applet.getHeight(),
                                                java.awt.image.BufferedImage.TYPE_INT_RGB);
                                        applet.printAll(img.getGraphics());
                                        javax.imageio.ImageIO.write(img, "png", new File(snap));
                                        System.out.println("[runner] snapshot saved to " + snap);
                                    } catch (Exception ex) {
                                        System.out.println("[runner] snapshot failed: " + ex);
                                    }
                                }
                            });
                        }
                    }).start();
                }
            }
        });
    }

    /** True when the loaded JSApplet predates the 2002 completion signal:
     * the 2000/2001 generations have no static _stop for the watcher to wait
     * on (and no switchPSM to switch tabs with), so completion falls back to
     * the result count holding still, and preview switching degrades to a
     * no-op. Probed once, lazily - the answer depends on which generation's
     * JSApplet the classpath put first. */
    static Boolean legacyGeneration;

    static boolean isLegacyGeneration() {
        if (legacyGeneration == null) {
            try {
                JSApplet.class.getDeclaredField("_stop");
                legacyGeneration = Boolean.FALSE;
            } catch (NoSuchFieldException noStopField) {
                legacyGeneration = Boolean.TRUE;
            }
        }
        return legacyGeneration.booleanValue();
    }

    /** Prints the outcome and shows the first result's preview when it lands. */
    static void watchForResults(final JSApplet applet) {
        new Thread(new Runnable() {
            public void run() {
                boolean stable = false;
                int last = -1;
                int stablePolls = 0;
                // The 2002 search is done inside 30s and says so via _stop.
                // The 2000/2001 generations say nothing, and their four-page
                // level loop over a live engine runs longer than that, so the
                // watcher gets 90s and relies on the 3-second stability rule.
                int polls = isLegacyGeneration() ? 300 : 100;
                for (int i = 0; i < polls; i++) {
                    try { Thread.sleep(300); } catch (InterruptedException e) { return; }
                    // The 2002 code sets _stop when the last engine's thread
                    // exits, and every showResult() has already run by then,
                    // so the count reported below is final. A count that
                    // merely stopped changing for 600ms is not: the scraper
                    // pauses between blocks and between levels, so the
                    // earlier check reported mid-scrape — 2 of 5 results,
                    // with the level loop still to come. The 2000/2001
                    // generations have no _stop; there the count holding
                    // still is the only completion signal there is, and it
                    // has to hold for 3 seconds — the v1.2.3 applet's level
                    // loop pauses for seconds between pages while DeepSeek
                    // is fetched, and 600ms of quiet is exactly what one of
                    // those pauses looks like.
                    if (!isLegacyGeneration() && !JSApplet._stop) continue;
                    int now = JSApplet.resultLi.getItemCount();
                    if (now > 0 && now == last) stablePolls++; else stablePolls = 0;
                    if (now > 0 && stablePolls >= (isLegacyGeneration() ? 10 : 0)) {
                        report(applet, now);
                        return;
                    }
                    last = now;
                }
                System.out.println("[runner] no results after 30s");
            }
        }).start();
    }

    /**
     * Reports the outcome. Each result line is the applet's own list item, so
     * it carries the engine the result came from — the only way to see the
     * original's fan-out over several engines, and the only way to tell which
     * engine a shared URL was credited to.
     */
    static void report(final JSApplet applet, final int count) {
        System.out.println("[runner] results in list = " + count
                + " (counted after the applet's own dedup, by URL, across every engine it searched)");
        for (int i = 0; i < count; i++) {
            System.out.println("[runner] result[" + i + "] = " + JSApplet.resultLi.getItem(i));
        }
        // The applet's own per-engine accounting: one status line per search
        // thread, showing what that engine delivered.
        for (int i = 0; i < JSApplet.statusLi.getItemCount(); i++) {
            System.out.println("[runner] status[" + i + "] = " + JSApplet.statusLi.getItem(i));
        }
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                JSApplet.resultLi.select(0);
                String url0 = (String) JSApplet.resultIndex.elementAt(0);
                ResultsDetails d = (ResultsDetails) JSApplet.resultTable.get(url0);
                if (d != null) {
                    showPreview(applet, d.preview);
                    System.out.println("[runner] preview of first result: " + d.preview);
                }
            }
        });
    }

    /** setText first, then best-effort tab switch: the 2002 applet hides
     * >>预览 behind the tab strip and needs switchPSM to reveal it, but the
     * 2000/2001 layouts have no such method - NoSuchMethodError there is
     * harmless (those screens show everything at once), and it must not eat
     * the setText that precedes it. */
    static void showPreview(final JSApplet applet, final String text) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                applet.previewTe.setText(text);
                try { applet.switchPSM(true, false, false); }
                catch (NoSuchMethodError legacyLayout) { }
            }
        });
    }

    /**
     * The host chrome IE and the Plug-in would have drawn around the applet:
     * a plain AWT menu bar. 文件/退出 closes; 工具 opens the selected result in
     * the system browser or Fetches its full text through the bridge into
     * >>预览. The applet itself stays untouched.
     */
    static MenuBar buildMenuBar(final JSApplet applet) {
        MenuBar bar = new MenuBar();
        Menu file = new Menu("文件");
        MenuItem exit = new MenuItem("退出");
        exit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { System.exit(0); }
        });
        file.add(exit);

        Menu tools = new Menu("工具");
        MenuItem open = new MenuItem("用系统浏览器打开选中结果");
        open.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { openSelected(applet); }
        });
        tools.add(open);
        MenuItem fetch = new MenuItem("抓取选中结果全文 (Fetch)");
        fetch.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { fetchSelected(applet); }
        });
        tools.add(fetch);
        MenuItem ask = new MenuItem("AI 问答（搜索并总结查询内容）");
        ask.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { askAI(applet); }
        });
        tools.add(ask);

        bar.add(file);
        bar.add(tools);
        return bar;
    }

    /** The browser command the applet pastes into its 选项 field and executes
     * with Runtime.exec: tokens split on spaces, the URL appended last. On
     * Windows rundll32 avoids cmd, whose & would eat every query string. */
    static String browserCommand() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        return windows ? "rundll32 url.dll,FileProtocolHandler" : "xdg-open";
    }

    /** Best-effort switch to the >>消息 tab; harmless no-op on the 2000/2001
     * layouts (see showPreview). */
    static void showMessages(final JSApplet applet) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try { applet.switchPSM(false, false, true); }
                catch (NoSuchMethodError legacyLayout) { }
            }
        });
    }

    /** The selected result's URL, or null after hinting in >>消息. */
    static String selectedResultUrl(final JSApplet applet) {
        int index = JSApplet.resultLi.getSelectedIndex();
        if (index < 0) {
            showMessages(applet);
            say(applet, "先在结果列表里选中一条结果。");
            return null;
        }
        return (String) JSApplet.resultIndex.elementAt(index);
    }

    static void openSelected(final JSApplet applet) {
        String url = selectedResultUrl(applet);
        if (url == null) return;
        try {
            new ProcessBuilder((browserCommand() + " " + url).split("\\s+")).start();
            say(applet, "已在系统浏览器打开: " + url);
        } catch (Exception ex) {
            say(applet, "打开失败: " + ex);
        }
    }

    /** Fetches the selected result's full text through the bridge's Fetch
     * route into >>预览. Off the event thread; the bridge bills per
     * successful fetch, so this fires only when clicked. */
    static void fetchSelected(final JSApplet applet) {
        final String url = selectedResultUrl(applet);
        if (url == null) return;
        say(applet, "正在抓取全文: " + url);
        new Thread(new Runnable() {
            public void run() {
                try {
                    final String text = fetchText(url);
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            showPreview(applet, text);
                            say(applet, "全文已抓取（" + text.length() + " 字符），见 >>预览。");
                        }
                    });
                } catch (final Exception ex) {
                    EventQueue.invokeLater(new Runnable() {
                        public void run() { say(applet, "抓取失败: " + ex.getMessage()); }
                    });
                }
            }
        }).start();
    }

    /** AI 问答 — the FreeAskInternet idea in one menu item: the query box's
     * text goes to the bridge's /deepseek/ask route, which searches
     * DeepSeek's index, hands the pages to deepseek-flash and returns the
     * synthesized answer. Lands in >>预览; progress in >>消息. */
    static void askAI(final JSApplet applet) {
        final String query = applet.containingTf.getText().trim();
        if (query.isEmpty()) {
            showMessages(applet);
            say(applet, "先在查询内容里输入问题，再用本菜单。");
            return;
        }
        say(applet, "正在让 DeepSeek 搜索并总结（可能需要十几秒）…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    final String text = fetchPath("/deepseek/ask?q="
                            + URLEncoder.encode(query, "UTF-8"));
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            showPreview(applet, text);
                            say(applet, "AI 回答已生成（" + text.length() + " 字符），见 >>预览。");
                        }
                    });
                } catch (final Exception ex) {
                    EventQueue.invokeLater(new Runnable() {
                        public void run() { say(applet, "AI 问答失败: " + ex.getMessage()); }
                    });
                }
            }
        }).start();
    }

    static String fetchPath(String path) throws Exception {
        String base = System.getenv("JSEARCH_BRIDGE");
        if (base == null || base.isEmpty()) base = "http://127.0.0.1:8902";
        BufferedReader in = new BufferedReader(new InputStreamReader(
                new URL(base + path).openStream(), StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.append(buf, 0, n);
        in.close();
        return out.toString();
    }

    static String fetchText(String url) throws Exception {
        return fetchPath("/deepseek/fetch?url=" + URLEncoder.encode(url, "UTF-8"));
    }

    /** Feedback lands in the applet's own >>消息 pane — the same place the
     * 2002 code reports engine errors. Appends only; the caller picks tabs. */
    static void say(final JSApplet applet, String text) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                applet.messageTe.append("[runner] " + text + "\n");
            }
        });
    }

    /**
     * Selects exactly the named engines, deselecting the rest. A name matches
     * the list item it prefixes, so "SearXNG" finds "SearXNG / live web
     * aggregation"; names are comma-separated. Exits instead of searching when
     * a name matches nothing: a search for an engine that does not exist would
     * otherwise fail inside the applet with its own generic message.
     */
    static void selectEngines(final JSApplet applet, String spec) {
        java.util.List<String> wanted = new java.util.ArrayList<String>();
        for (String part : spec.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) wanted.add(name);
        }
        for (int i = 0; i < applet.searchEnginesLi.getItemCount(); i++) {
            applet.searchEnginesLi.deselect(i);
        }
        for (String name : wanted) {
            boolean found = false;
            for (int i = 0; i < applet.searchEnginesLi.getItemCount(); i++) {
                if (applet.searchEnginesLi.getItem(i).startsWith(name)) {
                    applet.searchEnginesLi.select(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println("[runner] no engine in the Chinese category starts with '" + name + "'. The list has:");
                for (int i = 0; i < applet.searchEnginesLi.getItemCount(); i++) {
                    System.out.println("[runner]   " + applet.searchEnginesLi.getItem(i));
                }
                System.exit(2);
            }
        }
    }

    /**
     * Selects the level count in the applet's own Choice. The Choice holds
     * 1..10 as text, so the index is one less than the level.
     */
    static void selectLevels(final JSApplet applet, int levels) {
        int count = applet.smlCh.getItemCount();
        if (levels < 1 || levels > count) {
            System.out.println("[runner] --levels must be 1.." + count + ", not " + levels);
            System.exit(2);
        }
        applet.smlCh.select(levels - 1);
        System.out.println("[runner] levels = " + applet.smlCh.getSelectedItem()
                + " (the applet's own level loop will fetch that many pages per engine)");
    }

    /**
     * The 2002 VMs laid out this applet from the setBounds() calls alone.
     * Modern AWT instead runs the BorderLayout assigned at the end of
     * controlSetting() on first validate, which would stack every component
     * full-size. Recording the authored bounds and dropping the layout
     * managers (except the intentional CardLayout on the tab panel) restores
     * the geometry the code was written against. No-op if AWT agrees.
     */
    static void keepAuthoredGeometry(Container root) {
        record(root);
    }

    static void record(Container c) {
        java.util.List<Component> kids = new java.util.ArrayList<Component>();
        for (Component k : c.getComponents()) kids.add(k);
        java.util.List<Rectangle> bounds = new java.util.ArrayList<Rectangle>();
        for (Component k : kids) bounds.add(k.getBounds());

        if (!(c.getLayout() instanceof CardLayout)) c.setLayout(null);

        int i = 0;
        for (Component k : kids) {
            k.setBounds(bounds.get(i++));
            if (k instanceof Container) record((Container) k);
        }
    }
}
