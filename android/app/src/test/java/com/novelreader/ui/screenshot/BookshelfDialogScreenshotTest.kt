package com.novelreader.ui.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.novelreader.ui.screenshot.BookshelfDialogFixtures as Fx
import com.novelreader.ui.theme.NovelReaderAlertDialog
import com.novelreader.ui.theme.ReadingTheme
import com.novelreader.ui.theme.Skin
import com.novelreader.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 本棚ルート層のダイアログ群（[com.novelreader.ui.BookshelfScreen]）のスクリーンショット回帰。
 *
 * ## なぜ張るか（2026-08-07 の文言棚卸し）
 * 50字超のユーザー可視文言20件の**大半がこの6ダイアログに集中している**のに、`src/test/screenshots/` に
 * ダイアログの golden は**1枚も無かった**。長文が器に入っているのに fontScale 2.0 での破綻が一度も
 * 観測されていない状態＝「壊れていない」ではなく「**見ていない**」。
 * 実際、本テスト導入時の probe で電池最適化ダイアログ（当時の本文124字＝全文言の最長）を fontScale 2.0 で撮ったところ、
 * 本文が器の高さを超えて**チェックボックス行と重なり、末尾の一段落ごと切り落とされていた**
 * （M3 `AlertDialog` の text スロットは自動スクロールしない）。この破綻は撮っていない条件では
 * 原理的に見えない（ADR 0009 増補1 が screenshot の目的に明記している軸）。
 *
 * ## 撮り方（Dialog 単体・ホスト画面ごとではない）
 * 機序と前例は [captureDialogSkinned] の KDoc が正本（要約: ①`LocalDensity` 上書きはダイアログ窓に
 * 届かないので Configuration の fontScale を動かす ②`onRoot()` でなく `isDialog()` で窓を名指しする
 * ③ホストごと撮るには実 VM と UI 操作が要り、撮りたい版面と無関係な配線で折れる）。
 * 文言はテスト側の写しになるため、本番との一致は [BookshelfDialogTextFidelityTest] が機械照合する。
 *
 * ## スキンは K 固定
 * ADR 0027 で初回公開スコープは明快K 単独＝出荷する唯一の面。ダイアログ本文色は
 * `NovelReaderAlertDialog` が `LocalShelfColors.infoText` から引き、面の色は `SkinContainerTiers` が
 * スキン別に供給する＝**撮る対象と同じ入口で包む**（KScreenshotSupport と同じ流儀）。
 *
 * ## 撮る case と、撮らない case
 * 実装の全ダイアログ（8種）を「器の構造」で束ね、束ごとに**その器で最も割れやすいもの**を代表に選ぶ
 * （同型で文言だけ違うものを全部撮っても新しい破綻は出ないため）。
 *  - `battery`（**代表**・3テーマ×2スケール全数）: 器は**「本文＋チェックボックス行」を併せ持つ唯一の型**
 *    ＝溢れた本文がチェック行と重なる経路（上の probe で実際に割れた形）を持つのはこの1種だけ。
 *    2026-08-07 の短縮で本文は124→78字になり最長ではなくなった（最長は下の `notif_priming` 80字）が、
 *    代表の根拠は長さではなく器の形なのでここは動かさない。
 *  - `notif_priming`: 単一 Text 本文の最長（80字）。器は「題字＋本文＋2ボタン」の素朴形＝
 *    上書き確認・再取込①①'④・指紋なし分岐（いずれも本文61字以下）を代表する。
 *  - `import_prompt`: **ボタン3個**（dismissButton が Row で2個を抱える）＋確定ラベルが動的で長い形。
 *  - `reimport_scan`: 「本文＋補助行（bodySmall のファイル名ヒント）」＋ボタン3個。
 * 追加 case をライト×2スケールに限るのは既存の張り方（BookshelfD/K）と同じ理由＝
 * 色トークンは代表 case が張り、追加 case は骨格（拡大破綻）だけを見る。
 *
 * ⚠️ **撮れていない2種**（一括再取込 `showSweepDialog`／走査結果 `folderScanReport`）:
 * どちらも本文が `private @Composable ReimportBreakdownRow`（BookshelfScreen.kt のファイル private）で
 * 組まれており、テストから呼べない。行を再実装して撮ると golden が守るのは**テスト内の写しの版面**に
 * なり、本番の内訳行が壊れても緑のまま通る＝この束を張る意味が消える。
 * 撮るには本番側で `ReimportBreakdownRow` を `internal` へ開く必要があり、本便の所有範囲外
 * （src/test のみ）のため見送った。登録簿にも同じ理由を残してある。
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class BookshelfDialogScreenshotTest(
    private val caseId: String,
    private val theme: ReadingTheme,
    private val fontScale: Float,
) {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture() {
        composeTestRule.captureDialogSkinned(
            skin = Skin.MEIKAI_K,
            theme = theme,
            fontScale = fontScale,
            fileName = goldenName("BookshelfDialogK", caseId, theme, fontScale),
        ) {
            when (caseId) {
                CASE_NOTIF -> NotifPrimingDialog()
                CASE_IMPORT -> ImportPromptDialog()
                CASE_REIMPORT -> ReimportScanDialog()
                else -> BatteryOptDialog()
            }
        }
    }

    // ---- 各ダイアログの写し（本番 BookshelfScreen.kt の当該ブロックと1:1）--------------------
    // onDismissRequest / onClick は撮影に無関係なので no-op。押下後の遷移でなく**版面**を撮る束のため。

    /** BookshelfScreen.kt の `if (showBatteryOptDialog)` ブロック。 */
    @Composable
    private fun BatteryOptDialog() {
        NovelReaderAlertDialog(
            onDismissRequest = {},
            title = { Text(Fx.BATTERY_TITLE) },
            text = {
                Column {
                    Text(Fx.BATTERY_BODY)
                    Spacer(Modifier.height(Spacing.S8))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 既定 OFF＝本番の初期状態（doNotShowAgain の初期値）と同じ。
                        Checkbox(checked = false, onCheckedChange = {})
                        Text(Fx.BATTERY_CHECK, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = {}) { Text(Fx.BATTERY_CONFIRM) } },
            dismissButton = { TextButton(onClick = {}) { Text(Fx.BATTERY_DISMISS) } },
        )
    }

    /** BookshelfScreen.kt の `if (showNotifPriming)` ブロック。 */
    @Composable
    private fun NotifPrimingDialog() {
        NovelReaderAlertDialog(
            onDismissRequest = {},
            title = { Text(Fx.NOTIF_TITLE) },
            text = { Text(Fx.NOTIF_BODY) },
            confirmButton = { TextButton(onClick = {}) { Text(Fx.NOTIF_CONFIRM) } },
            dismissButton = { TextButton(onClick = {}) { Text(Fx.NOTIF_DISMISS) } },
        )
    }

    /**
     * BookshelfScreen.kt の `importPrompt?.let` ブロック（なろう形式が1件以上ある側の分岐）。
     * 件数は golden を安定させるため固定（12件中5件が非なろう＝なろう7件）。
     */
    @Composable
    private fun ImportPromptDialog() {
        NovelReaderAlertDialog(
            onDismissRequest = {},
            title = { Text(Fx.IMPORT_TITLE) },
            text = { Text(Fx.IMPORT_BODY) },
            confirmButton = { TextButton(onClick = {}) { Text(Fx.IMPORT_CONFIRM) } },
            dismissButton = {
                Row {
                    TextButton(onClick = {}) { Text(Fx.IMPORT_ALL) }
                    TextButton(onClick = {}) { Text(Fx.IMPORT_CANCEL) }
                }
            },
        )
    }

    /**
     * BookshelfScreen.kt の `ReimportPlan.PickPdf*` × `scanSha256 != null` 分岐。
     * 場所**未記憶**（`pdfFolderTreeUri == null`）側の長い方の文言＋取込元ヒント行ありで撮る
     * ＝この分岐で最も縦に伸びる組合せ。
     */
    @Composable
    private fun ReimportScanDialog() {
        NovelReaderAlertDialog(
            onDismissRequest = {},
            title = { Text(Fx.REIMPORT_TITLE) },
            text = {
                Column {
                    Text(Fx.REIMPORT_BODY)
                    Spacer(Modifier.height(Spacing.S12))
                    Text(
                        Fx.REIMPORT_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = {}) { Text(Fx.REIMPORT_CONFIRM) } },
            dismissButton = {
                Row {
                    TextButton(onClick = {}) { Text(Fx.REIMPORT_PICK) }
                    TextButton(onClick = {}) { Text(Fx.REIMPORT_DISMISS) }
                }
            },
        )
    }

    companion object {
        /** 代表 case＝全文言の最長（124字）＋チェックボックス行。テーマ×スケール全数を張る。 */
        private const val CASE_BATTERY = "battery"
        private const val CASE_NOTIF = "notif_priming"
        private const val CASE_IMPORT = "import_prompt"
        private const val CASE_REIMPORT = "reimport_scan"

        @JvmStatic
        @Parameters(name = "{0}_{1}_scale{2}")
        fun data(): List<Array<Any>> = buildList {
            // 代表 case＝全テーマ×全スケール（テーマ退行と拡大破綻の両軸）。
            ScreenshotConfig.THEMES.forEach { t ->
                ScreenshotConfig.FONT_SCALES.forEach { s -> add(arrayOf<Any>(CASE_BATTERY, t, s)) }
            }
            // 追加 case＝ライトのみ×2スケール（器の骨格＝拡大破綻の検知に限定）。
            ScreenshotConfig.FONT_SCALES.forEach { s ->
                add(arrayOf<Any>(CASE_NOTIF, ReadingTheme.LIGHT, s))
                add(arrayOf<Any>(CASE_IMPORT, ReadingTheme.LIGHT, s))
                add(arrayOf<Any>(CASE_REIMPORT, ReadingTheme.LIGHT, s))
            }
        }
    }
}
