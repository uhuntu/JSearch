# Dockerfile for running JSearch modern/ tests
# Build: docker build -t jsearch .
# Run tests: docker run --rm jsearch

FROM eclipse-temurin:11-jdk

WORKDIR /app

# Copy the entire project
COPY . .

# Set working directory to modern/
WORKDIR /app/modern

# Compile
RUN javac -encoding UTF-8 -d out $(find src -name '*.java')

# Default command: run tests
CMD ["java", "-Dfile.encoding=UTF-8", "-cp", "out", "jsearch.Tests"]
