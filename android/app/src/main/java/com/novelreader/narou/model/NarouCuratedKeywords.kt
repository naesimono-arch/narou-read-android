package com.novelreader.narou.model

// why: 語彙・分類はなろう公式検索ページ（yomou.syosetu.com/search.php）の「検索ワードを選択」
// パネルに準拠する（2026-07-07 取得・全数収載）。独自の抜粋をしないのは、ジャンル選択が公式分類
// 準拠で好評だった一方、独自抜粋の旧4分類は「中途半端に欠けた一覧は不足の方が記憶に残る」という
// 実機フィードバックを受けたため（入れるなら全部・入れないなら入れない、の前者を採る）。
data class CuratedKeywordCategory(val title: String, val words: List<String>)

object NarouCuratedKeywords {
    /** 公式「おすすめキーワード①-作品内容」（常時表示） */
    val basicCategories: List<CuratedKeywordCategory> = listOf(
        CuratedKeywordCategory("作品傾向", listOf("ギャグ", "シリアス", "ほのぼの", "ダーク")),
        CuratedKeywordCategory("登場キャラクター", listOf("男主人公", "女主人公", "人外", "魔王", "勇者")),
        CuratedKeywordCategory("舞台", listOf("和風", "西洋", "中華", "学園")),
        CuratedKeywordCategory("時代設定", listOf("戦国", "幕末", "明治/大正", "昭和", "平成", "古代", "中世", "近世", "近代", "現代", "未来")),
        CuratedKeywordCategory("要素", listOf("ロボット", "アンドロイド", "職業もの", "ハーレム", "逆ハーレム", "群像劇", "チート", "内政", "魔法", "冒険", "ミリタリー", "日常", "ハッピーエンド", "バッドエンド", "グルメ", "青春", "ゲーム", "超能力", "タイムトラベル", "ダンジョン", "パラレルワールド", "タイムリープ")),
    )

    /** 公式「おすすめキーワード②-ジャンル別」＋リプレイ用（既定は折りたたみ） */
    val genreCategories: List<CuratedKeywordCategory> = listOf(
        CuratedKeywordCategory("恋愛", listOf("異類婚姻譚", "身分差", "年の差", "悲恋")),
        CuratedKeywordCategory("異世界〔恋愛〕", listOf("ヒストリカル", "乙女ゲーム", "悪役令嬢")),
        CuratedKeywordCategory("現実世界〔恋愛〕", listOf("オフィスラブ", "スクールラブ", "古典恋愛")),
        CuratedKeywordCategory("ハイファンタジー", listOf("オリジナル戦記")),
        CuratedKeywordCategory("ローファンタジー", listOf("伝奇")),
        CuratedKeywordCategory("ヒューマンドラマ", listOf("ハードボイルド", "私小説", "ホームドラマ")),
        CuratedKeywordCategory("歴史", listOf("IF戦記", "史実", "時代小説", "逆行転生")),
        CuratedKeywordCategory("推理", listOf("ミステリー", "サスペンス", "探偵小説")),
        CuratedKeywordCategory("ホラー", listOf("スプラッタ", "怪談", "サイコホラー")),
        CuratedKeywordCategory("アクション", listOf("異能力バトル", "ヒーロー", "スパイ")),
        CuratedKeywordCategory("コメディー", listOf("ラブコメ")),
        CuratedKeywordCategory("SF", listOf("近未来", "人工知能", "電脳世界")),
        CuratedKeywordCategory("VRゲーム", listOf("VRMMO")),
        CuratedKeywordCategory("宇宙", listOf("スペースオペラ", "エイリアン")),
        CuratedKeywordCategory("空想科学", listOf("サイバーパンク", "スチームパンク", "ディストピア", "タイムマシン")),
        CuratedKeywordCategory("パニック", listOf("怪獣", "天災", "バイオハザード", "パンデミック")),
        CuratedKeywordCategory("リプレイ（TRPG）", listOf("SW2.0", "AR2E", "ダブルクロス3rd", "MGR", "グランクレスト", "ガーデンオーダー", "ナイトウィザード", "アルシャード", "NOVA", "dragonarms", "モノトーン", "BoA", "セブンフォートレス", "エースキラージーン", "MAG", "片道勇者", "アマデウス", "DLH", "ドラクルージュ", "コロッサルハンター", "スクハイ", "トワハイ", "ＴＮＭ", "アニマアニムス", "拳禅無双", "ルイブレ")),
    )

    /** 後方互換が要る場合のみ。既存参照は basic/genre へ置換するのでこの val は残さない。 */
}
