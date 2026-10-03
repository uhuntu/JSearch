# Glossary

Terms used in the JSearch project.

## A

**Applet**
A small Java program that runs in a web browser. Removed from browsers 2015-2021 and from JDK in JDK 11.

**Archive**
A preserved collection of historical software. This project is an archive, not a revival.

**AWT (Abstract Window Toolkit)**
Java's original UI framework. The original JSearch uses AWT components (List, Label, TextField).

## B

**Block Marker**
Strings that delimit result blocks in HTML. E.g., `<p><` and `k - ` for Google.

**BlockScraper**
Interface in modern design for extracting results from pages.

## C

**CAB (Cabinet)**
Microsoft's archive format. `JSearch.cab` contained the applet classes.

**Check-then-act**
A concurrency pattern where you check a condition then act on it. Must be atomic to avoid races.

**CJK**
Chinese, Japanese, Korean. JSearch supports Chinese and English.

**Concurrency**
Multiple threads executing simultaneously. The original had race conditions; modern design fixes them.

## D

**Deadlock**
When threads wait for each other's locks forever. The original's 5 nested synchronized blocks risked this.

**Dedup**
Short for deduplication. Removing duplicate results by URL.

**Design Defect**
A flaw in the software architecture. The URL-as-key bug is a design defect.

## E

**Engine**
A search engine (Google, Baidu, etc.). Identified by (name, category) in modern design.

**ExecutorService**
Java's thread pool API. Used in modern design instead of manual Thread creation.

## F

**Fixture**
Test data. The archive's HTML pages serve as fixtures.

**Future**
Java's handle to an async computation. `Future.cancel(true)` interrupts blocked threads.

**Finalize**
Java method called before garbage collection. The original had 20 overrides that forced GC.

## G

**GBK**
Chinese character encoding (GB2312 extended). The original source files are GBK-encoded.

**God Class**
Anti-pattern where one class does everything. JSApplet (906 lines) is a God class.

**GB8567-88**
Chinese national standard for software documentation. JSearch was documented against this.

## H

**Hashtable**
Java's legacy hash map. The original used URL as key, causing collisions.

**HTML Scraping**
Extracting data from HTML by pattern matching. Fragile when HTML changes.

## I

**i18n**
Internationalization. JSearch supports English and Chinese.

**Immutable**
Cannot be changed after creation. SearchResult is immutable in modern design.

## J

**JDK (Java Development Kit)**
Software development kit for Java. Modern design requires JDK 11+.

**JVM (Java Virtual Machine)**
Runtime environment for Java programs.

## L

**Lock**
Mechanism for thread synchronization. Modern design uses single lock; original used 5 nested.

## M

**Mojibake**
Garbled text from encoding mismatch. Happens when GBK is read as UTF-8.

**Monitor**
Java's synchronization mechanism. `synchronized` blocks acquire monitors.

## P

**PageFetcher**
Interface in modern design for retrieving pages.

**Provenance**
Documentation of where files came from and how they were preserved.

## R

**Race Condition**
Bug where behavior depends on thread timing. The original had TOCTOU races.

**Reference Design**
A demonstration of how something should be built. `modern/` is the reference design.

**ResultCollector**
Thread-safe component in modern design that deduplicates and counts results.

## S

**Scraper**
Code that extracts data from web pages. HtmlBlockScraper and JsonApiScraper.

**SearchService**
Orchestrates concurrent searches in modern design.

**Synchronized**
Java keyword for thread-safe code. Original had 5 nested synchronized blocks.

## T

**Thread**
Independent execution path. Original created 1 per engine; modern uses pool.

**TOCTOU**
Time-of-check-to-time-of-use race. Original had this between containsKey() and put().

**Thread Pool**
Reusable set of threads. ExecutorService provides this in modern design.

## U

**URL Template**
Pattern for building search URLs. `^` is replaced with query, `` `0 `` with level.

## V

**Vector**
Java's legacy dynamic array. The original used Vector for result URLs.

**Visual J++**
Microsoft's Java IDE (1990s). Used to build the original JSearch.

## W

**Worktree**
Git feature for multiple working directories. Kilo IDE uses these.
