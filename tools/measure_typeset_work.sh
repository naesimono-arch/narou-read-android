#!/usr/bin/env bash
# 本文組版の「仕事量」を回数で測る（縦書き／横書き × 章送り／フォント／行間）。
#
# なぜ ms でなく回数か:
#   開発端末（Find X6 Pro / SD 8 Gen 2）もエミュも速い側へ振れるため、ms は「遅い端末の体感」を
#   一切代弁しない。一方ここで測るのは
#     ①縦書き組版 typeset() の呼び出し回数（1回＝1段落の全再組版）
#     ②置き直したグリフ数と Paint 実測（verticalAdvance）の回数
#     ③横書き BasicText の onTextLayout 回数（①と対になる単位）
#     ④スライダーの値変化回数（＝①を駆動する分母）
#   で、いずれも **composition の起こり方だけで決まり端末性能に依存しない**。
#   同じ操作なら遅い端末でも同じ数字が出る＝回帰をハイエンド機で止められる。
#
# 分母が端末非依存である構造的根拠:
#   文字サイズスライダーは valueRange 14f..24f / steps=9 の**離散**（ReadingSettingsSheet.kt）＝
#   端から端までの1ドラッグは注入速度によらず**必ず 10 回**の値変化になる。行間は 2.3..2.8 / steps=4 ＝**5 回**。
#   ゆえに「1ドラッグあたりの組版回数」は 10（or 5）× 可視段落数 で決まり、実測もその形で出る。
#
# 前提: debug ビルドが導入済み（プローブ受け口 TypesetProbeReceiver は src/debug 限定）・root 不要。
#   ただし prefs の固定に root（`su 0 cp`）を使うため、エミュレータまたは root 端末で走らせること。
#
# 使い方:
#   bash tools/measure_typeset_work.sh <serial> [bookId] [work|scroll]
#   例: bash tools/measure_typeset_work.sh emulator-5554                  # 仕事量の計測
#       bash tools/measure_typeset_work.sh emulator-5554 5e3cb10d scroll  # 章遷移×スクロール位置の記録
#
# ⚠️ adb は必ず -s <serial> 付き（複数台繋がっている環境では無指定が "more than one device" で落ちる）。

set -uo pipefail

SERIAL="${1:-}"
BOOK_ID="${2:-5e3cb10d}"
# work  … 組版の仕事量の計測（既定・縦横 × 章送り/フォント/行間）
# scroll… 縦書きの章遷移×スクロール位置のベースライン記録（改善 A 前の挙動の保全）
MODE="${3:-work}"
PKG=com.novelreader
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
RECEIVER="$PKG/com.novelreader.perf.TypesetProbeReceiver"
ACTION=com.novelreader.debug.action.TYPESET_PROBE
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [[ -z "$SERIAL" ]]; then
    echo "usage: bash tools/measure_typeset_work.sh <serial> [bookId]" >&2
    echo "接続中:" >&2; "$ADB" devices | sed -n '2,$p' >&2
    exit 2
fi

sh() { "$ADB" -s "$SERIAL" shell "$@"; }

# プローブ操作（on/off/reset/dump）。ordered broadcast の resultData を1行で受ける
# ＝計測点ごとに logcat へ吐かないので、ring buffer 溢れによる静かな欠測が起きない。
probe() {
    sh "am broadcast -a $ACTION -n $RECEIVER --es cmd $1" 2>&1 |
        sed -n 's/.*data="\([^"]*\)".*/\1/p'
}

# 画面の a11y ツリーを取得する。縦書き本文は text ノードを持たない（desc のみ）ため、
# 当てずっぽうのタップをせず必ずこのダンプから座標を決める
# （docs/knowledge/vertical-reading-has-no-text-nodes-for-ui-automation.md）。
dump_ui() {
    sh uiautomator dump /sdcard/nr_ui.xml >/dev/null 2>&1
    sh cat /sdcard/nr_ui.xml > "$WORK/ui.xml"
}

# ダンプから要素の中心座標を出す。
#   center_of text <文字列>    … text 完全一致
#   center_of seekbar <n>      … SeekBar の n 番目（0 起点＝文字サイズ/行間/本文余白の順）
center_of() {
    python3 - "$WORK/ui.xml" "$1" "$2" <<'PY'
import re, sys
xml, kind, key = open(sys.argv[1], encoding='utf-8').read(), sys.argv[2], sys.argv[3]
hits = []
for n in re.findall(r'<node[^>]*>', xml):
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    if kind == 'text':
        t = re.search(r' text="([^"]*)"', n)
        if t and t.group(1) == key:
            hits.append((x1, y1, x2, y2))
    else:
        c = re.search(r'class="([^"]*)"', n)
        if c and c.group(1).endswith('SeekBar'):
            hits.append((x1, y1, x2, y2))
idx = int(key) if kind == 'seekbar' else 0
if idx >= len(hits):
    sys.exit(1)
x1, y1, x2, y2 = hits[idx]
print(x1, y1, x2, y2, (x1 + x2) // 2, (y1 + y2) // 2)
PY
}

# 面の決定論化。端末に残った prefs で測ると、同じスクリプトが日によって別の面を測る
# （2026-08-19 の TabSwipeBenchmark 全走行 fail と同じ罠）。毎回まるごと書き直す。
# 既存ファイルへ cp するのは owner/mode/SELinux ラベルを保つため（新規作成すると root 所有で壊れる）。
setup_prefs() {
    local vertical="$1"
    cat > "$WORK/app_prefs.xml" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="settings_schema_version" value="1" />
    <boolean name="notif_priming_shown" value="true" />
    <boolean name="battery_dialog_dismissed" value="true" />
    <boolean name="intro_reading_shown" value="true" />
    <boolean name="intro_about_shown" value="true" />
    <boolean name="intro_search_shown" value="true" />
    <boolean name="immersive_hint_shown" value="true" />
    <boolean name="reading_vertical" value="$vertical" />
    <int name="reading_font_size" value="18" />
    <float name="reading_line_height" value="2.5" />
    <int name="reading_body_margin" value="15" />
</map>
EOF
    sh am force-stop $PKG
    "$ADB" -s "$SERIAL" push "$WORK/app_prefs.xml" /data/local/tmp/nr_app_prefs.xml >/dev/null
    sh "su 0 cp /data/local/tmp/nr_app_prefs.xml /data/data/$PKG/shared_prefs/app_prefs.xml"
    # 章頭から始める（既読位置の残りで測ると可視段落集合が run ごとに変わる）。
    # run-as で書くと owner と SELinux ラベルが自動的に正しくなる（root の sqlite3 だと壊れうる）。
    sh "run-as $PKG sqlite3 /data/data/$PKG/databases/novel_reader_db \
        \"insert or replace into progress values('$BOOK_ID','chap_1.html',0,0,0,0);\"" >/dev/null
    # フルスクリーン確認ダイアログ（システム）は起動のたびに前面を奪う＝先に恒久抑止する。
    sh settings put secure immersive_mode_confirmations confirmed >/dev/null
}

# 本文へ直行する（deep link）。本棚のスクロール位置や並びに依存しない。
launch_reading() {
    sh "am start -n $PKG/.MainActivity --es com.novelreader.extra.BOOK_ID $BOOK_ID" >/dev/null
    sleep 4
    dump_ui
}

# 書字方向が指定どおりかを**画面から**確かめる（幾何で判定する）。
# ⚠️ 「横書きなら本文が text ノードに出る」は**誤り**＝本文段落は縦横とも clearAndSetSemantics で
#    desc 1本に畳まれ text を持たない（横書き RubyText / 縦書き VerticalChapterContent の両方）。
#    text に出るのは章見出し（ChapterHeader の Compose Text）だけで、これは横書きにしか無い。
#    唯一確実な徴は**本文段落ノードの縦横比**＝縦書きは列（細長い縦棒 h>w）・横書きは行の束（w>h）。
# ここを省くと「着地しているのに別の面を測っていた」に気づけない（同型の実害＝2026-08-19 TabSwipeBenchmark）。
assert_orientation() {
    local want="$1"
    python3 - "$WORK/ui.xml" "$want" <<'PY'
import re, sys
xml, want = open(sys.argv[1], encoding='utf-8').read(), sys.argv[2]
tall = wide = 0
for n in re.findall(r'<node[^>]*>', xml):
    d = re.search(r'content-desc="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not d or not b or len(d.group(1)) < 10:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    if y2 - y1 > x2 - x1:
        tall += 1
    else:
        wide += 1
if tall + wide < 2:
    sys.exit(f"本文段落ノードが見つからない（本文へ着地していない疑い）: tall={tall} wide={wide}")
got = 'vertical' if tall > wide else 'horizontal'
if got != want:
    sys.exit(f"面が指定と違う: want={want} got={got} (縦長={tall} 横長={wide})")
print(f"  面OK: {want} (本文段落 縦長={tall} 横長={wide})")
PY
}

# 下部クローム（次章/目次/表示設定/前章）を確実に出す。没入モードで畳まれた状態のまま
# 直前のダンプ座標でタップすると、本文への素のタップに化けて**何も起きないまま全カウンタ 0**になる
# ＝「操作そのものが走っていないのに緑に見える」失敗モード。見えるまで本文中央をタップして呼び戻す。
ensure_chrome() {
    local i
    for i in 1 2 3; do
        dump_ui
        if center_of text 表示設定 >/dev/null 2>&1; then return 0; fi
        sh input tap 540 1100 >/dev/null
        sleep 1.5
    done
    dump_ui
    center_of text 表示設定 >/dev/null 2>&1
}

# 下部クロームのボタンを**存在を確かめてから**押す。
# ⚠️ 「見えているはず」で座標を打つと、没入で畳まれている場合にタップが本文へ落ちて
#    **クロームを呼び戻すだけ**になり、押したつもりの操作が1つも起きない。しかも画面は
#    それらしく見えるので、そのまま次の手順へ進むと嘘の観測記録ができる（実際に踏んだ）。
tap_chrome() {
    local label="$1" coords bx by
    ensure_chrome || return 1
    coords="$(center_of text "$label")" || return 1
    read -r _ _ _ _ bx by <<<"$coords"
    sh input tap "$bx" "$by" >/dev/null
    sleep 3
}

# 縦書きの「章遷移とスクロール位置の関係」を数値で残す（改善 A の前後で diff する土台）。
# A は組版の寿命（LazyRow item スコープ → 章スコープ）を変える＝スクロール位置の復元に
# 影響しうるため、**A に入る前の挙動**をここで固定しておく。
# 記録するのは ①可視段落数 ②最右列の右端 x（章頭なら見出し列があり画面右端に届かない）
# ③段落 desc の SHA1 先頭6桁（本文そのものは出さずに「同じ段落か」だけを比較する）
# ④progress の (scrollIndex, scrollOffset)。
scroll_snapshot() {
    local label="$1"
    dump_ui
    python3 - "$WORK/ui.xml" "$label" <<'PY'
import re, sys, hashlib
xml, label = open(sys.argv[1], encoding='utf-8').read(), sys.argv[2]
paras = []
for n in re.findall(r'<node[^>]*>', xml):
    d = re.search(r'content-desc="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not d or not b or len(d.group(1)) < 6:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    if y2 - y1 <= x2 - x1:
        continue
    paras.append((x1, x2, hashlib.sha1(d.group(1).encode()).hexdigest()[:6]))
paras.sort(key=lambda p: -p[0])
right = paras[0][1] if paras else '-'
head = paras[0][2] if paras else '-'
print(f"  {label:28} 可視段落={len(paras):2} 最右列右端x={right:>5} 最右段落={head} 列左端x={[p[0] for p in paras]}")
PY
    echo -n "  ${label:0:28}   progress="
    sh "run-as $PKG sqlite3 /data/data/$PKG/databases/novel_reader_db \
        \"select lastReadFilename||' idx='||scrollIndex||' off='||scrollOffset from progress where bookId='$BOOK_ID';\"" | tr -d '\r'
}

run_scroll_trace() {
    echo "== 縦書き 章遷移×スクロール位置のベースライン（改善 A 前の挙動） =="
    setup_prefs true
    launch_reading
    assert_orientation vertical || return 1
    scroll_snapshot "1.章1着地"
    tap_chrome 次章 || { echo "  次章を押せない" >&2; return 1; }
    scroll_snapshot "2.次章→章2(章1は未スクロール)"
    # 500ms なのは移動量を小さく固定するため（1発 +5列）。「100ms は fling に食われ動かない」は
    # 誤り＝上乗せで逆に大きく動く（+18〜20列）＝docs/knowledge/vertical-lazyrow-fast-swipe-is-not-eaten-by-fling.md
    local i
    for i in 1 2 3 4; do sh input swipe 250 1200 850 1200 500 >/dev/null; sleep 1; done
    sleep 2
    scroll_snapshot "3.章2内を4回送り"
    tap_chrome 次章 || return 1
    scroll_snapshot "4.次章→章3(新章)"
    tap_chrome 前章 || return 1
    scroll_snapshot "5.前章→章2(既読・位置あり)"
    tap_chrome 前章 || return 1
    scroll_snapshot "6.前章→章1(既読・位置なし)"
}

# 端から端までの1ドラッグを **MOVE 20 段で明示注入**する。
# なぜ `input swipe` でなく `input motionevent` か: swipe は注入ステップ数が端末/版で変わり、
# 「1ドラッグ＝何値」が環境依存になる。明示注入なら段数がこちらの定数になり再現する
# （スライダーが離散なので、段数を増やしても値変化数は steps 由来の上限で頭打ち＝これが端末非依存の要）。
drag_slider() {
    local x1="$1" x2="$2" y="$3" cmd
    cmd="input motionevent DOWN $x1 $y"
    for i in $(seq 1 20); do
        cmd="$cmd; input motionevent MOVE $(( x1 + (x2 - x1) * i / 20 )) $y"
    done
    cmd="$cmd; input motionevent UP $x2 $y"
    sh "$cmd" >/dev/null
    sleep 2
}

run_scenario() {
    local orientation="$1" scenario="$2"
    echo "== $orientation / $scenario =="
    setup_prefs "$([[ $orientation == vertical ]] && echo true || echo false)"
    launch_reading
    assert_orientation "$orientation" || return 1

    ensure_chrome || { echo "  下部クロームを出せない（表示設定ボタン不在）" >&2; return 1; }

    local coords idx bx1 bx2 by lx rx nx ny sx sy
    case "$scenario" in
    chapter_flip)
        coords="$(center_of text 次章)" || { echo "  次章ボタンが見つからない" >&2; return 1; }
        read -r _ _ _ _ nx ny <<<"$coords"
        probe on >/dev/null
        for _ in 1 2 3 4 5; do sh input tap "$nx" "$ny" >/dev/null; sleep 1.5; done
        ;;
    font_drag | lineheight_drag)
        coords="$(center_of text 表示設定)" || { echo "  表示設定ボタンが無い" >&2; return 1; }
        read -r _ _ _ _ sx sy <<<"$coords"
        sh input tap "$sx" "$sy" >/dev/null
        sleep 2.5
        dump_ui
        # SeekBar は上から 文字サイズ / 行間 / 本文余白 の順で3本。
        if [[ $scenario == font_drag ]]; then idx=0; else idx=1; fi
        coords="$(center_of seekbar "$idx")" || { echo "  スライダー #$idx が無い（シートが開いていない）" >&2; return 1; }
        read -r bx1 _ bx2 _ _ by <<<"$coords"
        # 端は当たり判定の外＝内側へ寄せる（外すと1値も動かず全カウンタ 0 で「緑に見える」）。
        lx=$(( bx1 + 45 )); rx=$(( bx2 - 18 ))
        sh input tap "$lx" "$by" >/dev/null   # 最小値へ寄せてからドラッグ（開始値を固定）
        sleep 1.5
        probe on >/dev/null
        drag_slider "$lx" "$rx" "$by"
        ;;
    esac
    echo "  $(probe dump)"
    echo
}

echo "device=$SERIAL book=$BOOK_ID  ($(sh getprop ro.product.model | tr -d '\r'), $(sh wm size | tr -d '\r'))"
echo

if [[ "$MODE" == scroll ]]; then
    run_scroll_trace
    exit $?
fi

echo "指標: v_typeset=縦書き組版の呼び出し回数 / v_glyphs=置き直したグリフ数 / v_advance=Paint実測回数"
echo "      h_layout=横書き BasicText の再レイアウト回数 / font_eff・lh_eff=スライダーの実効値変化数（分母）"
echo
for orientation in vertical horizontal; do
    for scenario in chapter_flip font_drag lineheight_drag; do
        run_scenario "$orientation" "$scenario"
    done
done
