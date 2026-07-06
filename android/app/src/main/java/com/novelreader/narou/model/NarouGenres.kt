package com.novelreader.narou.model

object NarouGenres {
    /** 大ジャンル: code to 表示名（表示順） */
    val BIGGENRES: List<Pair<Int, String>> = listOf(
        1 to "恋愛",
        2 to "ファンタジー",
        3 to "文芸",
        4 to "SF",
        99 to "その他",
        98 to "ノンジャンル"
    )

    /** 大ジャンルcode → 詳細ジャンル(code to 表示名)リスト */
    val GENRES_BY_BIG: Map<Int, List<Pair<Int, String>>> = mapOf(
        1 to listOf(
            101 to "異世界〔恋愛〕",
            102 to "現実世界〔恋愛〕"
        ),
        2 to listOf(
            201 to "ハイファンタジー",
            202 to "ローファンタジー"
        ),
        3 to listOf(
            301 to "純文学",
            302 to "ヒューマンドラマ",
            303 to "歴史",
            304 to "推理",
            305 to "ホラー",
            306 to "アクション",
            307 to "コメディー"
        ),
        4 to listOf(
            401 to "VRゲーム",
            402 to "宇宙",
            403 to "空想科学",
            404 to "パニック"
        ),
        99 to listOf(
            9901 to "童話",
            9902 to "詩",
            9903 to "エッセイ",
            9904 to "リプレイ",
            9999 to "その他"
        ),
        98 to listOf(
            9801 to "ノンジャンル"
        )
    )

    private val GENRES_MAP: Map<Int, String> by lazy {
        GENRES_BY_BIG.values.flatten().toMap()
    }

    private val BIGGENRES_MAP: Map<Int, String> by lazy {
        BIGGENRES.toMap()
    }

    /**
     * 詳細ジャンルcode→表示名（null/未知は null）
     */
    fun genreLabel(code: Int?): String? {
        if (code == null) return null
        return GENRES_MAP[code]
    }

    /**
     * 大ジャンルcode→表示名（null/未知は null）
     */
    fun biggenreLabel(code: Int?): String? {
        if (code == null) return null
        return BIGGENRES_MAP[code]
    }
}
