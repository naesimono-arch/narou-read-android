"""
chapter_processor.py
Phase 03-03B: 段落リストを話数・前書き・後書きに分割・整形するモジュール
（変更なし — 純Pythonのみ、完全互換）
"""
import re


def split_into_chapters(paragraphs):
    chapters = []
    current_title = "作品情報・プロローグ"
    current_body = []

    for p in paragraphs:
        if p.startswith("【題名】"):
            if "後書き" in p:
                current_body.append(p.replace("【題名】", ""))
                continue

            if current_body:
                chapters.append({"title": current_title, "body": current_body})

            current_title = p.replace("【題名】", "").strip()
            current_body = []
        else:
            current_body.append(p)

    if current_body:
        chapters.append({"title": current_title, "body": current_body})

    return chapters


def process_foreword_afterword(chapters_data):
    final_chapters = []
    temp_foreword = ""

    for chap in chapters_data:
        title = chap["title"]
        body_text = "\n".join(chap["body"])

        if "前書き" in title:
            temp_foreword = (
                f'<div style="background-color: #f9f9f9; padding: 15px; '
                f'border: 1px solid #eee; margin-bottom: 20px;">'
                f'<b>（前書き）</b><br>{body_text}</div><hr>'
            )
            continue

        if "後書き" in title:
            if final_chapters:
                afterword_html = (
                    f'<hr><div style="background-color: #f9f9f9; padding: 15px; '
                    f'border: 1px solid #eee; margin-top: 20px;">'
                    f'<b>（後書き）</b><br>{body_text}</div>'
                )
                final_chapters[-1]["body"] += afterword_html
            continue

        full_body = temp_foreword + body_text

        def ruby_marker_repl(m):
            base, ruby = m.group(1), m.group(2)
            if len(base) == len(ruby):
                return "".join(f"<ruby>{b}<rt>{r}</rt></ruby>" for b, r in zip(base, ruby))
            return f"<ruby>{base}<rt>{ruby}</rt></ruby>"

        full_body = re.sub(r"\|([^《]+)《([^》]+)》", ruby_marker_repl, full_body)

        final_chapters.append({"title": title, "body": full_body})
        temp_foreword = ""

    return final_chapters
