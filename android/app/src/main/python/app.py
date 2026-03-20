"""
app.py (Android版)
Phase 05: PDFから本棚1冊分まで一括処理するオーケストレーター
Chaquopy から callAttr("process_pdf", pdf_path, book_id, output_dir, android_mode) で呼び出す。
"""
import os
import pdf_extractor
import chapter_processor
import html_exporter


def process_pdf(pdf_path, book_id, output_dir=None, android_mode=False):
    """PDFを開き、タイトル抽出→本文抽出→話分割→HTML出力まで行い、書籍タイトルを返す。"""
    if output_dir is None:
        # Web版との互換性維持（Android では必ず output_dir を渡すこと）
        output_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "novel_app", book_id)

    real_title = pdf_extractor.extract_book_title(pdf_path)
    paragraphs = pdf_extractor.run_final_engine(pdf_path_override=pdf_path)
    chapters_data = chapter_processor.split_into_chapters(paragraphs)
    final_chapters = chapter_processor.process_foreword_afterword(chapters_data)
    html_exporter.export_to_pwa(final_chapters, book_id, real_title, output_dir, android_mode=android_mode)

    return real_title
