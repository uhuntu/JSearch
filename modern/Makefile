# JSearch modern/ Makefile
# Requires JDK 11 or later

.PHONY: all build test clean help

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

clean:
	@echo "Cleaning $(OUT_DIR)..."
	rm -rf $(OUT_DIR)

help:
	@echo "JSearch modern/ build targets:"
	@echo ""
	@echo "  make          - Compile the project"
	@echo "  make test     - Compile and run tests"
	@echo "  make clean    - Remove build output"
	@echo "  make help     - Show this help"
	@echo ""
	@echo "Requirements: JDK 11 or later"
