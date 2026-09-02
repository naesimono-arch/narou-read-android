package com.novelreader.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.novelreader.PrefKeys
import com.novelreader.ui.theme.NovelReaderAlertDialog
import com.novelreader.ui.theme.Spacing

/**
 * なろうの面に入ったことを名乗る注意喚起の**文言と抑止フラグの正本**（ADR 0042 決定1＝案A）。
 *
 * ## なぜ必要か（ADR 0042 背景）
 * アプリ内から「なろうで読む」を押すと、以後の面は**なろうが配信するページそのもの**になる。
 * アプリの表示設定もふりがな機能も効かず、なろうの表示がそのまま出る。この切り替わりを名乗るものが
 * どこにも無かった（トップバーの題「なろうで読む」だけ）。
 *
 * ## 文言の制約（勝手に書き換えないこと）
 * - **「広告」の語を名指ししない**。名指しした瞬間〈場所の案内〉が〈警告〉へ変質し、なろうの収益基盤を
 *   こちらが問題視しているかのように読める（ADR 0042 決定2・正本モックの裁定）。「なろうが配信する
 *   ページを、そのまま表示します」という言い方に一語で織り込む。
 * - **「外部サイトへ移動します」と書かない**＝移動しない（アプリ内 WebView のまま）＝嘘になる。
 *   境界を言う語は「**ここからは**」。
 * - **「ふりがなは付かない」と書かない**＝なろうのページには作者が付けたルビがそのまま出る。
 *   効かないのは**アプリ側**のふりがな機能なので、主語をアプリへ寄せる（ADR 0042 の ⚠️ 明示）。
 *   ⚠️ 同じ誤りが初回教示カード（`ui/intro/IntroDeck.kt`）にも残っている＝そちらの是正は別便。
 *
 * ## 抑止の意味論
 * [PrefKeys.NAROU_EXTERNAL_NOTICE_SUPPRESSED] が立つのは「次回から表示しない」を選んで閉じた時**だけ**。
 * チェックせずに閉じた回は焼かない——読み落とした人にもう一度届く余地を残すため（同型の先例＝
 * 電池最適化ダイアログの `PrefKeys.BATTERY_DIALOG_DISMISSED`）。
 */
internal object NarouExternalPageNotice {

    /** 見出し。移動ではなく**境界**を言う（正本モック W1）。 */
    const val TITLE = "ここからは小説家になろうのページです"

    /**
     * 本文。ADR 0042 決定2 の3要素を1文ずつで持つ＝
     * 〈なろうが配信する面である〉〈なろうの表示のまま出る〉〈アプリ側の機能は効かない〉。
     */
    const val BODY =
        "なろうが配信するページを、そのまま表示します。" +
            "アプリの操作・表示設定・ふりがな機能は効きません。"

    /** 抑止チェックのラベル（正本モック W1）。 */
    const val SUPPRESS_LABEL = "次回から表示しない"

    /** 確定ボタン。**1つだけ**＝引き返す選択肢を作らない（正本モック W1 の裁定）。 */
    const val CONFIRM_LABEL = "わかりました"

    /** まだ抑止されていない（＝この入場で出す）か。 */
    fun shouldShow(prefs: SharedPreferences): Boolean =
        !prefs.getBoolean(PrefKeys.NAROU_EXTERNAL_NOTICE_SUPPRESSED, false)

    /** 以後は出さない（ユーザーが「次回から表示しない」を選んだ時だけ呼ぶ）。 */
    fun suppress(prefs: SharedPreferences) {
        prefs.edit().putBoolean(PrefKeys.NAROU_EXTERNAL_NOTICE_SUPPRESSED, true).apply()
    }
}

/**
 * 注意喚起の器（出すか否かの判定・抑止の永続化・ダイアログ描画を1箇所に束ねたもの）。
 *
 * なぜ「入口ごと」でなくここで束ねるか: 「なろうで読む」の入口は作品詳細4・本棚Webカード2（＋K/P/M/J の
 * 各一覧面）と分散しているが、**アプリ内 WebView へはただ1つのルート `web-reader/{ncode}/{startEpisode}`
 * を通ってしか入れない**（`MainActivity` の grep で全数確認）。着地面に1つ置けば入口の全数を覆え、
 * 入口が増えても判定の分岐が増えない＝抜けが構造的に起きない。
 * 背後ではページの読み込みが走っているので、**待ち時間がそのまま説明の時間に化ける**（ADR 0042 決定1）。
 *
 * @param prefs 抑止フラグの置き場。既定はアプリ全設定の SharedPreferences（テストは差し替える）。
 */
@Composable
internal fun NarouExternalPageNoticeHost(prefs: SharedPreferences = rememberAppPrefs()) {
    // なぜ rememberSaveable か: 回転・ダーク切替で Activity が作り直されても「この入場では既に説明した」を
    // 持ち越し、同じ入場で二度出さない（保存が無いと構成変更のたびに再判定されて出戻る）。
    var visible by rememberSaveable { mutableStateOf(NarouExternalPageNotice.shouldShow(prefs)) }
    if (!visible) return
    NarouExternalPageNoticeDialog { dontShowAgain ->
        if (dontShowAgain) NarouExternalPageNotice.suppress(prefs)
        visible = false
    }
}

/**
 * 注意喚起ダイアログ本体（正本モック `narou-external-page-notice-candidates.html` 案A の翻訳）。
 *
 * 意匠は [NovelReaderAlertDialog]（＝`bookshelf-multiselect-D` の `.dlg` に対応する既存の器）へ委ねる。
 * 器を共有するのが翻訳として正しい理由: モックの案A は `.dlg` の**写経＝値同一**と明記されており、
 * その `.dlg` の Compose 翻訳は既にこのラッパである（削除確認・電池最適化などアプリ内の全ダイアログが同器）。
 * ここで独自の色・字面を積むと、同じ器のはずのダイアログだけが1枚ズレる。
 *
 * @param onAcknowledge 閉じたときに呼ばれる。引数は「次回から表示しない」の選択状態。
 */
@Composable
internal fun NarouExternalPageNoticeDialog(onAcknowledge: (dontShowAgain: Boolean) -> Unit) {
    var dontShowAgain by rememberSaveable { mutableStateOf(false) }
    NovelReaderAlertDialog(
        // スクリム外タップ・システム Back で閉じた場合も「わかりました」と同じ扱いにする。
        // なぜ同じか: この面には引き返す選択肢が無い（ボタンが1つだけ＝正本モックの裁定）ので、
        // 閉じ方の違いで意味が変わらない。チェックの意思も同じく汲む（閉じ方で抑止が効かないのは裏切り）。
        onDismissRequest = { onAcknowledge(dontShowAgain) },
        title = { Text(NarouExternalPageNotice.TITLE) },
        text = {
            Column {
                Text(NarouExternalPageNotice.BODY)
                Spacer(Modifier.height(Spacing.S16))
                // 行全体を toggleable にして枡とラベルのどちらでも切り替わる（Checkbox 自体は
                // onCheckedChange=null＝行のトグルへ委譲する Material 標準の a11y マージ形。
                // 同型の先例＝DeleteSourcePdfOption）。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = dontShowAgain,
                            role = Role.Checkbox,
                            onValueChange = { dontShowAgain = it },
                        ),
                ) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = null)
                    Spacer(Modifier.width(Spacing.S8))
                    Text(NarouExternalPageNotice.SUPPRESS_LABEL)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAcknowledge(dontShowAgain) }) {
                Text(NarouExternalPageNotice.CONFIRM_LABEL)
            }
        },
    )
}

/** アプリ全設定の SharedPreferences をコンポジション寿命で1度だけ取る（置き場の直書き回避＝PrefKeys 経由）。 */
@Composable
private fun rememberAppPrefs(): SharedPreferences {
    val context = LocalContext.current
    return remember(context) {
        context.getSharedPreferences(PrefKeys.FILE_APP_PREFS, Context.MODE_PRIVATE)
    }
}
