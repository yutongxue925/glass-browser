#!/usr/bin/env bash
# 一键验证「网址识别 / 搜索分发」核心逻辑（不需要 Android 设备，也不需要 Gradle 构建）
#
# 用法：bash tools/run-urlcheck.sh
# 依赖：JDK 17+；Kotlin 编译器 jar（默认取 Gradle 发行版自带的 embeddable 编译器）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/urlcheck-glassbrowser"
GRADLE_LIB="${GRADLE_HOME:-/opt/gradle-9.5.0}/lib"

pick() { for f in "$@"; do [ -f "$f" ] && echo "$f" && return; done; }

KC=$(pick "$GRADLE_LIB"/kotlin-compiler-embeddable-*.jar \
          /root/.sdkman/candidates/gradle/*/lib/kotlin-compiler-embeddable-*.jar)
STD=$(pick "$GRADLE_LIB"/kotlin-stdlib-2*.jar \
           /root/.sdkman/candidates/gradle/*/lib/kotlin-stdlib-2*.jar)
EXTRA=$(ls "$GRADLE_LIB"/kotlin-reflect-*.jar "$GRADLE_LIB"/kotlin-script-runtime-*.jar \
             "$GRADLE_LIB"/kotlin-daemon-embeddable-*.jar "$GRADLE_LIB"/kotlinx-coroutines-core-jvm-*.jar \
             "$GRADLE_LIB"/annotations-*.jar 2>/dev/null | tr '\n' ':')

[ -n "$KC" ] && [ -n "$STD" ] || { echo "找不到 Kotlin 编译器/标准库，请设置 GRADLE_HOME"; exit 1; }

echo "编译器: $KC"
rm -rf "$OUT" && mkdir -p "$OUT"

java -cp "$KC:$STD:$EXTRA" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -classpath "$STD" -d "$OUT/app.jar" \
  "$ROOT/app/src/main/java/com/glassbrowser/core/UrlResolver.kt" \
  "$ROOT/app/src/main/java/com/glassbrowser/core/SearchEngine.kt" \
  "$ROOT/app/src/test/java/com/glassbrowser/core/UrlResolverCheck.kt"

java -cp "$OUT/app.jar:$STD" com.glassbrowser.core.UrlResolverCheckKt
