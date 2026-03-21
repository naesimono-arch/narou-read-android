"""
pdf_extractor.py (Android版)
Phase 00-02: PDFから書籍タイトルと本文を抽出するモジュール
pdfminer.six に移行済み（PyMuPDF / fitz を使用しない純Python実装）
"""
import math
from pdfminer.high_level import extract_pages
from pdfminer.layout import LTChar, LTAnno
from pdfminer.pdfpage import PDFPage
import pdf_rules


def _iter_chars_from_page(layout_page, page_height):
    """pdfminer の LTPage から PyMuPDF rawdict 互換の文字 dict を生成する。
    Y軸反転: pdfminer は下原点、PyMuPDF 互換に上原点へ変換。
    """
    def _recurse(element):
        if isinstance(element, LTChar):
            yield {
                "text":     element.get_text(),
                "fontname": element.fontname,
                "size":     element.size,
                "x0":       element.x0,
                "top":      page_height - element.y1,
                "bottom":   page_height - element.y0,
            }
        elif isinstance(element, LTAnno):
            pass  # 合字/スペースはスキップ
        elif hasattr(element, "__iter__"):
            for child in element:
                yield from _recurse(child)
    yield from _recurse(layout_page)


# ==========================================
# 【Phase 00】 表紙から書籍タイトルを抽出
# ==========================================
def extract_book_title(pdf_path):
    """1ページ目の最も大きな文字列を、X左→右の順で結合してタイトルとする。"""
    pages = list(extract_pages(pdf_path, page_numbers=[0]))
    if not pages:
        return "不明なタイトル"
    page = pages[0]
    chars = list(_iter_chars_from_page(page, page.height))

    if not chars:
        return "不明なタイトル"

    max_size = max(c["size"] for c in chars)
    title_chars = [c for c in chars if math.isclose(c["size"], max_size, abs_tol=0.1)]
    title_chars.sort(key=lambda c: (c["top"], c["x0"]))
    title = "".join(c["text"] for c in title_chars).strip()
    return title if title else "無題の作品"


# ==========================================
# 【Phase 01-02】 抽出・整形エンジン
# ==========================================
def run_final_engine(pdf_path_override, progress_callback=None):
    """PDFから本文を抽出する。"""
    path_to_use = pdf_path_override
    all_paragraphs = []
    current_paragraph = ""

    # ページ数を取得（最後のページを除外するため）
    with open(path_to_use, "rb") as f:
        total_pages = sum(1 for _ in PDFPage.get_pages(f))

    body_total = max(total_pages - 4, 1)

    # 最初の3ページ（表紙・注意事項）と最後の1ページ（クレジット）を除外
    for page_num, page in enumerate(extract_pages(path_to_use)):
        if page_num < 3 or page_num >= total_pages - 1:
            continue

        if progress_callback is not None:
            processed = page_num - 3
            pct = 10 + int(processed / body_total * 50)
            progress_callback(pct, processed, body_total)

        titles_all = []
        bodies_all = []
        rubies_all = []

        for c in _iter_chars_from_page(page, page.height):
            fontname = c["fontname"]
            fontsize = c["size"]
            y_pos   = c["top"]

            # ① ページ数の除外フィルター
            if math.isclose(fontsize, pdf_rules.FONT_SIZE_PAGE, abs_tol=pdf_rules.TOLERANCE):
                if (math.isclose(y_pos, pdf_rules.PAGE_NUM_Y, abs_tol=5.0) or
                        math.isclose(c["bottom"], pdf_rules.PAGE_NUM_Y, abs_tol=5.0)):
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
            titles_all.sort(key=lambda c: (-c["x0"], c["top"]))
            title_text = "".join(
                c["text"] for c in titles_all if c["text"] not in [" ", "\n", "\r"]
            )
            if title_text:
                if current_paragraph:
                    all_paragraphs.append(current_paragraph)
                    current_paragraph = ""
                all_paragraphs.append(f"【題名】{title_text}")

        # 本文のソート（X降順・Y昇順）
        bodies_all.sort(key=lambda c: (-c["x0"], c["top"]))

        # 本文をX座標（行）ごとに仕分ける
        lines_dict = {}
        for c in bodies_all:
            x_val = c["x0"]
            matched_key = None
            for k in lines_dict:
                if math.isclose(x_val, k, abs_tol=pdf_rules.TOLERANCE):
                    matched_key = k
                    break
            if matched_key is not None:
                lines_dict[matched_key].append(c)
            else:
                lines_dict[x_val] = [c]

        # ルビを親文字に紐付ける
        for r in rubies_all:
            target_x = r["x0"] - pdf_rules.RUBY_OFFSET_X
            matched_line_key = None
            for x_key in lines_dict:
                if math.isclose(x_key, target_x, abs_tol=pdf_rules.TOLERANCE):
                    matched_line_key = x_key
                    break

            if matched_line_key is not None:
                target_line = lines_dict[matched_line_key]
                best_match_char = None
                min_distance = float("inf")
                for bc in target_line:
                    dist = abs(bc["top"] - r["top"])
                    if dist < min_distance:
                        min_distance = dist
                        best_match_char = bc
                if best_match_char is not None:
                    if "ruby_text" not in best_match_char:
                        best_match_char["ruby_text"] = ""
                    best_match_char["ruby_text"] += r["text"]

        # 右の行から順にテキスト化 ＆ 段落の縫合
        lines_sorted_x = sorted(lines_dict.keys(), reverse=True)
        prev_x = None

        for x in lines_sorted_x:
            line_bodies = sorted(lines_dict[x], key=lambda c: c["top"])
            line_str = ""

            j = 0
            while j < len(line_bodies):
                bc = line_bodies[j]
                char_text = bc.get("text", "")
                if char_text in [" ", "\n", "\r", "\t", "\xa0"]:
                    j += 1
                    continue

                ruby_text = bc.get("ruby_text")
                if ruby_text:
                    base_run = ""
                    ruby_run = ""
                    while j < len(line_bodies):
                        bc2 = line_bodies[j]
                        t2 = bc2.get("text", "")
                        if t2 in [" ", "\n", "\r", "\t", "\xa0"]:
                            j += 1
                            continue
                        r2 = bc2.get("ruby_text")
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

            if line_str.startswith("　") or line_str.startswith("「") or \
               line_str.startswith("『") or line_str.startswith("（"):
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

    final_output = []
    for p in all_paragraphs:
        if p == "":
            final_output.append("")
        else:
            cleaned = p.strip(" \t\n\r")
            if cleaned:
                final_output.append(cleaned)

    return final_output
