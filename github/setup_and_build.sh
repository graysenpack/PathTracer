#!/bin/bash
# PathTracer Mod — setup & build script
# Downloads the Gradle wrapper directly (no system Gradle needed),
# builds the mod, and installs it into your Minecraft mods folder.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "╔══════════════════════════════════╗"
echo "║   PathTracer Mod Builder v1.1    ║"
echo "╚══════════════════════════════════╝"
echo ""

# ─────────────────────────────────────────────
# 1. Find Java 21
# ─────────────────────────────────────────────
echo "▶ Checking for Java 21..."

find_java21() {
    local candidates=(
        "/opt/homebrew/opt/openjdk@21/bin/java"
        "/usr/local/opt/openjdk@21/bin/java"
        "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java"
        "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java"
        "/usr/bin/java"
    )
    for cmd in "${candidates[@]}"; do
        if [ -x "$cmd" ]; then
            local ver
            ver=$("$cmd" -version 2>&1 | head -1)
            if echo "$ver" | grep -q '"21'; then
                echo "$cmd"; return 0
            fi
        fi
    done
    if command -v java &>/dev/null; then
        local ver
        ver=$(java -version 2>&1 | head -1)
        if echo "$ver" | grep -q '"21'; then
            echo "java"; return 0
        fi
    fi
    return 1
}

JAVA_CMD=$(find_java21 || true)

if [ -z "$JAVA_CMD" ]; then
    echo "  ✗ Java 21 not found."
    if command -v brew &>/dev/null; then
        echo "  → Installing openjdk@21 via Homebrew..."
        brew install openjdk@21
        if [ -x "/opt/homebrew/opt/openjdk@21/bin/java" ]; then
            JAVA_CMD="/opt/homebrew/opt/openjdk@21/bin/java"
        else
            JAVA_CMD="/usr/local/opt/openjdk@21/bin/java"
        fi
        echo "  ✓ Java 21 installed."
    else
        echo ""
        echo "  Please install Java 21 from: https://adoptium.net"
        echo "  Then re-run this script."
        exit 1
    fi
else
    echo "  ✓ Found: $JAVA_CMD"
fi

# Resolve JAVA_HOME from the java binary
JAVA_BIN_DIR="$(dirname "$JAVA_CMD")"
export JAVA_HOME="${JAVA_BIN_DIR%/bin}"
# Handle Apple JDK bundle layout (Contents/Home/bin)
if [ ! -f "$JAVA_HOME/release" ] && [ -d "$JAVA_HOME/../Home" ]; then
    JAVA_HOME="$(cd "$JAVA_HOME/../Home" && pwd)"
fi

# ─────────────────────────────────────────────
# 2. Set up the Gradle wrapper (without running
#    system Gradle — avoids version conflicts)
# ─────────────────────────────────────────────
echo ""
echo "▶ Setting up Gradle 9.5.0 wrapper..."

mkdir -p gradle/wrapper

# Download gradle-wrapper.jar from the official Fabric example mod repo
# (a trusted ~59KB bootloader that handles the actual Gradle download)
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "  → Downloading gradle-wrapper.jar..."
    curl -fsSL -o gradle/wrapper/gradle-wrapper.jar \
        "https://raw.githubusercontent.com/FabricMC/fabric-example-mod/1.21.1/gradle/wrapper/gradle-wrapper.jar"
    echo "  ✓ Downloaded."
else
    echo "  ✓ gradle-wrapper.jar already present."
fi

# Write properties pointing to Gradle 9.4
# (Fabric Loom 1.16.1 requires Gradle 9.4+)
cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
echo "  ✓ Wrapper configured for Gradle 9.5.0."

# Download the real gradlew script from the Fabric example mod repo
# (avoids hand-rolling the quoting logic, which is fiddly)
echo "  → Downloading gradlew..."
curl -fsSL -o gradlew \
    "https://raw.githubusercontent.com/FabricMC/fabric-example-mod/1.21.1/gradlew"
chmod +x gradlew
echo "  ✓ gradlew script ready."

# ─────────────────────────────────────────────
# 3. Build the mod
# ─────────────────────────────────────────────
echo ""
echo "▶ Clearing cached Gradle project state..."
rm -rf .gradle
echo "  ✓ Cache cleared."

echo ""
echo "▶ Building mod..."
echo "  (First run downloads Minecraft + Fabric libs — please wait)"
echo ""

JAVA_HOME="$JAVA_HOME" ./gradlew build

# ─────────────────────────────────────────────
# 4. Install into Minecraft mods folder
# ─────────────────────────────────────────────
echo ""
echo "▶ Installing mod..."

JAR=$(find build/libs -name "path-tracer-*.jar" ! -name "*-sources.jar" 2>/dev/null | head -1)

if [ -z "$JAR" ]; then
    echo "  ✗ Build output jar not found — check build/libs/ for errors."
    exit 1
fi

echo "  ✓ Built: $(basename "$JAR")"
echo ""
echo "  ┌─ Mac users ────────────────────────────────────────────────┐"
MODS_DIR="$HOME/Library/Application Support/minecraft/mods"
mkdir -p "$MODS_DIR"
cp "$JAR" "$MODS_DIR/"
echo "  │  Auto-copied to: $MODS_DIR"
echo "  └────────────────────────────────────────────────────────────┘"
echo ""
echo "  ┌─ Windows users ────────────────────────────────────────────┐"
echo "  │  Copy the jar to: %APPDATA%\\.minecraft\\mods\\"
echo "  │  Jar is at: $(pwd)/$(basename "$JAR" | sed 's|^|build/libs/|')"
echo "  └────────────────────────────────────────────────────────────┘"
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║  Done! Launch Minecraft with Fabric 1.21.11 to test it.   ║"
echo "║  Walk around, then press H to toggle the path overlay.    ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
