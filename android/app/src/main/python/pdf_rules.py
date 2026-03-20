import math

# ==========================================
# なろう縦書きPDF 解析用 定義（設定値）
# ==========================================

# 1. フォントサイズとフォント名に関する設定
FONT_SIZE_BODY_TITLE = 14.0  # 題名と本文のフォントサイズ
FONT_SIZE_RUBY = 7.0         # ルビのフォントサイズ
FONT_SIZE_PAGE = 12.0        # ページ数のフォントサイズ
FONT_MARKER_TITLE = "Bold"   # 題名を判定するための目印（フォント名に含まれるか）

# 2. ページ数に関する座標設定
PAGE_NUM_Y = 528.98          # ページ数のY座標（固定）

# 3. ルビに関する設定
RUBY_OFFSET_X = 14.84        # 親文字のX座標に対するルビのX座標のズレ（+14.84）

# 4. 行間の設定
LINE_STEP_X = 22.68          # 1行あたりのX座標の移動量（空行計算用）

# 5. 開始位置（Y座標）に関する設定
START_Y_BODY = 83.36         # 本文の通常の開始Y座標
START_Y_TITLE = 97.33        # 題名特有の開始Y座標

# 注意：数値比較用の許容誤差
TOLERANCE = 0.1              # math.isclose で使用する誤差範囲


def check_is_title(fontname, fontsize):
    if fontname is not None and FONT_MARKER_TITLE in fontname and \
            math.isclose(fontsize, FONT_SIZE_BODY_TITLE, abs_tol=TOLERANCE):
        return True
    return False
