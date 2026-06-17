"""
app.py (Android版)
Phase 05: PDFから本棚1冊分まで一括処理するオーケストレーター
Chaquopy から callAttr("process_pdf", pdf_path, book_id, output_dir) で呼び出す。
"""
import os
import pdf_extractor
import chapter_processor
import html_exporter


# Kotlin の classifyError() がクラス名で判定するためのカスタム例外
class EncryptedPdfError(Exception):
    pass


class InsufficientStorageError(Exception):
    pass


class CorruptedPdfError(Exception):
    pass


# pdfminer / pdfparser が PDF 構造の破損で投げる例外の型名マーカー。
# なぜ型名の部分一致で判定するか: pdfminer のバージョン差で例外の所属モジュール
# （pdfminer.pdfparser / pdfminer.pdfexceptions / pdfminer.psexceptions 等）が
# 変わっても追従できるよう、EncryptedPdf 判定と同じく型名で判定する。
_CORRUPTED_PDF_MARKERS = (
    "PDFSyntaxError",
    "PDFException",
    "PDFNoValidXRef",
    "PSEOF",
    "PSSyntaxError",
    "PSException",
)


def process_pdf(pdf_path, book_id, output_dir, progress_callback=None):
    """PDFを開き、タイトル抽出→本文抽出→話分割→HTML出力まで行い、書籍タイトルを返す。"""

    def _notify(step, step_local, phase):
        if progress_callback is not None:
            progress_callback(step, step_local, phase)

    try:
        _notify(0, 0.0, "タイトルを読み取っています…")
        real_title = pdf_extractor.extract_book_title(pdf_path)
        real_author = pdf_extractor.extract_book_author(pdf_path)

        _notify(1, 0.0, "本文を抽出しています…")
        paragraphs = pdf_extractor.run_final_engine(
            pdf_path_override=pdf_path,
            progress_callback=lambda pct, cur, tot: _notify(1, cur / max(tot, 1), f"本文を抽出しています… ({cur+1:,}/{tot:,}ページ)")
        )

        _notify(2, 0.0, "章を分割しています…")
        chapters_data = chapter_processor.split_into_chapters(paragraphs)

        _notify(2, 1.0, "前書き・後書きを処理しています…")
        final_chapters = chapter_processor.process_foreword_afterword(chapters_data)

        _notify(3, 0.0, "HTMLを生成しています…")
        html_exporter.export_to_pwa(
            final_chapters, book_id, real_title, output_dir,
            progress_callback=lambda pct, phase: _notify(3, (pct - 88) / 12, phase)
        )

        return [real_title, real_author]

    except Exception as e:
        err_str = str(e)
        err_type = type(e).__name__

        if "PDFPasswordIncorrect" in err_type or "password" in err_str.lower():
            raise EncryptedPdfError(err_str) from e
        if "No space left on device" in err_str or "[Errno 28]" in err_str:
            raise InsufficientStorageError(err_str) from e
        # PDF 構造の破損（pdfminer 由来の解析例外）をユーザー向けに分類する。
        # なぜ型名のみで限定するか: ValueError 等の汎用例外まで巻き込むと
        # 本来「予期しないエラー」として扱うべきものまで「破損」に化けるため、
        # pdfminer 系の型名に該当する場合だけ CorruptedPdfError に包む。
        if any(marker in err_type for marker in _CORRUPTED_PDF_MARKERS):
            raise CorruptedPdfError(err_str) from e
        # EncryptedPdfError / InsufficientStorageError / CorruptedPdfError などの
        # カスタム例外も、それ以外の未知例外も、ここではそのまま再送出する
        # （bare raise なので型・トレースバックを保持）。
        raise
