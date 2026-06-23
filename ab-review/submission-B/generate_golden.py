# -*- coding: utf-8 -*-
"""
Python リファレンス(pdfminer)から「合意JSON形式」のゴールデンを生成する。

目的: --compare の正解データ。リファレンスの最終段 process_foreword_afterword は
HTML(<ruby>)を吐くため、ここでは run_final_engine + split_into_chapters（マーカー保持）まで
リファレンスをそのまま使い、前書き/後書きの畳み込みとノード化のみ JSON 向けに行う。
畳み込み規則・ラベル("（前書き）"/"（後書き）")はプロトタイプ(ChapterProcessor)と同形に揃える。
こうすることで --compare の差分が「ラベル様式の違い」ではなく
「pdfminer↔PDFBox の抽出精度差」を表すようになる。

使い方:
  python generate_golden.py            # ../sample_pdfs/*.pdf を golden_spec/ へ
"""
import sys, os, re, json

sys.path.append(os.path.abspath("../../android/app/src/main/python"))
import pdf_extractor
import chapter_processor

RUBY = re.compile(r"\|([^《]+)《([^》]+)》")
NAMES = ["N1453LW", "N2959KI", "N6169DZ"]
OUT_DIR = "golden_spec"


def fold_foreword_afterword(split):
    """ChapterProcessor.processForewordAfterword と同じ構造規則をテキスト水準で再現。"""
    final = []
    temp_foreword = None
    for chap in split:
        title, body = chap["title"], chap["body"]
        if "前書き" in title:
            temp_foreword = ["（前書き）"] + body
        elif "後書き" in title:
            if final:
                final[-1]["body"].append("（後書き）")
                final[-1]["body"].extend(body)
        else:
            merged = (temp_foreword or []) + body
            final.append({"title": title, "body": list(merged)})
            temp_foreword = None
    return final


def to_nodes(text):
    nodes, last = [], 0
    for m in RUBY.finditer(text):
        if m.start() > last:
            nodes.append({"type": "plain", "text": text[last:m.start()]})
        nodes.append({"type": "ruby", "base": m.group(1), "reading": m.group(2)})
        last = m.end()
    if last < len(text):
        nodes.append({"type": "plain", "text": text[last:]})
    return nodes


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name in NAMES:
        path = f"../sample_pdfs/{name}.pdf"
        if not os.path.exists(path):
            print(f"skip (not found): {path}")
            continue
        title = pdf_extractor.extract_book_title(path)
        author = pdf_extractor.extract_book_author(path)
        paras = pdf_extractor.run_final_engine(pdf_path_override=path)
        split = chapter_processor.split_into_chapters(paras)
        folded = fold_foreword_afterword(split)
        book = {
            "title": title,
            "author": author,
            "chapters": [
                {"title": c["title"], "paragraphs": to_nodes("\n".join(c["body"]))}
                for c in folded
            ],
        }
        out = os.path.join(OUT_DIR, f"{name}.json")
        with open(out, "w", encoding="utf-8") as f:
            json.dump(book, f, ensure_ascii=False, indent=2)
        print(f"wrote {out} (chapters={len(book['chapters'])})")


if __name__ == "__main__":
    main()
