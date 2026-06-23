"""
golden_regression.py
現行 Python エンジン（pdf_extractor + chapter_processor）の実PDF抽出結果を
スナップショットとして固定し、回帰検出する。Kotlin+PDFBox移植の「正解」を厳密に固定する目的。

使い方:
  # 初回 or 意図的な更新（スナップショット生成）
  UPDATE_GOLDEN=1 python golden_regression.py
  # 回帰チェック（差分があれば exit 1）
  python golden_regression.py

なぜ本体テスト(android/.../test_logic.py)に入れないか:
  実PDF(最大8.9MB)は ab-review/ 配下の未追跡資産であり、CIや他環境で利用できない。
  本体の単体テストは合成データで完結させ、実PDF回帰はここで分離して回す。
"""
import os
import sys
import json
import hashlib
import time

# 現行エンジン（android配下）を import パスに追加
_PY_DIR = os.path.join(
    os.path.dirname(__file__), "..", "android", "app", "src", "main", "python"
)
sys.path.insert(0, os.path.abspath(_PY_DIR))

import pdf_extractor  # noqa: E402
from chapter_processor import split_into_chapters, process_foreword_afterword  # noqa: E402

SAMPLE_DIR = os.path.join(os.path.dirname(__file__), "sample_pdfs")
GOLDEN_DIR = os.path.join(os.path.dirname(__file__), "golden_regression")


def _ruby_runs(paragraphs):
    """段落リスト中のルビマーカー「|base《reading》」総数を数える。"""
    return sum(p.count("《") for p in paragraphs)


def build_snapshot(pdf_path):
    """1つのPDFから抽出メトリクスと正規化ダイジェストを作る。"""
    t0 = time.perf_counter()
    title = pdf_extractor.extract_book_title(pdf_path)
    author = pdf_extractor.extract_book_author(pdf_path)
    paragraphs = pdf_extractor.run_final_engine(pdf_path_override=pdf_path)
    chapters = process_foreword_afterword(split_into_chapters(paragraphs))
    elapsed = time.perf_counter() - t0

    body_text = "\n".join(paragraphs)
    # 全文ハッシュで本文同一性を1値に圧縮（差分時はメトリクスで切り分け）
    digest = hashlib.sha256(body_text.encode("utf-8")).hexdigest()

    return {
        "title": title,
        "author": author,
        "paragraph_count": len(paragraphs),
        "blank_paragraph_count": sum(1 for p in paragraphs if p == ""),
        "chapter_count": len(chapters),
        "chapter_titles": [c.get("title", "") for c in chapters],
        "total_chars": len(body_text),
        "ruby_run_count": _ruby_runs(paragraphs),
        "body_sha256": digest,
        "elapsed_sec": round(elapsed, 2),
        # 先頭3段落を目視確認用に保持（ハッシュ不一致時の切り分け）
        "head_paragraphs": paragraphs[:3],
    }


def main():
    update = os.environ.get("UPDATE_GOLDEN") == "1"
    os.makedirs(GOLDEN_DIR, exist_ok=True)
    pdfs = sorted(f for f in os.listdir(SAMPLE_DIR) if f.lower().endswith(".pdf"))
    if not pdfs:
        print(f"PDFが見つかりません: {SAMPLE_DIR}")
        return 1

    failures = []
    for name in pdfs:
        snap = build_snapshot(os.path.join(SAMPLE_DIR, name))
        gpath = os.path.join(GOLDEN_DIR, name + ".json")
        if update:
            with open(gpath, "w", encoding="utf-8") as f:
                json.dump(snap, f, ensure_ascii=False, indent=2)
            print(f"[更新] {name}: {snap['paragraph_count']}段落 "
                  f"{snap['chapter_count']}章 ルビ{snap['ruby_run_count']} "
                  f"{snap['elapsed_sec']}s")
            continue

        if not os.path.exists(gpath):
            print(f"[未固定] {name}: ゴールデン未生成。UPDATE_GOLDEN=1 で生成してください。")
            failures.append(name)
            continue

        with open(gpath, encoding="utf-8") as f:
            golden = json.load(f)
        # elapsed_sec は環境依存なので比較対象から除外
        cur = {k: v for k, v in snap.items() if k != "elapsed_sec"}
        old = {k: v for k, v in golden.items() if k != "elapsed_sec"}
        if cur == old:
            print(f"[OK] {name}: {snap['paragraph_count']}段落 "
                  f"{snap['chapter_count']}章 ルビ{snap['ruby_run_count']} "
                  f"({snap['elapsed_sec']}s)")
        else:
            print(f"[NG] {name}: 差分検出")
            for k in cur:
                if cur[k] != old.get(k):
                    print(f"    {k}: golden={old.get(k)!r} -> now={cur[k]!r}")
            failures.append(name)

    if failures and not update:
        print(f"\n回帰検出: {len(failures)}/{len(pdfs)} 件 {failures}")
        return 1
    print(f"\n完了: {len(pdfs)}件 {'生成' if update else '全一致'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
