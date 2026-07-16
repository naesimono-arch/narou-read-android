#!/usr/bin/env bash
#
# run_macrobenchmark.sh — ColorOS 実機で Macrobenchmark（コールド起動）を確実に完走させる標準手順。
#
# 用途:
#   OPPO/ColorOS では UiAutomation のシェル完了待ちが永久ブロックする（docs/knowledge/
#   coloros-uiautomation-shell-pipe-eof-hang.md）。本スクリプトは am instrument を背景起動し、
#   テストプロセスへ 2 秒周期で SIGQUIT を送り続ける「除細動ループ」で完走させる。
#
# 前提:
#   - 事前に `adb-bridge` 済み（WSL2 から実機が adb で見えている状態）。
#   - benchmark APK は既にビルド済み（ビルドは本スクリプトの責務外）。未ビルドなら --install が案内する。
#   - PATH の adb は承認済み鍵を提示するラッパー（platform-tools を PATH 前置きしないこと）。
#
# 実行例:
#   tools/run_macrobenchmark.sh                          # 計測のみ（従来挙動＝起動計測）
#   tools/run_macrobenchmark.sh --install                # APK を install -r -g してから計測
#   tools/run_macrobenchmark.sh --assert                 # 起動予算 assert を有効化して計測（startup 専用）
#   tools/run_macrobenchmark.sh --assert --budget-median 100 --budget-max 100  # 予算を絞って FAIL 経路を実証
#   tools/run_macrobenchmark.sh --install --assert --serial 192.168.1.210:5555
#   tools/run_macrobenchmark.sh --scenario shelf-scroll  # 本棚スクロール jank（frame timing）を計測
#
# シナリオ:
#   --scenario startup       起動計測（既定）。--assert / --budget-* はこの scenario 専用。
#   --scenario shelf-scroll  本棚スクロール jank 計測（BookshelfScrollBenchmark）。予算 assert 無し。
#
# 注意:
#   コールド起動5反復は除細動込みで約13分（knowledge 実測）。タイムアウトは余裕を持たせてある。
#
set -euo pipefail
# set -e の地雷回避方針: コマンド置換 `X="$(pipe)"` は pipefail 下で中間段(pidof/ls 等)が
# 非ゼロを返すと代入文ごと非ゼロ化し、die を経由せず即 exit する。`A && B` 単文もリスト全体が
# 非ゼロだと同型で死ぬ。ゆえに「失敗が正常な段」は先頭段に `|| true` を挟むか if 化して失敗許容を
# 明示する（本来 die すべき経路＝タイムアウト・install 失敗・起動失敗は従来どおり die させる）。

# ---- 定数 ---------------------------------------------------------------
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_PACKAGE="com.novelreader.macrobenchmark"
TEST_RUNNER="androidx.test.runner.AndroidJUnitRunner"
# TEST_CLASS は scenario で確定する（既定 startup）。下のオプション解析後に代入する。
STARTUP_CLASS="com.novelreader.macrobenchmark.StartupBenchmark"
SHELF_SCROLL_CLASS="com.novelreader.macrobenchmark.BookshelfScrollBenchmark"
TEST_CLASS="$STARTUP_CLASS"

APP_APK="$REPO_ROOT/android/app/build/outputs/apk/benchmark/app-benchmark.apk"
MACRO_APK="$REPO_ROOT/android/macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk"

RESULT_DIR="$REPO_ROOT/android/macrobenchmark/build/benchmark-results"  # gitignore 圏内
# 端末側の JSON 出力先（前回実測での実出力先）。self-instrumenting のためテスト APK 名の下に出る。
DEVICE_MEDIA_DIR="/sdcard/Android/media/${TEST_PACKAGE}"

TIMEOUT_SEC=1800          # 30分。5反復（約13分）＋余裕
PID_POLL_TIMEOUT_SEC=120  # テストプロセス起動を待つ上限
DEFIB_INTERVAL_SEC=2      # SIGQUIT 送出周期（knowledge 実測形）
HEARTBEAT_SEC=15          # 進行表示の周期

# ---- オプション解析 -----------------------------------------------------
DO_ASSERT=0
DO_INSTALL=0
SERIAL=""
SCENARIO="startup"  # startup（既定・従来挙動）| shelf-scroll
BUDGET_MEDIAN=""   # 空なら透過しない＝StartupBudget 側の既定定数が使われる
BUDGET_MAX=""      # 同上（--assert と併用前提。単独指定は assert 無効なら無視される）
while [ $# -gt 0 ]; do
  case "$1" in
    --assert)  DO_ASSERT=1; shift ;;
    --install) DO_INSTALL=1; shift ;;
    --serial)  SERIAL="${2:-}"; shift 2 ;;
    --scenario) SCENARIO="${2:-}"; shift 2 ;;
    --budget-median) BUDGET_MEDIAN="${2:-}"; shift 2 ;;
    --budget-max)    BUDGET_MAX="${2:-}"; shift 2 ;;
    -h|--help)
      grep '^#' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "不明なオプション: $1" >&2; exit 2 ;;
  esac
done

# ---- scenario 確定と併用ガード -----------------------------------------
# --assert / --budget-* は startup 専用。shelf-scroll と併用されたら黙殺せずエラー終了する
# （予算 assert は BookshelfScrollBenchmark に無く、指定を無視して緑にすると誤解を生むため）。
case "$SCENARIO" in
  startup)
    TEST_CLASS="$STARTUP_CLASS" ;;
  shelf-scroll)
    TEST_CLASS="$SHELF_SCROLL_CLASS"
    if [ "$DO_ASSERT" -eq 1 ] || [ -n "$BUDGET_MEDIAN" ] || [ -n "$BUDGET_MAX" ]; then
      echo "--assert / --budget-* は --scenario startup 専用（shelf-scroll とは併用不可）。" >&2
      exit 2
    fi ;;
  *)
    echo "不明な --scenario: '$SCENARIO'（startup | shelf-scroll）" >&2
    exit 2 ;;
esac

# ---- adb コマンド組み立て（serial 指定があれば -s 付与） -----------------
ADB=(adb)
if [ -n "$SERIAL" ]; then
  ADB=(adb -s "$SERIAL")
fi

log()  { printf '%s %s\n' "[$(date +%H:%M:%S)]" "$*"; }
die()  { printf '%s %s\n' "[ERROR]" "$*" >&2; exit 1; }

# ---- 1. デバイス接続確認 ------------------------------------------------
# `adb devices` の state=device 行だけを対象にする（offline/unauthorized を除外）。
mapfile -t ONLINE < <(adb devices | awk '/\tdevice$/ {print $1}')
if [ -n "$SERIAL" ]; then
  found=0
  # `[ ... ] && found=1` 単文は不一致行でリスト全体が非ゼロ→set -e の地雷。if 化で失敗許容を明示。
  for s in "${ONLINE[@]:-}"; do
    if [ "$s" = "$SERIAL" ]; then found=1; fi
  done
  [ "$found" -eq 1 ] || die "指定 serial '$SERIAL' が接続中デバイスに無い。adb-bridge 済みか確認。"
else
  case "${#ONLINE[@]}" in
    0) die "接続中デバイスが無い。まず adb-bridge を実行。" ;;
    1) SERIAL="${ONLINE[0]}"; ADB=(adb -s "$SERIAL") ;;
    *) die "デバイスが複数接続されている（${ONLINE[*]}）。--serial で1台を指定。" ;;
  esac
fi
log "対象デバイス: $SERIAL"

# ---- 2. perfetto/trace_processor 残骸チェック --------------------------
# 残骸（特に port 9001 を握る trace_processor）は新走行を二次ハングさせる。SELinux で kill 不能なため
# 端末再起動でのみ掃除できる（knowledge 参照）。
RESIDUE="$("${ADB[@]}" shell ps -A 2>/dev/null | grep -E 'perfetto|trace_processor' || true)"
if [ -n "$RESIDUE" ]; then
  echo "$RESIDUE" >&2
  die "perfetto/trace_processor の残骸を検出。kill 不能な残骸は端末再起動でのみ掃除可（再起動後に再実行）。"
fi
log "残骸チェック OK"

# ---- 3. (--install 時) APK インストール --------------------------------
if [ "$DO_INSTALL" -eq 1 ]; then
  missing=0
  for apk in "$APP_APK" "$MACRO_APK"; do
    [ -f "$apk" ] || { echo "APK が無い: $apk" >&2; missing=1; }
  done
  if [ "$missing" -eq 1 ]; then
    echo "先に benchmark APK をビルドすること:" >&2
    echo "  cd $REPO_ROOT/android && ./gradlew :app:assembleBenchmark :macrobenchmark:assembleBenchmark" >&2
    exit 1
  fi
  # ColorOS は shell の pm grant を遮断するため、インストール時付与 -g を使う（-r で蔵書 DB 保持）。
  log "install -r -g app-benchmark.apk"
  "${ADB[@]}" install -r -g "$APP_APK"
  log "install -r -g macrobenchmark-benchmark.apk"
  "${ADB[@]}" install -r -g "$MACRO_APK"
fi

# ---- 4. am instrument を背景起動（出力はログファイルへ） -----------------
mkdir -p "$RESULT_DIR"
LOG_FILE="$RESULT_DIR/instrument-$(date +%Y%m%d-%H%M%S).log"

# 引数列: -w 完了待ち・output.enable=true が benchmarkData.json 書き出しの前提条件。
# --assert 時のみ enableBudgetAssert=true を付与（StartupBudget が JSON を読んで判定）。
INSTR_ARGS=(am instrument -w
  -e androidx.benchmark.output.enable true
  -e class "$TEST_CLASS")
if [ "$DO_ASSERT" -eq 1 ]; then
  INSTR_ARGS+=(-e enableBudgetAssert true)
fi
# 予算上書き値を透過（--assert 併用前提）。指定時のみ付与し、未指定なら StartupBudget の既定定数に委ねる。
if [ -n "$BUDGET_MEDIAN" ]; then
  INSTR_ARGS+=(-e budgetMedianMs "$BUDGET_MEDIAN")
fi
if [ -n "$BUDGET_MAX" ]; then
  INSTR_ARGS+=(-e budgetMaxMs "$BUDGET_MAX")
fi
INSTR_ARGS+=("${TEST_PACKAGE}/${TEST_RUNNER}")

log "instrument 起動: ${INSTR_ARGS[*]}"
log "ログ: $LOG_FILE"
# adb クライアントを背景化（デバイス側 am instrument の完了で終わる）。出力は本文判定用にファイルへ。
"${ADB[@]}" shell "${INSTR_ARGS[@]}" >"$LOG_FILE" 2>&1 &
INSTR_ADB_PID=$!

# 異常終了時に背景プロセスを確実に片付ける
cleanup() {
  kill "$INSTR_ADB_PID" 2>/dev/null || true
  [ -n "${DEFIB_ADB_PID:-}" ] && kill "$DEFIB_ADB_PID" 2>/dev/null || true
}
trap cleanup EXIT

# ---- 5. テストプロセスの PID を ps ポーリングで取得 ---------------------
DEV_PID=""
poll_start=$(date +%s)
while :; do
  # 起動前は pidof が exit 1（未起動＝正常）→ pipefail でパイプ全体が非ゼロ→代入文ごと即 exit する
  # のが実走即死の真因。pidof 段を `|| true` で失敗許容し、未起動時は空文字を得てポーリング継続。
  DEV_PID="$({ "${ADB[@]}" shell pidof "$TEST_PACKAGE" 2>/dev/null || true; } | tr -d '\r' | awk '{print $1}')"
  if [ -n "$DEV_PID" ]; then break; fi
  # instrument がプロセス起動前に落ちていないか監視
  if ! kill -0 "$INSTR_ADB_PID" 2>/dev/null; then
    log "instrument がテストプロセス起動前に終了。ログ末尾:"; tail -n 40 "$LOG_FILE" >&2; die "起動失敗"
  fi
  if [ "$(( $(date +%s) - poll_start ))" -gt "$PID_POLL_TIMEOUT_SEC" ]; then
    die "テストプロセス($TEST_PACKAGE) の PID を ${PID_POLL_TIMEOUT_SEC}s 以内に取得できず。"
  fi
  sleep 2
done
log "テストプロセス PID: $DEV_PID"

# ---- 6. 除細動ループ（SIGQUIT を 2秒周期で送出） ------------------------
# 生存判定は `ps -p`（run-as … kill -0 は偽陰性のため禁止・knowledge 参照）。
# ループ全体をデバイス側の1シェルで回し、adb クライアントを背景化する。
"${ADB[@]}" shell "while ps -p $DEV_PID >/dev/null 2>&1; do run-as $TEST_PACKAGE kill -3 $DEV_PID 2>/dev/null; sleep $DEFIB_INTERVAL_SEC; done" &
DEFIB_ADB_PID=$!
log "除細動ループ開始（$DEFIB_INTERVAL_SEC 秒周期で SIGQUIT）"

# ---- 7. 完了待ち（ハートビート＋タイムアウト） --------------------------
# 注: ここで見ているのはローカル adb クライアント（INSTR_ADB_PID）＝素の kill -0 で可。
#     禁止されているのは「デバイス上アプリプロセスを run-as kill -0 で生存判定」する行為。
run_start=$(date +%s)
while kill -0 "$INSTR_ADB_PID" 2>/dev/null; do
  elapsed=$(( $(date +%s) - run_start ))
  if [ "$elapsed" -gt "$TIMEOUT_SEC" ]; then
    die "タイムアウト（${TIMEOUT_SEC}s 超過）。ログ: $LOG_FILE"
  fi
  log "[heartbeat] 経過 ${elapsed}s / 上限 ${TIMEOUT_SEC}s"
  sleep "$HEARTBEAT_SEC"
done
INSTR_RC=0
wait "$INSTR_ADB_PID" || INSTR_RC=$?
kill "$DEFIB_ADB_PID" 2>/dev/null || true
log "instrument 終了（adb exit=$INSTR_RC）"

# ---- 8. 成否判定（exit code だけに頼らず出力本文で突合） ----------------
# パイプ/adb が exit code を潰す前例があるため、ログ本文のマーカーで最終判定する。
STATUS="UNKNOWN"
if grep -q 'FAILURES!!!' "$LOG_FILE"; then
  STATUS="FAIL"
elif grep -q 'Process crashed' "$LOG_FILE" || grep -q 'shortMsg=' "$LOG_FILE"; then
  STATUS="FAIL"
elif grep -qE 'INSTRUMENTATION_STATUS_CODE: -1' "$LOG_FILE"; then
  STATUS="FAIL"
elif grep -qE 'OK \([0-9]+ test' "$LOG_FILE"; then
  STATUS="PASS"
fi
log "本文判定: $STATUS（adb exit=$INSTR_RC）"

# ---- 9. benchmarkData.json を pull して median/max を表示 ---------------
# ls がファイル未発見で非ゼロを返す経路がある→コマンド置換ごと即 exit しないよう `|| true` で許容。
JSON_ON_DEV="$({ "${ADB[@]}" shell "ls -t ${DEVICE_MEDIA_DIR}/*benchmarkData.json 2>/dev/null | head -1" || true; } | tr -d '\r')"
if [ -n "$JSON_ON_DEV" ]; then
  LOCAL_JSON="$RESULT_DIR/$(basename "$JSON_ON_DEV")"
  # pull は結果表示の副次パス＝失敗しても die させず警告で継続（本体の PASS/FAIL 判定は済んでいる）。
  if "${ADB[@]}" pull "$JSON_ON_DEV" "$LOCAL_JSON" >/dev/null 2>&1; then
    log "pull: $LOCAL_JSON"
  else
    log "pull 失敗: $JSON_ON_DEV（結果表示のみスキップ・計測本体は完了済み）"
  fi
  if command -v python3 >/dev/null 2>&1 && [ -f "$LOCAL_JSON" ]; then
    python3 - "$LOCAL_JSON" <<'PY' || true
import json, sys
# benchmarkData.json の実スキーマ（1.4.1・StartupBudget.kt の parser で確認済み）:
#   benchmarks[] 各要素に name / metrics{} / sampledMetrics{} があり、各メトリクスは
#   { median, minimum, maximum, P50, P90, P95, P99, ... } のような統計オブジェクト。
#   StartupTimingMetric → metrics.timeToInitialDisplayMs（median/maximum を見る）
#   FrameTimingMetric   → sampledMetrics.frameDurationCpuMs / frameOverrunMs（P50/P90/P95/P99 を見る）
# scenario によって出るメトリクスが違うため、存在するものだけ表示する（全 benchmarks[] を走査）。
data = json.load(open(sys.argv[1]))

def find(b, key):
    for grp in ("metrics", "sampledMetrics"):
        d = b.get(grp) or {}
        v = d.get(key)
        if isinstance(v, dict):
            return v
    return None

for b in data.get("benchmarks", []):
    print(f"  {b.get('name', '(no name)')}")
    t = find(b, "timeToInitialDisplayMs")
    if t is not None:
        print(f"    timeToInitialDisplayMs: median={t.get('median')}ms max={t.get('maximum')}ms")
    for key in ("frameDurationCpuMs", "frameOverrunMs"):
        s = find(b, key)
        if s is not None:
            parts = " ".join(f"{p}={s.get(p)}" for p in ("P50", "P90", "P95", "P99") if p in s)
            print(f"    {key}: {parts} (ms)")
PY
  fi
else
  log "benchmarkData.json が端末に見当たらない（output.enable=true か確認）"
fi

# ---- 総合成否を exit code へ連動 ---------------------------------------
trap - EXIT
if [ "$STATUS" = "PASS" ]; then
  log "RESULT: PASS"
  exit 0
else
  log "RESULT: $STATUS（詳細は $LOG_FILE）"
  exit 1
fi
