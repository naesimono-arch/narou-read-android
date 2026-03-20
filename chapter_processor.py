"""
chapter_processor.py
Phase 03-03B: 段落リストを話数・前書き・後書きに分割・整形するモジュール
"""
import re


# ==========================================
# 【Phase 03】 話数の自動分割ロジック
# ==========================================
def split_into_chapters(paragraphs):
    chapters = []
    current_title = "作品情報・プロローグ"
    current_body = []

    for p in paragraphs:
        if p.startswith("【題名】"):
            # 後書きが太字で題名扱いされた場合の安全装置
            if "後書き" in p:
                current_body.append(p.replace("【題名】", ""))
                continue

            if current_body:
                chapters.append({
                    "title": current_title,
                    "body": current_body
                })

            current_title = p.replace("【題名】", "").strip()
            current_body = []
        else:
            current_body.append(p)

    if current_body:
        chapters.append({
            "title": current_title,
            "body": current_body
        })

    return chapters


# ==========================================
# 【Phase 03-B】 前書き・後書きの処理
# ==========================================
def process_foreword_afterword(chapters_data):
    final_chapters = []
    temp_foreword = ""

    for chap in chapters_data:
        title = chap['title']
        body_text = "\n".join(chap['body'])

        # 前書きは次の本編のためにキープ
        if "前書き" in title:
            temp_foreword = (
                f'<div style="background-color: #f9f9f9; padding: 15px; '
                f'border: 1px solid #eee; margin-bottom: 20px;">'
                f'<b>（前書き）</b><br>{body_text}</div><hr>'
            )
            continue

        # 後書きは直前の本編の末尾に合流
        if "後書き" in title:
            if final_chapters:
                afterword_html = (
                    f'<hr><div style="background-color: #f9f9f9; padding: 15px; '
                    f'border: 1px solid #eee; margin-top: 20px;">'
                    f'<b>（後書き）</b><br>{body_text}</div>'
                )
                final_chapters[-1]["body"] += afterword_html
            continue

        # 通常の本編
        full_body = temp_foreword + body_text

        # ルビは必ず「|親文字《ルビ》」だけを <ruby> 化する（行全体の巻き込み防止）
        def ruby_marker_repl(m):
            base, ruby = m.group(1), m.group(2)
            if len(base) == len(ruby):
                return "".join(f"<ruby>{b}<rt>{r}</rt></ruby>" for b, r in zip(base, ruby))
            return f"<ruby>{base}<rt>{ruby}</rt></ruby>"

        full_body = re.sub(r"\|([^《]+)《([^》]+)》", ruby_marker_repl, full_body)

        final_chapters.append({
            "title": title,
            "body": full_body
        })
        temp_foreword = ""

    return final_chapters
