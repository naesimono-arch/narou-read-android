"""
pdf_extractor.py
Phase 00-02: PDFから書籍タイトルと本文を抽出するモジュール
"""
import math
import pdfplumber
import pdf_rules


# ==========================================
# 【Phase 00】 表紙から書籍タイトルを抽出
# ==========================================
def extract_book_title(pdf_path):
    """1ページ目の最も大きな文字列を、X左→右の順で結合してタイトルとする。"""
    with pdfplumber.open(pdf_path) as pdf:
        first_page = pdf.pages[0]
        chars = first_page.chars

        if not chars:
            return "不明なタイトル"

        max_size = max(c["size"] for c in chars)
        title_chars = [c for c in chars if math.isclose(c["size"], max_size, abs_tol=0.1)]
        title_chars.sort(key=lambda c: (c["top"], c["x0"]))
        title = "".join([c["text"] for c in title_chars]).strip()
        return title if title else "無題の作品"


# ==========================================
# 【Phase 01-02】 抽出・整形エンジン
# ==========================================
def run_final_engine(pdf_path_override=None, _default_pdf_path="N6169DZ.pdf"):
    """PDFから本文を抽出。pdf_path_override が None の場合はデフォルトパスを使用。"""
    path_to_use = pdf_path_override if pdf_path_override is not None else _default_pdf_path
    all_paragraphs = []
    current_paragraph = ""

    with pdfplumber.open(path_to_use) as pdf:
        # 最初の3ページ（表紙・注意事項）と最後の1ページ（クレジット）を除外
        for page_num in range(3, len(pdf.pages) - 1):
            page = pdf.pages[page_num]
            chars = page.chars

            titles_all = []
            bodies_all = []
            rubies_all = []

            # 文字を1つずつチェックし、「題名」「本文」「ルビ」「ページ数」に完全分離
            for c in chars:
                fontname = c.get('fontname', '')
                fontsize = c['size']
                y_pos = c['top']

                # ① ページ数の除外フィルター
                if math.isclose(fontsize, pdf_rules.FONT_SIZE_PAGE, abs_tol=pdf_rules.TOLERANCE):
                    if math.isclose(y_pos, pdf_rules.PAGE_NUM_Y, abs_tol=5.0) or math.isclose(c['bottom'], pdf_rules.PAGE_NUM_Y, abs_tol=5.0):
                        continue

                # ② 題名の分離フィルター（Bold判定）
                if pdf_rules.check_is_title(fontname, fontsize):
                    titles_all.append(c)

                # ③ 本文の分離フィルター
                elif math.isclose(fontsize, pdf_rules.FONT_SIZE_BODY_TITLE, abs_tol=pdf_rules.TOLERANCE):
                    bodies_all.append(c)

                # ④ ルビの分離フィルター
                elif math.isclose(fontsize, pdf_rules.FONT_SIZE_RUBY, abs_tol=pdf_rules.TOLERANCE):
                    rubies_all.append(c)

            # 題名のテキスト化処理
            if titles_all:
                titles_all.sort(key=lambda c: (-c['x0'], c['top']))
                title_text = "".join([c['text'] for c in titles_all if c['text'] not in [' ', '\n', '\r']])

                if title_text:
                    if current_paragraph:
                        all_paragraphs.append(current_paragraph)
                        current_paragraph = ""
                    all_paragraphs.append(f"【題名】{title_text}")

            # 本文のソート（X降順・Y昇順）
            bodies_all.sort(key=lambda c: (-c['x0'], c['top']))

            # 本文をX座標（行）ごとに仕分ける
            lines_dict = {}
            for c in bodies_all:
                x_val = c['x0']
                matched_key = None
                for k in lines_dict.keys():
                    if math.isclose(x_val, k, abs_tol=pdf_rules.TOLERANCE):
                        matched_key = k
                        break
                if matched_key:
                    lines_dict[matched_key].append(c)
                else:
                    lines_dict[x_val] = [c]

            # ルビを親文字に紐付ける
            for r in rubies_all:
                target_x = r['x0'] - pdf_rules.RUBY_OFFSET_X
                matched_line_key = None
                for x_key in lines_dict.keys():
                    if math.isclose(x_key, target_x, abs_tol=pdf_rules.TOLERANCE):
                        matched_line_key = x_key
                        break

                if matched_line_key:
                    target_line = lines_dict[matched_line_key]
                    best_match_char = None
                    min_distance = float('inf')
                    for bc in target_line:
                        dist = abs(bc['top'] - r['top'])
                        if dist < min_distance:
                            min_distance = dist
                            best_match_char = bc

                    if best_match_char:
                        if 'ruby_text' not in best_match_char:
                            best_match_char['ruby_text'] = ""
                        best_match_char['ruby_text'] += r['text']

            # 右の行から順にテキスト化 ＆ 段落の縫合
            lines_sorted_x = sorted(lines_dict.keys(), reverse=True)
            prev_x = None

            for x in lines_sorted_x:
                line_bodies = sorted(lines_dict[x], key=lambda c: c['top'])
                line_str = ""

                # ルビ対象の親文字の開始位置に必ず '|' を付与し、
                # HTML化では「|...《...》」だけを <ruby> に変換する。
                j = 0
                while j < len(line_bodies):
                    bc = line_bodies[j]
                    char_text = bc.get('text', '')
                    if char_text in [' ', '\n', '\r', '\t', '\xa0']:
                        j += 1
                        continue

                    ruby_text = bc.get('ruby_text')
                    if ruby_text:
                        base_run = ""
                        ruby_run = ""
                        while j < len(line_bodies):
                            bc2 = line_bodies[j]
                            t2 = bc2.get('text', '')
                            if t2 in [' ', '\n', '\r', '\t', '\xa0']:
                                j += 1
                                continue
                            r2 = bc2.get('ruby_text')
                            if not r2:
                                break
                            base_run += t2
                            ruby_run += r2
                            j += 1
                        if base_run and ruby_run:
                            line_str += f"|{base_run}《{ruby_run}》"
                        else:
                            line_str += char_text
                            j += 1
                    else:
                        line_str += char_text
                        j += 1

                if not line_str:
                    continue

                is_new_paragraph = False
                blank_line_count = 0

                if line_str.startswith('　') or line_str.startswith('「') or line_str.startswith('『') or line_str.startswith('（'):
                    is_new_paragraph = True

                if prev_x is not None:
                    diff_x = prev_x - x
                    if diff_x > (pdf_rules.LINE_STEP_X * 1.5):
                        is_new_paragraph = True
                        blank_line_count = round(diff_x / pdf_rules.LINE_STEP_X) - 1

                if is_new_paragraph:
                    if current_paragraph:
                        all_paragraphs.append(current_paragraph)
                    for _ in range(max(0, blank_line_count)):
                        all_paragraphs.append("")
                    current_paragraph = line_str
                else:
                    current_paragraph += line_str

                prev_x = x

    if current_paragraph:
        all_paragraphs.append(current_paragraph)

    # 意図的な空行（""）は消さずに保持する
    final_output = []
    for p in all_paragraphs:
        if p == "":
            final_output.append("")
        else:
            cleaned = p.strip(' \t\n\r')
            if cleaned:
                final_output.append(cleaned)

    return final_output
