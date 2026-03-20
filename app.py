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

def process_pdf(pdf_path, book_id):
    """PDFを開き、タイトル抽出→本文抽出→話分割→HTML出力まで行い、書籍タイトルを返す。"""
    real_title = pdf_extractor.extract_book_title(pdf_path)
    paragraphs = pdf_extractor.run_final_engine(pdf_path_override=pdf_path)
    chapters_data = chapter_processor.split_into_chapters(paragraphs)
    final_chapters = chapter_processor.process_foreword_afterword(chapters_data)
    
    # ここで絶対パスを計算して明示的に渡す
    output_dir = str(NOVEL_APP_DIR / book_id)
    html_exporter.export_to_pwa(final_chapters, book_id, real_title, output_dir)
    
    return real_title