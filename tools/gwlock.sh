#!/usr/bin/env bash
# Gradle を「1 作業ツリーにつき同時1本」に直列化して起動するラッパー。
#
# ── なぜ必要か（2026-08-21 の実害。並列委譲を始めた瞬間に初めて出る） ─────────────
# Gradle は「同じプロジェクトツリーに対する2本目のビルド」を自分では拒まない。
# ところがビルドが書き換える可変資源は、**どれもツリー単位でしか分かれていない**:
#   (a) 出力ルート＝`android/app/build/`（canonical では init スクリプトで ext4 へ退避した先）
#   (b) プロジェクト直下の `.gradle/`（file hash キャッシュ・configuration cache）
#   (c) ソースツリーへ書き戻す成果物（golden PNG の record 等）
#   (d) 各タスクが使う中間ディレクトリ・レポート出力・ロックファイル
# 2本が同時に走ると、片方が掃除した／掴んだファイルをもう片方が書こうとして I/O エラーになり、
# **BUILD FAILED** になる。実例は `Could not write XML test results …` だが、
# ⚠️ **これは test-results 固有の問題ではない**——上のどれで先に当たるかが違うだけで、
# 「共有された出力先の奪い合いが、テスト結果ではなくビルドの成否を汚染する」という同じ機序。
#
# ⚠️ 最大の害は落ちること自体ではなく **偽の赤**: テストは完走していて結果 XML は failures=0 なのに
# ビルドは FAILED になる＝「ゲートが緑か」を答えられなくなる。単独実行では再現しないので、
# 並列で回した便だけが 70 分待って何もコミットできない、という形で表面化した。
#
# ── 対策 ────────────────────────────────────────────────────────────
# 資源の粒度（＝作業ツリー）と同じ粒度の排他ロックを、ビルド開始前（JVM を起こす前）に取る。
# 退避先を呼び出しごとにユニーク化する案は採らない: 衝突を (a) から (b)(c) へ移すだけで、
# しかも増分ビルド／キャッシュを毎回捨てるので極端に遅くなる。
# **並列で回したいときは worktree**（`wt-new`）＝ツリーが別なのでロックも別、待ちも発生しない。
set -uo pipefail

# 自分の設置場所からツリー root を決める（cwd に依存させない＝各ツリーが自分のコピーで自分を守る）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"

# ロックファイルは必ず ext4 に置く。/mnt/c(drvfs) の flock は当てにできないため
# （drvfs は hardlink 等の POSIX 操作が EPERM になる程度には不完全＝ロックも信用しない）。
LOCK_DIR="${NR_BUILD_LOCK_DIR:-$HOME/ext-build/.locks}"
mkdir -p "$LOCK_DIR" || { echo "[gwlock] ロック置き場を作れない: $LOCK_DIR" >&2; exit 1; }
KEY="$(printf '%s' "$ROOT" | tr -c 'A-Za-z0-9._-' '_')"
LOCK="$LOCK_DIR/$KEY.lock"
HOLDER="$LOCK_DIR/$KEY.holder"

holder_desc() {
  # 保持者が分からないまま待たされると「固まった」と誤認されるので、必ず誰が持っているか出す
  if [ -s "$HOLDER" ]; then cat "$HOLDER"; else echo "(保持者情報なし＝直前に解放された可能性)"; fi
}

exec 9>"$LOCK" || { echo "[gwlock] ロックを開けない: $LOCK" >&2; exit 1; }
if ! flock -n 9; then
  echo "[gwlock] このツリーで別の Gradle が実行中のため待機します: $ROOT"
  echo "[gwlock]   保持者: $(holder_desc)"
  echo "[gwlock]   並列で回したいなら worktree（wt-new <branch>）で別ツリーを作ること。"
  waited=0
  until flock -w 30 9; do
    waited=$((waited + 30))
    hpid="$(awk '{print $1}' "$HOLDER" 2>/dev/null)"
    stale=""
    # 保持者が死んでいるのに待たされ続けるのは異常（fd の取り残し）＝黙って待たずに申告する
    case "$hpid" in
      pid=*) kill -0 "${hpid#pid=}" 2>/dev/null || stale=" ⚠️保持者プロセスが存在しない（ロック残留の疑い）" ;;
    esac
    echo "[gwlock] 待機中 ${waited}秒 — 保持者: $(holder_desc)$stale"
  done
fi
printf 'pid=%s 開始=%s ツリー=%s タスク=%s\n' "$$" "$(date '+%H:%M:%S')" "$ROOT" "$*" > "$HOLDER"
# ロックは fd 9 のクローズ（＝プロセス終了）で解放される。holder は情報用なので明示的に消す。
trap 'rm -f "$HOLDER"' EXIT

cd "$ROOT/android" || exit 1

# 非対話シェル（Claude Code の Bash ツール等）は ~/.bashrc を読まない＝ここで明示する
export JAVA_HOME="${JAVA_HOME:-$HOME/opt/jdk-17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

# Android Studio が sync のたびに書き戻す Windows パスの sdk.dir を除去し ANDROID_HOME へ委ねる。
# drvfs では `sed -i` が EPERM を出しうるので一時ファイル経由で書き換える。
if [ -f local.properties ] && grep -q '^sdk\.dir' local.properties; then
  grep -v '^sdk\.dir' local.properties > local.properties.gwlock.tmp \
    && cat local.properties.gwlock.tmp > local.properties
  rm -f local.properties.gwlock.tmp
fi

# canonical(/mnt/c = drvfs) では AAPT2 が hardlink 等の EPERM で落ちるため、成果物だけ ext4 へ逃がす
# init スクリプトが必須。ext4 の worktree では不要（in-tree のままネイティブ速度で通る）。
EXTRA=()
case "$ROOT" in
  /mnt/*)
    INIT="${NR_INIT_SCRIPT:-$HOME/ext-build/novel-reader-init.gradle}"
    if [ ! -f "$INIT" ]; then
      echo "[gwlock] init スクリプトが無い: $INIT" >&2
      echo "[gwlock] /mnt 配下(drvfs)では AAPT2 が EPERM で落ちるため、これ無しでは通らない。" >&2
      exit 1
    fi
    EXTRA+=(--init-script "$INIT")
    ;;
esac
case " $* " in *" --console"*) ;; *) EXTRA+=(--console=plain) ;; esac

"$JAVA_HOME/bin/java" -classpath "$ROOT/android/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "${EXTRA[@]}" "$@"
