# JSearch modern/ Makefile
# Requires JDK 11 or later

.PHONY: all build test benchmark web swing replica clean help

OUT_DIR := out
SRC_DIR := src
JAVA_FILES := $(shell find $(SRC_DIR) -name '*.java')

all: build

build:
	@echo "Compiling..."
	@mkdir -p $(OUT_DIR)
	javac -encoding UTF-8 -d $(OUT_DIR) $(JAVA_FILES)
	@echo "Compilation successful."

test: build
	@echo ""
	@echo "Running tests..."
	java -Dfile.encoding=UTF-8 -cp $(OUT_DIR) jsearch.Tests
	@echo ""
	@echo "Checking the numbers the documents quote..."
	java -Dfile.encoding=UTF-8 -cp $(OUT_DIR) jsearch.DocsGuard

benchmark: build
	@echo ""
	@echo "Running benchmarks..."
	java -Dfile.encoding=UTF-8 -cp $(OUT_DIR) jsearch.Benchmarks

web: build
	@echo ""
	@echo "Launching Web UI on http://localhost:8080..."
	java -Dfile.encoding=UTF-8 -cp $(OUT_DIR) jsearch.WebServer

swing: build
	@echo ""
	@echo "Launching the Swing reference UI (needs a display)..."
	java -Dfile.encoding=UTF-8 -cp $(OUT_DIR) jsearch.SwingDemo

replica: build
	@echo ""
	@echo "Launching the 1:1 Swing replica of the 2002 AWT applet (needs a display)..."
	@echo "(pass REP=JSEngines-file to go live through HttpPageFetcher)"
	java -Dfile.encoding=UTF-8 -cp $(OUT_DIR) jsearch.SwingReplica $(REP)

clean:
	@echo "Cleaning $(OUT_DIR)..."
	rm -rf $(OUT_DIR)

help:
	@echo "JSearch modern/ build targets:"
	@echo ""
	@echo "  make            - Compile the project"
	@echo "  make test       - Compile and run tests"
	@echo "  make benchmark  - Compile and run benchmarks"
	@echo "  make web        - Launch interactive Web UI and REST API"
	@echo "  make swing      - Launch the Swing reference UI"
	@echo "  make replica    - Launch the 1:1 Swing replica of the 2002 applet"
	@echo "  make clean      - Remove build output"
	@echo "  make help       - Show this help"
	@echo ""
	@echo "Requirements: JDK 11 or later"
