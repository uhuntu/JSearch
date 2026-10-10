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
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URL;
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
                try { new ProcessBuilder("xdg-open", url.toString()).start(); }
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
                if ("webBrowPTf".equals(name)) return "xdg-open";
                return null;
            }
            public AppletContext getAppletContext() { return ctx; }
            public void appletResize(int w, int h) { frame.setSize(w, h); }
        });

        frame.setLayout(new BorderLayout());
        frame.add(applet, BorderLayout.CENTER);
        frame.setSize(790, 450);
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
                        + " (the file defines 5 and all 5 load: record 1's URL key has a"
                        + " trailing space, which accidentally keeps the URL-keyed table from colliding)");
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

    /** Prints the outcome and shows the first result's preview when it lands. */
    static void watchForResults(final JSApplet applet) {
        new Thread(new Runnable() {
            public void run() {
                // Wait for the applet's own completion signal rather than for
                // the result count to hold still. SearchThread sets _stop when
                // the last engine's thread exits, and every showResult() has
                // already run by then, so the count reported below is final.
                // A count that merely stopped changing for 600ms is not: the
                // scraper pauses between blocks and between levels, so the
                // earlier check reported mid-scrape — 2 of 5 results, with the
                // level loop still to come.
                for (int i = 0; i < 100; i++) {
                    try { Thread.sleep(300); } catch (InterruptedException e) { return; }
                    if (!JSApplet._stop) continue;
                    int now = JSApplet.resultLi.getItemCount();
                    if (now > 0) {
                        report(applet, now);
                    } else {
                        // The search finished and found nothing. Say so with
                        // the count, so a failing engine is visible as data
                        // rather than as a missing line.
                        System.out.println("[runner] results in list = 0"
                                + " (the search finished; no engine returned a block the scraper could read)");
                        for (int j = 0; j < JSApplet.statusLi.getItemCount(); j++) {
                            System.out.println("[runner] status[" + j + "] = " + JSApplet.statusLi.getItem(j));
                        }
                    }
                    return;
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
                    applet.switchPSM(true, false, false);
                    applet.previewTe.setText(d.preview);
                    System.out.println("[runner] preview of first result: " + d.preview);
                }
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
