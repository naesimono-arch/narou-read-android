import pdfplumber
import math
import pdf_rules
import re

pdf_path = "N2959KI.pdf"

print("--- 精密抽出開始 ---")
def run_final_engine():
    all_paragraphs = []
    current_paragraph = ""
    
    with pdfplumber.open(pdf_path) as pdf:
        # ★【修正点3】最後の1ページ（クレジット）を除外するため、 - 1 を追加！
        for page_num in range(3, len(pdf.pages) - 1):
            page = pdf.pages[page_num]
            chars = page.chars
            
            titles_all = []
            bodies_all = []
            rubies_all = []
            
            # ★【修正点1＆2】文字を1つずつチェックし、「題名」「本文」「ルビ」に完全分離
            for c in chars:
                fontname = c.get('fontname', '')
                fontsize = c['size']
                y_pos = c['top'] # 文字の縦の位置
                
                # ① ページ数の除外フィルター（要件②の適用）
                if math.isclose(fontsize, pdf_rules.FONT_SIZE_PAGE, abs_tol=pdf_rules.TOLERANCE):
                    # フォントサイズが12で、かつY座標が指定の位置（下部）付近なら「ゴミ箱」へ捨てる
                    if math.isclose(y_pos, pdf_rules.PAGE_NUM_Y, abs_tol=5.0) or math.isclose(c['bottom'], pdf_rules.PAGE_NUM_Y, abs_tol=5.0):
                        continue # continueは「この文字は無視して次へ行く」という指示です
                
                # ② 題名の分離フィルター（要件①の適用）
                if pdf_rules.check_is_title(fontname, fontsize):
                    titles_all.append(c)
                
                # ③ 本文の分離フィルター
                elif math.isclose(fontsize, pdf_rules.FONT_SIZE_BODY_TITLE, abs_tol=pdf_rules.TOLERANCE):
                    bodies_all.append(c)
                
                # ④ ルビの分離フィルター
                elif math.isclose(fontsize, pdf_rules.FONT_SIZE_RUBY, abs_tol=pdf_rules.TOLERANCE):
                    rubies_all.append(c)
            
            # ---------------------------------------------------------
            # ★【新機能】題名（タイトル）のテキスト化処理
            if titles_all:
                # 題名もX降順・Y昇順で並び替える
                titles_all.sort(key=lambda c: (-c['x0'], c['top']))
                title_text = "".join([c['text'] for c in titles_all if c['text'] not in [' ', '\n', '\r']])
                
                if title_text:
                    # もし前のページの本文が作りかけで残っていたら、先に保存しておく
                    if current_paragraph:
                        all_paragraphs.append(current_paragraph)
                        current_paragraph = ""
                    # 題名を「特別な見出し」としてリストに追加する
                    all_paragraphs.append(f"【題名】{title_text}")
            # ---------------------------------------------------------

            # 本文のソート（X降順・Y昇順）
            bodies_all.sort(key=lambda c: (-c['x0'], c['top']))
            
            # 「本文だけ」をX座標（行）ごとに仕分ける
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

            # 右の行から順にテキスト化 ＆ 【段落の縫合】
            lines_sorted_x = sorted(lines_dict.keys(), reverse=True)
            prev_x = None

            for x in lines_sorted_x:
                line_bodies = sorted(lines_dict[x], key=lambda c: c['top'])
                line_str = ""
                
                for bc in line_bodies:
                    char_text = bc['text']
                    if char_text in [' ', '\n', '\r', '\t', '\xa0']:
                        continue
                        
                    line_str += char_text
                    if 'ruby_text' in bc:
                        line_str += f"《{bc['ruby_text']}》"
                
                if not line_str:
                    continue
                    
                is_new_paragraph = False
                blank_line_count = 0 # ★追加：空行の数を数える変数
                
                if line_str.startswith('　') or line_str.startswith('「') or line_str.startswith('『') or line_str.startswith('（'):
                    is_new_paragraph = True
                
                # ★追加：座標の差分から「空行の数」を計算する
                if prev_x is not None:
                    diff_x = prev_x - x
                    if diff_x > (pdf_rules.LINE_STEP_X * 1.5):
                        is_new_paragraph = True
                        # 何行分飛んだかを計算（例：2行分飛んでいたら1行の空行）
                        blank_line_count = round(diff_x / pdf_rules.LINE_STEP_X) - 1

                if is_new_paragraph:
                    if current_paragraph:
                        all_paragraphs.append(current_paragraph)
                    
                    # ★追加：計算した数だけ意図的に「空の行」を入れる
                    for _ in range(max(0, blank_line_count)):
                        all_paragraphs.append("")
                        
                    current_paragraph = line_str
                else:
                    current_paragraph += line_str
                
                prev_x = x

    if current_paragraph:
        all_paragraphs.append(current_paragraph)

    # 【ルビの結合（フュージョン）】
    for i in range(len(all_paragraphs)):
        p = all_paragraphs[i]
        
        # ★追加：空行の時はルビの処理をしない（エラー防止）
        if p == "":
            continue
            
        while re.search(r'《([^》]+)》([^\s《》]{1,3})《([^》]+)》', p):
            p = re.sub(r'《([^》]+)》([^\s《》]{1,3})《([^》]+)》', r'\2《\1\3》', p)
        
        p = re.sub(r'(」|』|）)《([^》]+)》', r'《\2》\1', p)
        all_paragraphs[i] = p

    # ★変更：意図的な空行（""）は消さずにリストに残す
    final_output = []
    for p in all_paragraphs:
        if p == "":
            final_output.append("")
        else:
            cleaned = p.strip(' \t\n\r')
            if cleaned:
                final_output.append(cleaned)
                
    return final_output

# 実行
final_paragraphs = run_final_engine()

# ★変更：黒い画面に文字を出すだけでなく、テキストファイルとして保存します
print("--- 最終抽出結果を output_test.txt に保存します ---")
with open("output_test.txt", "w", encoding="utf-8") as f:
    for p in final_paragraphs:
        f.write(p + "\n")

print("保存完了！ Cursorの左側から output_test.txt を開いてみてください。")