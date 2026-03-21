"""
app.py (Android版)
Phase 05: PDFから本棚1冊分まで一括処理するオーケストレーター
Chaquopy から callAttr("process_pdf", pdf_path, book_id, output_dir, android_mode) で呼び出す。
"""
import os
import pdf_extractor
import chapter_processor
import html_exporter


def process_pdf(pdf_path, book_id, output_dir=None, android_mode=False, progress_callback=None):
    """PDFを開き、タイトル抽出→本文抽出→話分割→HTML出力まで行い、書籍タイトルを返す。"""
    if output_dir is None:
        # Web版との互換性維持（Android では必ず output_dir を渡すこと）
        output_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "novel_app", book_id)

    def _notify(percent, phase):
        if progress_callback is not None:
            progress_callback(percent, phase)

    _notify(5, "タイトルを読み取っています…")
    real_title = pdf_extractor.extract_book_title(pdf_path)

    _notify(15, "本文を抽出しています…")
    paragraphs = pdf_extractor.run_final_engine(pdf_path_override=pdf_path)

    _notify(75, "章を分割しています…")
    chapters_data = chapter_processor.split_into_chapters(paragraphs)

    _notify(82, "前書き・後書きを処理しています…")
    final_chapters = chapter_processor.process_foreword_afterword(chapters_data)

    _notify(88, "HTMLを生成しています…")
    html_exporter.export_to_pwa(final_chapters, book_id, real_title, output_dir, android_mode=android_mode)

    return real_title
