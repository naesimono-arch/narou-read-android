"""
app.py
Phase 05: PDFから本棚1冊分まで一括処理するオーケストレーター
"""
from pathlib import Path
import pdf_extractor
import chapter_processor
import html_exporter

BASE_DIR = Path(__file__).resolve().parent
NOVEL_APP_DIR = BASE_DIR / "novel_app"

def process_pdf(pdf_path, book_id, progress_callback=None):
    """PDFを開き、タイトル抽出→本文抽出→話分割→HTML出力まで行い、書籍タイトルを返す。"""
    def _cb(percent, phase):
        if progress_callback:
            progress_callback(percent, phase)

    _cb(5, "PDFを開いています…")
    real_title = pdf_extractor.extract_book_title(pdf_path)

    paragraphs = pdf_extractor.run_final_engine(
        pdf_path_override=pdf_path,
        progress_callback=lambda pct: _cb(pct, "本文を抽出しています…")
    )

    _cb(60, "章に分割しています…")
    chapters_data = chapter_processor.split_into_chapters(paragraphs)

    _cb(75, "前書き・後書きを処理しています…")
    final_chapters = chapter_processor.process_foreword_afterword(chapters_data)

    _cb(85, "HTMLを生成しています…")
    output_dir = str(NOVEL_APP_DIR / book_id)
    html_exporter.export_to_pwa(final_chapters, book_id, real_title, output_dir)

    return real_title