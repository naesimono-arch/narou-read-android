#!/usr/bin/env bash
# S3（空行復元）オラクル fixture の生成器。
#
# なぜページ抜き PDF が要るか: S3 が唯一全滅していた N0833HI は .gitignore 済み（sample_pdfs/N0833HI.pdf）で
# CI から参照できず、欠陥6クラスのうち S3 だけが回帰ゲートの外に取り残されていた。全 991 ページは著作物なので
# 持ち込めないため、**空行復元の検証に要る最小単位＝1 話ぶんのページだけ**を抜いた fixture を作る。
#
# 抜いたことで壊れていない証明（このスクリプトが機械で行う）:
#   ① 全文版の抽出（現行実装）で第 N 話の段落列を切り出す
#   ② ページ抜き版の抽出が①と**完全一致**することを assert する（不一致なら生成を中止）
# 一致すれば「fixture の抽出結果＝全文版の当該話の抽出結果」が保証され、期待値を全文版から持ってこられる。
#
# ⚠️ 再生成には裁定待ちのローカル資産が2つ要る（fixture 側は自己完結＝テスト実行には不要）:
#   - sample_pdfs/N0833HI.pdf（.gitignore 済みの全文版）
#   - ~/naro-pdf-engine/work/out/N0833HI/（第二実装の出力＝系譜外オラクルの出所。docs/backlog-frozen.md）
#
# 使い方: bash tools/build_s3_fixture.sh [話番号(既定 57)]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EP="${1:-57}"                      # 既定 57＝全 66 話で最小ページ数（11p）の話。著作物の持ち込みを最小化する。
SRC="$ROOT/sample_pdfs/N0833HI.pdf"
THIRD="$HOME/naro-pdf-engine/work/out/N0833HI"
OUTDIR="$ROOT/android/app/src/test/resources/pdf_oracle"
HARNESS="$ROOT/android/app/src/test/java/com/novelreader/pdf/S3FixtureHarnessTest.kt"
WORK="$(mktemp -d)"

[ -f "$SRC" ] || { echo "全文版が無い: $SRC（裁定待ちのローカル資産）"; exit 1; }
[ -d "$THIRD" ] || { echo "第二実装の出力が無い: $THIRD（裁定待ちのローカル資産）"; exit 1; }

# ハーネスは一時ファイル。異常終了でもテストソースへ残さない（残すと CI が全文版を要求して落ちる）。
cleanup() { rm -f "$HARNESS"; }
trap cleanup EXIT

cat > "$WORK/harness.kt" <<'KT'
package com.novelreader.pdf

import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 一時ハーネス（tools/build_s3_fixture.sh が生成し、終了時に必ず削除する）。リポジトリへ残してはいけない。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class S3FixtureHarnessTest {
    @Before fun init() { PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext()) }

    @Test
    fun dump() {
        val io = JSONObject(File("@IO@").readText())
        PDDocument.load(File(io.getString("pdf"))).use { doc ->
            val pages = PdfExtractor.loadPages(doc)
            val rules = DetectedRules.detect(pages)
            val paras = JSONArray()
            val emitPage = JSONArray()
            var cur = -1
            val streamer = TextProcessor.ParagraphStreamer(doc.numberOfPages, rules, null) { p ->
                paras.put(p); emitPage.put(cur)
            }
            for ((i, chars) in pages.withIndex()) { cur = i; streamer.addPage(i, chars) }
            cur = doc.numberOfPages
            streamer.finish()
            // 段落結合前の「行（＝列）」も落とす。空行欠落の真因はページ境界に在り、
            // 段落列だけでは列 x とページの切れ目が見えないため。
            val pageLines = JSONArray()
            for ((i, chars) in pages.withIndex()) {
                val arr = JSONArray()
                if (i >= 3 && i < doc.numberOfPages - 1) {
                    val bodies = chars.filter {
                        !ParserRules.checkIsTitle(it.fontName, it.size, rules.bodySize) &&
                            ParserRules.isClose(it.size, rules.bodySize)
                    }.sortedWith(compareByDescending<CharBox> { it.x0 }.thenBy { it.top })
                    val dict = TextProcessor.groupCharsByLine(bodies, rules.lineStepX / 2.0)
                    for (x in dict.keys.sortedDescending()) {
                        arr.put(
                            JSONObject().put("x", x)
                                .put("s", TextProcessor.buildLineStr(dict[x]!!.sortedBy { it.top })),
                        )
                    }
                }
                pageLines.put(arr)
            }
            val o = JSONObject()
                .put("pages", doc.numberOfPages)
                .put("rules", JSONObject()
                    .put("bodySize", rules.bodySize).put("rubySize", rules.rubySize)
                    .put("pageNumSize", rules.pageNumSize).put("pageNumY", rules.pageNumY)
                    .put("rubyOffsetX", rules.rubyOffsetX).put("lineStepX", rules.lineStepX))
                .put("paras", paras).put("emitPage", emitPage).put("pageLines", pageLines)
            File(io.getString("out")).writeText(o.toString())
        }
    }
}
KT
python3 -c "
import sys
p,io=sys.argv[1],sys.argv[2]
open(p.replace('/harness.kt','/harness.out.kt'),'w').write(open(p).read().replace('@IO@', io))
" "$WORK/harness.kt" "$WORK/io.json"
cp "$WORK/harness.out.kt" "$HARNESS"

run_harness() {  # $1=入力PDF $2=出力JSON
  printf '{"pdf":"%s","out":"%s"}\n' "$1" "$2" > "$WORK/io.json"
  bash "$ROOT/tools/gwlock.sh" testDebugUnitTest --tests '*S3FixtureHarnessTest*' > "$WORK/gradle.log" 2>&1 \
    || { tail -40 "$WORK/gradle.log"; echo "ハーネス実行に失敗"; exit 1; }
}

echo "[1/4] 全文版を抽出"
run_harness "$SRC" "$WORK/full.json"

echo "[2/4] 第 $EP 話のページを抜いた fixture PDF を作る"
"$HOME/.local/bin/uv" run --quiet --with pypdf python "$ROOT/tools/build_s3_fixture.py" cut "$WORK" "$EP" "$SRC" "$OUTDIR"

echo "[3/4] ページ抜き版を抽出"
run_harness "$OUTDIR/N0833HI_ep$EP.pdf" "$WORK/sub.json"

echo "[4/4] 全文版との一致を検証し、オラクル fixture を書く"
python3 "$ROOT/tools/build_s3_fixture.py" verify "$WORK" "$EP" "$THIRD" "$OUTDIR"
