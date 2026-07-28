package com.novelreader.review

import android.app.Activity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException

/**
 * In-App Review（Play ストアの評価打診シート）の呼び出し口。
 *
 * なぜインターフェースで包むか: ReviewManager は Play 開発者サービス必須で JVM テストから実物を
 * 叩けない。トリガ条件（読了 reachedEnd false→true 遷移で一度だけ・同一セッション多重なし）は
 * BookshelfViewModel 側にあり、そちらをフェイクで JVM テストに固定する。実表示の確認は
 * 内部テストトラックで行う（本番はクォータ制限で確認困難）。
 */
interface ReviewPrompter {
    /**
     * レビュー打診を試みる。表示されるかは Play 側のクォータ管理が決める（表示保証なし）。
     * 呼び出し側は「満足のピーク（読了の瞬間）」でのみ呼ぶこと（事前質問・★5誘導・
     * 呼び出しボタンはガイドライン違反/非推奨のため作らない＝監督裁定）。
     */
    suspend fun promptReview(activity: Activity)
}

/** 本物の Play In-App Review フロー。sideload された debug ビルドでは自然に無表示の no-op になる。 */
class PlayReviewPrompter : ReviewPrompter {
    override suspend fun promptReview(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            // requestReview→launchReview は review-ktx の suspend 拡張（Task コールバックの入れ子を回避）。
            val reviewInfo = manager.requestReview()
            manager.launchReview(activity, reviewInfo)
        } catch (e: CancellationException) {
            throw e // 協調キャンセルは握らない（構造化並行性の規約）
        } catch (e: Exception) {
            // 意図した握り潰し（症状隠しではない）: In-App Review はベストエフォート機能で、
            // クォータ超過・Play サービス不在・非 Play 経路インストールでは ReviewException 等が
            // 「正常系」として飛ぶ。Google のガイドラインも失敗をユーザーへ知らせないことを求めて
            // おり、読了直後の満足の瞬間をエラー表示で汚さない（リカバリ手段も存在しない）。
        }
    }
}
