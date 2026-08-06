package com.novelreader.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// ============================================================
// reduce-motion（端末の「アニメーションを削除」／開発者向け設定のアニメータ無効／省電力）の**単一情報源**。
//
// なぜ 1 ファイルへ集約したか（監査 2026-08-06 C2 の真因）: 判定式
// `ANIMATOR_DURATION_SCALE == 0` が 8 箇所へ複製され、そのすべてが**キー無しの `remember`** で
// 初回コンポーズの値を固定していた。この設定の変更は構成変更を伴わない＝Activity は再生成されず、
// 各所の `remember` は二度と再評価されない。結果、**支援を必要とする人が設定を ON にしても
// プロセスを殺すまでアニメが止まらない**（OFF→ON 方向が届かない）。
// 1 箇所だけ直しても他 7 箇所に同じ凍結が残るため、判定式・購読・配布をここへ寄せた。
// ============================================================

/**
 * 端末設定から reduce-motion を読む純粋読み取り。**判定式を持つのはこの関数だけ**（各画面は直接読まない）。
 *
 * `ANIMATOR_DURATION_SCALE == 0` を採るのは、Android にモーション低減の専用 API が無く、
 * 「アニメーションを削除（ユーザー補助）」も省電力もこの Global 設定を 0 へ倒すため。
 */
fun readReduceMotion(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * 上流（MainActivity）が購読して全画面へ配る reduce-motion。
 *
 * `null`（未提供）＝**画面を単体でコンポーズしている場合**（Robolectric/golden/Preview）で、
 * このとき [rememberReduceMotion] は自前で live 購読へフォールバックする＝単体でも設定が効く。
 * 既定を `false` にしないのは、「未提供」と「提供された false」を区別しないとフォールバックを判断できないため。
 */
val LocalReduceMotion = compositionLocalOf<Boolean?> { null }

/**
 * reduce-motion の現在値。設定変更が**その場で**反映される（ContentObserver 購読）。
 *
 * 上流で [LocalReduceMotion] が提供されていればそれを読む＝アプリ実行時は購読が root の 1 本だけになり、
 * 本棚グリッドのようにカードが多数並ぶ画面でも ContentObserver が枚数ぶん増殖しない。
 */
@Composable
fun rememberReduceMotion(): Boolean = LocalReduceMotion.current ?: rememberLiveReduceMotion().value

/**
 * 端末設定を直接購読する live な reduce-motion。root（MainActivity）と、単体コンポーズ時のフォールバック用。
 *
 * ContentObserver で購読するのは、この設定の変更が構成変更を伴わない＝再コンポーズも Activity 再生成も
 * 起こさないため。購読しない限り値は初回コンポーズの写しのまま凍る（＝監査 C2 の真因そのもの）。
 */
@Composable
private fun rememberLiveReduceMotion(): State<Boolean> {
    val resolver = LocalContext.current.contentResolver
    val state = remember(resolver) { mutableStateOf(readReduceMotion(resolver)) }
    DisposableEffect(resolver) {
        // 登録直後に読み直す: コンポーズ〜購読開始の隙間、およびこの composable が居なかった間の変更を取りこぼさない。
        state.value = readReduceMotion(resolver)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = readReduceMotion(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return state
}
