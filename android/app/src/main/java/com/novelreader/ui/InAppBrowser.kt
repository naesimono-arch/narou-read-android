package com.novelreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/**
 * URL を Chrome Custom Tabs で「アプリ内オーバーレイ」表示する。外部ブラウザへ送客しない。
 *
 * なぜ外部ブラウザ(ACTION_VIEW)や WebView ではなく Custom Tabs か:
 * 「なるべくこのアプリで完結したい」方針を、WebView の保守負担・規約リスク
 * （本アプリは意図的に WebView を捨ててネイティブ化した経緯がある）を負わずに満たすため。
 * Custom Tabs は端末ブラウザのタブをアプリ上に重ねて表示し、ログイン状態も端末ブラウザと共有できる。
 *
 * @param toolbarColor 非null時はツールバーをその色に着色する。呼び出し画面のテーマ面色を渡すと、
 *                     外部サイトへ「飛ばされた」感が和らぎ没入を保てる。
 */
fun openInAppBrowser(context: Context, url: String, @ColorInt toolbarColor: Int? = null) {
    val builder = CustomTabsIntent.Builder().setShowTitle(true)
    if (toolbarColor != null) {
        builder.setDefaultColorSchemeParams(
            CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build()
        )
    }
    try {
        builder.build().launchUrl(context, Uri.parse(url))
    } catch (e: ActivityNotFoundException) {
        // なぜ握りつぶすか: Custom Tabs 対応ブラウザどころか通常ブラウザすら1つも無い端末では
        // launchUrl 内の startActivity が失敗する。表示不能は機能欠落だがクラッシュさせる理由は
        // ないため静かに無視する（ブラウザが1つでもあればそこで開かれ、ここには到達しない）。
    }
}
