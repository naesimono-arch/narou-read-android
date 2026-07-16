package com.novelreader.typeset

/**
 * 縦書きで1文字（書記素）をどう置くかの分類。
 *
 * なぜ enum で持つか: 「どの字を回すか／寄せるか」の決定を純Kotlin層に集約し、
 * 描画層（P2 GlyphRenderer）は決定に従って実行するだけにする分業のため
 * （fontFeatureSettings="vert" の効きは書体依存で不安定＝P0-1 実測。判断を描画に委ねない）。
 */
enum class CharClass {
    /** 正立（そのまま縦に置く）。漢字・仮名・全角英数字・約物「？！・」など。 */
    UPRIGHT,

    /** 90度回転して置く。括弧・長音・ダッシュ類・半角英字（欧文横倒し）など。 */
    ROTATE,

    /** 位置替え（右上寄せ等）。句読点「、。，．」と小書き仮名。 */
    PUNCT_REPOSITION,

    /** 縦中横（横倒しにせず横並びの小組みを1マスに収める）。半角の数字・英字・!? の2〜3字連。 */
    TATE_CHU_YOKO,
}

/**
 * 書記素→CharClass の分類器（純関数・文脈非依存）。
 *
 * なぜ文脈非依存か: ここは「1字を単独で見たときの既定の向き」を返す層。
 * 縦中横のラン文脈（1桁は正立・2〜3桁は縦中横・4桁以上は各字回転）は
 * ラン長を知る VerticalTypesetter 側が上書きする。分類器は半角英数字を
 * 一律 ROTATE（欧文横倒しの安全既定）に倒し、ラン文脈が精緻化する二層構成。
 *
 * 表の正本: P0 スパイク実測（vertical-mode-p0-measurements-2026-07-17.md）＋ JIS 縦組み慣行。
 */
object CharClassifier {

    /** 小書き仮名（ひらがな・カタカナ）。縦書きでは右上へ位置替えする（P0-1: vert で字形変化を確認）。 */
    private const val SMALL_KANA =
        "ぁぃぅぇぉっゃゅょゎゕゖァィゥェォッャュョヮヵヶ"

    /** PUNCT_REPOSITION 対象＝句読点＋小書き仮名（右上寄せ系の位置替え）。 */
    private val PUNCT_REPOSITION_CHARS: Set<Char> = ("、。，．" + SMALL_KANA).toSet()

    /**
     * ROTATE 対象＝括弧18種＋長音/波/ダッシュ/約物の一部。
     * なぜここに「…‥；−」も入るか: P0-1 で vert が効かない（回してくれない）実測字＝
     * CharClassifier が回転判定を持つ必要がある（VertFeatureCoverage 参照）。
     */
    private val ROTATE_CHARS: Set<Char> = (
        "「」『』（）〔〕［］｛｝〈〉《》【】" +
            "ー～〜…‥—―‐–＝：；−｜"
        ).toSet()

    /**
     * 書記素1つを分類する。未知文字は UPRIGHT に倒す（防御的既定）。
     * なぜ UPRIGHT 既定か: 縦書きで向きが不明な字も、正立なら最悪でも判読できる
     * （回転や位置替えを誤ると欠字同然に見えるため、既定は最も安全な正立）。
     */
    fun classify(grapheme: String): CharClass {
        if (grapheme.isEmpty()) return CharClass.UPRIGHT
        // 単一 BMP 文字だけがクラス表の対象。サロゲートペア・結合文字（length>1）は正立既定へ。
        if (grapheme.length == 1) {
            val c = grapheme[0]
            // 半角英数字は文脈非依存では欧文横倒し＝ROTATE（数字のラン文脈は Typesetter が上書き）。
            if (c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z') return CharClass.ROTATE
            if (c in PUNCT_REPOSITION_CHARS) return CharClass.PUNCT_REPOSITION
            if (c in ROTATE_CHARS) return CharClass.ROTATE
        }
        return CharClass.UPRIGHT
    }
}

/**
 * fontFeatureSettings="vert" が効かず、描画層で明示的に90度回転が必須な字の実測リスト。
 *
 * なぜ別立てか: これらは CharClass 上は ROTATE だが、P0-1 実測で vert フィーチャが
 * 縦字形を出さない（「‥」は default/serif で書体割れ）。P2 の GlyphRenderer が
 * 「vert に任せず必ず自前回転する字」を判定するために参照する。
 * 実測根拠: docs/knowledge/vert-feature-pgem10-coverage.md（P0-1 の 130 計測）。
 */
object VertFeatureCoverage {
    /** vert が効かず自前回転が必須と実測された字（‥は書体割れ＝フォールバック必須の実証例）。 */
    val MANUAL_ROTATE_REQUIRED: Set<String> = setOf("…", "‥", "；", "−")
}
