"""
test_logic.py
Pythonロジックのユニットテスト
実行: cd android/app/src/main/python && python -m unittest test_logic -v
fixture更新: cd android/app/src/main/python && UPDATE_GOLDEN=1 python -m unittest test_logic.TestHtmlGolden -v
"""
import os
import tempfile
import unittest
from contextlib import ExitStack
from unittest.mock import patch, MagicMock
import pdf_extractor
import pdf_rules
from pdf_rules import check_is_title
from chapter_processor import split_into_chapters, process_foreword_afterword
from html_exporter import export_to_mobile_html


class TestCheckIsTitle(unittest.TestCase):
    """pdf_rules.check_is_title のテスト"""

    def test_bold_font_correct_size(self):
        # Boldフォント + 14.0pt → タイトル判定
        self.assertTrue(check_is_title("HogeB Bold", 14.0))

    def test_bold_font_within_tolerance(self):
        # 許容誤差0.1以内 → タイトル判定
        self.assertTrue(check_is_title("NotoSerifCJK Bold", 13.95))

    def test_non_bold_font(self):
        # Boldなし → タイトルではない
        self.assertFalse(check_is_title("NotoSerifCJK Regular", 14.0))

    def test_wrong_size(self):
        # Boldでもサイズが違う → タイトルではない
        self.assertFalse(check_is_title("HogeB Bold", 7.0))

    def test_none_fontname(self):
        # fontnameがNone → タイトルではない（クラッシュしない）
        self.assertFalse(check_is_title(None, 14.0))


class TestSplitIntoChapters(unittest.TestCase):
    """chapter_processor.split_into_chapters のテスト"""

    def test_empty_input(self):
        self.assertEqual(split_into_chapters([]), [])

    def test_no_title_markers(self):
        # 題名マーカーなし → 1章としてまとめる
        result = split_into_chapters(["本文A", "本文B"])
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["title"], "作品情報・プロローグ")
        self.assertEqual(result[0]["body"], ["本文A", "本文B"])

    def test_single_chapter(self):
        paragraphs = ["【題名】第一話　始まり", "本文A", "本文B"]
        result = split_into_chapters(paragraphs)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["title"], "第一話　始まり")
        self.assertEqual(result[0]["body"], ["本文A", "本文B"])

    def test_multiple_chapters(self):
        paragraphs = [
            "【題名】第一話",
            "本文1",
            "【題名】第二話",
            "本文2",
        ]
        result = split_into_chapters(paragraphs)
        self.assertEqual(len(result), 2)
        self.assertEqual(result[0]["title"], "第一話")
        self.assertEqual(result[1]["title"], "第二話")

    def test_afterword_title_becomes_separate_chapter(self):
        # 後書きは通常の章として分離される（process_foreword_afterword で後処理）
        paragraphs = ["【題名】第一話", "本文1", "【題名】後書き", "後書き本文"]
        result = split_into_chapters(paragraphs)
        self.assertEqual(len(result), 2)
        self.assertEqual(result[1]["title"], "後書き")
        self.assertIn("後書き本文", result[1]["body"])

    def test_afterword_with_no_body_is_dropped(self):
        # 後書きタイトルの直後に本文がない場合、章としてドロップされる
        # （後書きインライン処理削除後: current_body が空のため if current_body: を通過しない）
        paragraphs = ["【題名】第一話", "本文1", "【題名】後書き"]
        result = split_into_chapters(paragraphs)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["title"], "第一話")

    def test_consecutive_titles_no_body_between(self):
        # 本文のない章は if current_body: チェックでサイレントドロップ（仕様明文化）
        paragraphs = ["【題名】第一話", "【題名】第二話", "本文"]
        result = split_into_chapters(paragraphs)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["title"], "第二話")
        self.assertEqual(result[0]["body"], ["本文"])

    def test_afterword_substring_in_chapter_title_is_split(self):
        # タイトルに「後書き」を含む話も通常の章として分離される
        paragraphs = ["【題名】第一話", "本文1", "【題名】第五話　後書きの話", "本文2"]
        result = split_into_chapters(paragraphs)
        self.assertEqual(len(result), 2)
        self.assertEqual(result[1]["title"], "第五話　後書きの話")
        self.assertNotIn("第五話　後書きの話", result[0]["body"])


class TestProcessForewordAfterwword(unittest.TestCase):
    """chapter_processor.process_foreword_afterword のテスト"""

    def test_ruby_single_char(self):
        # 1文字ルビ → <ruby>字<rt>よみ</rt></ruby>
        chapters = [{"title": "第一話", "body": ["|字《よみ》"]}]
        result = process_foreword_afterword(chapters)
        self.assertIn("<ruby>字<rt>よみ</rt></ruby>", result[0]["body"])

    def test_ruby_multi_char_same_length(self):
        # 2文字 + 2文字ルビ → 1文字ずつ分割（「漢字」→「かじ」で文字数を揃える）
        chapters = [{"title": "第一話", "body": ["|漢字《かじ》"]}]
        result = process_foreword_afterword(chapters)
        self.assertIn("<ruby>漢<rt>か</rt></ruby>", result[0]["body"])
        self.assertIn("<ruby>字<rt>じ</rt></ruby>", result[0]["body"])

    def test_ruby_multi_char_different_length(self):
        # 親文字とルビの文字数が異なる → まとめてrubyタグ
        chapters = [{"title": "第一話", "body": ["|三文字《よみ》"]}]
        result = process_foreword_afterword(chapters)
        self.assertIn("<ruby>三文字<rt>よみ</rt></ruby>", result[0]["body"])

    def test_html_special_chars_are_escaped(self):
        # 本文中の < > & は HTML エスケープされ、生のタグとして解釈されない
        chapters = [{"title": "第一話", "body": ["a < b & c > d"]}]
        result = process_foreword_afterword(chapters)
        body = result[0]["body"]
        self.assertIn("a &lt; b &amp; c &gt; d", body)
        self.assertNotIn("a < b", body)

    def test_escape_and_ruby_coexist(self):
        # エスケープ後もルビマーカーは <ruby> へ変換され、親文字内の & も実体参照になる
        chapters = [{"title": "第一話", "body": ["|A&B《えび》"]}]
        result = process_foreword_afterword(chapters)
        self.assertIn("<ruby>A&amp;B<rt>えび</rt></ruby>", result[0]["body"])

    def test_foreword_prepended(self):
        # 前書きは次の章の先頭に付与される
        chapters = [
            {"title": "前書き", "body": ["前書き本文"]},
            {"title": "第一話", "body": ["本文"]},
        ]
        result = process_foreword_afterword(chapters)
        self.assertEqual(len(result), 1)
        self.assertIn("（前書き）", result[0]["body"])
        self.assertIn("前書き本文", result[0]["body"])

    def test_afterword_appended(self):
        # 後書きは直前の章末に付与される
        chapters = [
            {"title": "第一話", "body": ["本文"]},
            {"title": "後書き", "body": ["後書き本文"]},
        ]
        result = process_foreword_afterword(chapters)
        self.assertEqual(len(result), 1)
        self.assertIn("（後書き）", result[0]["body"])

    def test_no_foreword_afterword(self):
        # 前書き・後書きなし → そのまま通過
        chapters = [{"title": "第一話", "body": ["本文"]}]
        result = process_foreword_afterword(chapters)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["title"], "第一話")

    def test_only_foreword_no_following_chapter(self):
        # 前書きのみで後続の通常章がない場合、temp_foreword がセットされるが使われずドロップ
        chapters = [{"title": "前書き", "body": ["前書き本文"]}]
        result = process_foreword_afterword(chapters)
        self.assertEqual(result, [])

    def test_only_afterword_no_preceding_chapter(self):
        # 後書きのみで前章がない場合、if final_chapters: チェックでドロップ
        chapters = [{"title": "後書き", "body": ["後書き本文"]}]
        result = process_foreword_afterword(chapters)
        self.assertEqual(result, [])

    def test_ruby_unmatched_empty_reading(self):
        # |base《》 → [^》]+ は空文字列にマッチしないためマーカーがそのまま残る（クラッシュしない）
        chapters = [{"title": "第一話", "body": ["|字《》"]}]
        result = process_foreword_afterword(chapters)
        self.assertIn("|字《》", result[0]["body"])
        self.assertNotIn("<ruby>", result[0]["body"])

    def test_ruby_in_afterword_body(self):
        # 後書き本文の ruby マーカーも変換される
        chapters = [
            {"title": "第一話", "body": ["本文"]},
            {"title": "後書き", "body": ["|字《よみ》"]},
        ]
        result = process_foreword_afterword(chapters)
        self.assertIn("<ruby>字<rt>よみ</rt></ruby>", result[0]["body"])


def _ch(text, fontname, size, x0, top, bottom=None):
    """テスト用の char dict を生成するヘルパー。"""
    return {
        "text": text,
        "fontname": fontname,
        "size": size,
        "x0": x0,
        "top": top,
        "bottom": bottom if bottom is not None else top + size,
    }


class TestExtractBookTitle(unittest.TestCase):
    """pdf_extractor.extract_book_title のテスト（extract_pages/_iter_chars_from_page をモック）"""

    def _run(self, chars):
        # MagicMock を使うのは extract_book_title が page.height を参照するため
        mock_page = MagicMock()
        with patch("pdf_extractor.extract_pages", return_value=[mock_page]), \
             patch("pdf_extractor._iter_chars_from_page", return_value=chars):
            return pdf_extractor.extract_book_title("dummy.pdf")

    def test_largest_chars_become_title(self):
        # 最大サイズ(20pt)の文字だけがタイトルに結合される
        chars = [
            _ch("小", "R", 12.0, 100.0, 50.0),
            _ch("タ", "R", 20.0, 100.0, 30.0),
            _ch("イ", "R", 20.0, 100.0, 50.0),
            _ch("ト", "R", 20.0, 100.0, 70.0),
        ]
        self.assertEqual(self._run(chars), "タイト")

    def test_sorted_by_top_then_x0(self):
        # top 昇順→x0 昇順でソートされる（縦書き：上から下の順）
        chars = [
            _ch("二", "R", 20.0, 100.0, 70.0),
            _ch("一", "R", 20.0, 100.0, 30.0),
        ]
        self.assertEqual(self._run(chars), "一二")

    def test_tolerance_within_01(self):
        # 最大サイズ差が 0.1 以内の文字もタイトルに含まれる
        chars = [
            _ch("A", "R", 20.0,  100.0, 30.0),
            _ch("B", "R", 19.95, 100.0, 50.0),  # 差0.05 → 含まれる
            _ch("C", "R", 12.0,  100.0, 70.0),  # 差8.0  → 除外
        ]
        self.assertEqual(self._run(chars), "AB")

    def test_no_pages_returns_unknown(self):
        # ページが存在しない場合は "不明なタイトル"
        mock_page = object()
        with patch("pdf_extractor.extract_pages", return_value=[]), \
             patch("pdf_extractor._iter_chars_from_page", return_value=[]):
            result = pdf_extractor.extract_book_title("dummy.pdf")
        self.assertEqual(result, "不明なタイトル")

    def test_no_chars_returns_unknown(self):
        # ページはあるが文字が空の場合は "不明なタイトル"
        self.assertEqual(self._run([]), "不明なタイトル")

    def test_all_whitespace_returns_notitle(self):
        # 最大サイズ文字が空白のみ → "無題の作品"
        chars = [_ch(" ", "R", 20.0, 100.0, 50.0)]
        self.assertEqual(self._run(chars), "無題の作品")


class TestExtractBookAuthor(unittest.TestCase):
    """pdf_extractor.extract_book_author のテスト（extract_pages/_iter_chars_from_page をモック）"""

    def _run(self, chars):
        # MagicMock を使うのは extract_book_author が page.height を参照するため
        mock_page = MagicMock()
        with patch("pdf_extractor.extract_pages", return_value=[mock_page]), \
             patch("pdf_extractor._iter_chars_from_page", return_value=chars):
            return pdf_extractor.extract_book_author("dummy.pdf")

    def test_author_size_chars_are_joined(self):
        # FONT_SIZE_AUTHOR(12pt) の文字が著者名として結合される
        chars = [
            _ch("作", "R", pdf_rules.FONT_SIZE_AUTHOR, 100.0, 100.0),
            _ch("者", "R", pdf_rules.FONT_SIZE_AUTHOR, 110.0, 100.0),
        ]
        self.assertEqual(self._run(chars), "作者")

    def test_footer_chars_are_excluded(self):
        # COVER_FOOTER_Y 付近の同サイズ文字はページ番号等のため除外される
        footer_top = pdf_rules.COVER_FOOTER_Y  # 500.0
        chars = [
            _ch("著", "R", pdf_rules.FONT_SIZE_AUTHOR, 100.0, 100.0),
            _ch("1", "R", pdf_rules.FONT_SIZE_AUTHOR, 100.0, footer_top),  # フッター → 除外
        ]
        self.assertEqual(self._run(chars), "著")

    def test_footer_tolerance_boundary(self):
        # COVER_FOOTER_Y ± COVER_FOOTER_Y_TOL(30) 境界の文字も除外される
        near_footer = pdf_rules.COVER_FOOTER_Y + pdf_rules.COVER_FOOTER_Y_TOL - 1.0
        chars = [
            _ch("著", "R", pdf_rules.FONT_SIZE_AUTHOR, 100.0, 100.0),
            _ch("X", "R", pdf_rules.FONT_SIZE_AUTHOR, 100.0, near_footer),  # 許容誤差内 → 除外
        ]
        self.assertEqual(self._run(chars), "著")

    def test_no_pages_returns_empty(self):
        # ページなし → ""
        with patch("pdf_extractor.extract_pages", return_value=[]), \
             patch("pdf_extractor._iter_chars_from_page", return_value=[]):
            result = pdf_extractor.extract_book_author("dummy.pdf")
        self.assertEqual(result, "")

    def test_no_author_size_chars_returns_empty(self):
        # 著者サイズ文字なし → ""
        chars = [_ch("大", "R", 20.0, 100.0, 100.0)]
        self.assertEqual(self._run(chars), "")

    def test_sorted_by_top_then_x0(self):
        # top 昇順→x0 昇順でソートされる
        chars = [
            _ch("者", "R", pdf_rules.FONT_SIZE_AUTHOR, 100.0, 200.0),
            _ch("著", "R", pdf_rules.FONT_SIZE_AUTHOR, 100.0, 100.0),
        ]
        self.assertEqual(self._run(chars), "著者")


class TestGroupCharsByLine(unittest.TestCase):
    """pdf_extractor._group_chars_by_line のテスト"""

    def test_same_x_grouped(self):
        # 同一 x0 の文字は1グループになる
        chars = [
            _ch("あ", "R", 14.0, 100.0, 50.0),
            _ch("い", "R", 14.0, 100.0, 70.0),
        ]
        result = pdf_extractor._group_chars_by_line(chars)
        self.assertEqual(len(result), 1)
        self.assertEqual(len(list(result.values())[0]), 2)

    def test_close_x_within_tolerance(self):
        # x0 差が TOLERANCE(0.1) 以内は同グループ、超過は別グループ
        close  = _ch("あ", "R", 14.0, 100.05, 50.0)  # 差0.05 → 同グループ
        far    = _ch("い", "R", 14.0, 100.20, 50.0)  # 差0.20 → 別グループ
        anchor = _ch("基", "R", 14.0, 100.0,  50.0)
        result = pdf_extractor._group_chars_by_line([anchor, close, far])
        self.assertEqual(len(result), 2)


class TestAssociateRuby(unittest.TestCase):
    """pdf_extractor._associate_ruby のテスト"""

    def test_ruby_attached_to_nearest_char(self):
        # ルビの x0 が親文字 x0 + RUBY_OFFSET_X と一致する場合に紐付く
        body_char = _ch("漢", "R", 14.0, 200.0, 50.0)
        ruby_char = _ch("か", "R", 7.0, 200.0 + pdf_rules.RUBY_OFFSET_X, 50.0)
        lines_dict = {200.0: [body_char]}
        pdf_extractor._associate_ruby(lines_dict, [ruby_char])
        self.assertEqual(body_char.get("ruby_text"), "か")

    def test_ruby_no_match_ignored(self):
        # 対応する列がないルビはスキップされる（クラッシュしない）
        body_char = _ch("漢", "R", 14.0, 200.0, 50.0)
        ruby_char = _ch("か", "R", 7.0, 999.0, 50.0)  # 対応列なし
        lines_dict = {200.0: [body_char]}
        pdf_extractor._associate_ruby(lines_dict, [ruby_char])
        self.assertNotIn("ruby_text", body_char)


class TestBuildLineStr(unittest.TestCase):
    """pdf_extractor._build_line_str のテスト"""

    def test_ruby_run_built(self):
        # ruby_text 付き文字が |base《ruby》 形式に変換される
        char = _ch("字", "R", 14.0, 100.0, 50.0)
        char["ruby_text"] = "よみ"
        result = pdf_extractor._build_line_str([char])
        self.assertEqual(result, "|字《よみ》")

    def test_plain_text_no_ruby(self):
        # ルビなし文字はそのまま結合される
        chars = [_ch("あ", "R", 14.0, 100.0, 50.0), _ch("い", "R", 14.0, 100.0, 60.0)]
        self.assertEqual(pdf_extractor._build_line_str(chars), "あい")

    def test_consecutive_ruby_merged_into_one_marker(self):
        # ルビあり文字が連続する場合は1つのマーカーにまとめられる
        c1 = _ch("漢", "R", 14.0, 100.0, 50.0); c1["ruby_text"] = "か"
        c2 = _ch("字", "R", 14.0, 100.0, 60.0); c2["ruby_text"] = "じ"
        self.assertEqual(pdf_extractor._build_line_str([c1, c2]), "|漢字《かじ》")

    def test_mixed_ruby_and_plain(self):
        # ルビあり・なしが混在する行はそれぞれ正しく処理される
        ruby_c = _ch("漢", "R", 14.0, 100.0, 50.0); ruby_c["ruby_text"] = "か"
        plain_c = _ch("字", "R", 14.0, 100.0, 60.0)
        self.assertEqual(pdf_extractor._build_line_str([ruby_c, plain_c]), "|漢《か》字")

    def test_whitespace_chars_are_skipped(self):
        # スペース・改行等はスキップされる
        chars = [_ch(" ", "R", 14.0, 100.0, 50.0), _ch("あ", "R", 14.0, 100.0, 60.0)]
        self.assertEqual(pdf_extractor._build_line_str(chars), "あ")


class TestProcessPages(unittest.TestCase):
    """pdf_extractor._process_pages の統合回帰テスト（3点確認）"""

    def _make_pages(self):
        """5ページ構成: page0-2はスキップ, page3が有効, page4はスキップ（total_pages-1）"""
        bold = "NotoSerifCJK Bold"
        reg  = "NotoSerifCJK Regular"
        skip_char   = _ch("除外", reg, 14.0, 200.0, 50.0)
        title_char  = _ch("話", bold, 14.0, 200.0, 50.0)
        body_char   = _ch("本", reg, 14.0, 180.0, 70.0)
        pageno_char = _ch("1", reg, pdf_rules.FONT_SIZE_PAGE, 100.0,
                          pdf_rules.PAGE_NUM_Y,
                          pdf_rules.PAGE_NUM_Y + pdf_rules.FONT_SIZE_PAGE)
        return [
            [skip_char],                           # page 0: 除外されるべき
            [],                                    # page 1
            [],                                    # page 2
            [title_char, body_char, pageno_char],  # page 3: 有効
            [skip_char],                           # page 4 (= total_pages-1): 除外されるべき
        ]

    def test_page_exclusion_title_detection_pageno_exclusion(self):
        pages  = self._make_pages()
        result = pdf_extractor._process_pages(pages, total_pages=5)
        joined = "\n".join(result)

        # 1. ページ除外: page0/4 の「除外」は結果に含まれない
        self.assertNotIn("除外", joined)

        # 2. 題名検出: Bold文字が 【題名】 プレフィックス付きで出力される
        self.assertTrue(any("【題名】" in p for p in result))

        # 3. ページ番号除外: PAGE_NUM_Y 位置の 12pt 文字「1」は出力されない
        self.assertNotIn("1", joined)


# ゴールデンテスト用定数
_GOLDEN_CHAPTERS = [
    {
        "title": "第一話　ゴールデンテスト",
        "body": "この<ruby>物語<rt>ものがたり</rt></ruby>は始まる。\n第二段落。",
    },
    {
        "title": "第二話　終章",
        "body": "すべては<ruby>終<rt>お</rt></ruby>わった。",
    },
]
_GOLDEN_BOOK_TITLE = "テスト小説"
_GOLDEN_BOOK_ID = "golden_test"
_FIXTURE_DIR = os.path.join(os.path.dirname(__file__), "fixtures", "golden_html")
_UPDATE_GOLDEN = os.environ.get("UPDATE_GOLDEN") == "1"


class TestHtmlGolden(unittest.TestCase):
    """html_exporter.export_to_mobile_html のゴールデンテスト
    UPDATE_GOLDEN=1 で fixture を再生成できる"""

    def _run_export(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            export_to_mobile_html(
                _GOLDEN_CHAPTERS, tmpdir, _GOLDEN_BOOK_TITLE,
                _GOLDEN_BOOK_ID, lambda *a: None,
            )
            files = {}
            for name in ["index.html", "chap_1.html", "chap_2.html"]:
                path = os.path.join(tmpdir, name)
                with open(path, encoding="utf-8") as f:
                    files[name] = f.read()
        return files

    def test_golden_html(self):
        actual = self._run_export()
        if _UPDATE_GOLDEN:
            os.makedirs(_FIXTURE_DIR, exist_ok=True)
            for name, content in actual.items():
                with open(os.path.join(_FIXTURE_DIR, name), "w", encoding="utf-8") as f:
                    f.write(content)
            self.skipTest("UPDATE_GOLDEN=1: fixture を更新しました")
        for name, content in actual.items():
            fixture_path = os.path.join(_FIXTURE_DIR, name)
            with open(fixture_path, encoding="utf-8") as f:
                expected = f.read()
            self.assertEqual(expected, content, msg=f"{name} の内容が期待値と異なります")


class TestExportToPwa(unittest.TestCase):
    """html_exporter.export_to_pwa のテスト
    export_to_pwa は export_to_mobile_html の薄いラッパーなので、
    引数が正しく委譲されているかだけを確認する。"""

    def test_delegates_to_export_to_mobile_html(self):
        # export_to_pwa が export_to_mobile_html を正しい引数で呼ぶこと
        from html_exporter import export_to_pwa
        chapters = [{"title": "第一話", "body": "本文"}]
        cb = lambda *a: None
        with patch("html_exporter.export_to_mobile_html") as mock_export:
            export_to_pwa(chapters, "book_01", "テスト小説", "/tmp/out", cb)
        mock_export.assert_called_once_with(
            chapters,
            output_dir="/tmp/out",
            book_title="テスト小説",
            book_id="book_01",
            progress_callback=cb,
        )


class TestProcessPdf(unittest.TestCase):
    """app.process_pdf のエラーハンドリングと正常系テスト

    依存モジュール（pdf_extractor / chapter_processor / html_exporter）は
    unittest.mock.patch で差し替えるため、実 PDF ファイル不要。
    """

    # ---- ヘルパー -------------------------------------------------------

    def _happy_stack(self, overrides=None):
        """正常系パッチを一括適用する ExitStack を返す。
        overrides で特定の関数だけ例外を出させるなど上書きできる。"""
        defaults = {
            "pdf_extractor.extract_book_title": {"return_value": "テスト小説"},
            "pdf_extractor.extract_book_author": {"return_value": "テスト作者"},
            "pdf_extractor.run_final_engine":    {"return_value": []},
            "chapter_processor.split_into_chapters":       {"return_value": []},
            "chapter_processor.process_foreword_afterword": {"return_value": []},
            "html_exporter.export_to_pwa": {},
        }
        if overrides:
            defaults.update(overrides)
        stack = ExitStack()
        for target, kwargs in defaults.items():
            stack.enter_context(patch(target, **kwargs))
        return stack

    # ---- 正常系 ---------------------------------------------------------

    def test_happy_path_returns_title_and_author(self):
        # 全工程が正常終了したとき [title, author] が返ること
        import app as app_module
        with self._happy_stack():
            result = app_module.process_pdf("dummy.pdf", "book_id", "/tmp")
        self.assertEqual(result, ["テスト小説", "テスト作者"])

    # ---- EncryptedPdfError 変換 -----------------------------------------

    def test_password_in_message_raises_EncryptedPdfError(self):
        # 例外メッセージに "password" を含む場合は EncryptedPdfError に変換される
        import app as app_module
        with self._happy_stack({
            "pdf_extractor.extract_book_title": {"side_effect": Exception("password required")}
        }):
            with self.assertRaises(app_module.EncryptedPdfError):
                app_module.process_pdf("dummy.pdf", "book_id", "/tmp")

    def test_pdf_password_incorrect_type_raises_EncryptedPdfError(self):
        # 例外クラス名に "PDFPasswordIncorrect" が含まれる場合も EncryptedPdfError に変換される
        # （pdfminer の例外名が変わっても対応できるよう型名で判定している）
        import app as app_module

        class FakePDFPasswordIncorrectError(Exception):
            pass

        with self._happy_stack({
            "pdf_extractor.extract_book_title": {
                "side_effect": FakePDFPasswordIncorrectError("bad password")
            }
        }):
            with self.assertRaises(app_module.EncryptedPdfError):
                app_module.process_pdf("dummy.pdf", "book_id", "/tmp")

    # ---- InsufficientStorageError 変換 ----------------------------------

    def test_no_space_left_raises_InsufficientStorageError(self):
        # "No space left on device" メッセージ → InsufficientStorageError
        import app as app_module
        with self._happy_stack({
            "pdf_extractor.run_final_engine": {
                "side_effect": OSError("No space left on device")
            }
        }):
            with self.assertRaises(app_module.InsufficientStorageError):
                app_module.process_pdf("dummy.pdf", "book_id", "/tmp")

    def test_errno28_raises_InsufficientStorageError(self):
        # "[Errno 28]" メッセージ → InsufficientStorageError
        import app as app_module
        with self._happy_stack({
            "pdf_extractor.run_final_engine": {
                "side_effect": OSError("[Errno 28] No space left on device")
            }
        }):
            with self.assertRaises(app_module.InsufficientStorageError):
                app_module.process_pdf("dummy.pdf", "book_id", "/tmp")

    # ---- カスタム例外の再送出 -------------------------------------------

    def test_already_encrypted_pdf_error_reraises(self):
        # すでに EncryptedPdfError であれば変換せずそのまま再送出される
        import app as app_module
        with self._happy_stack({
            "pdf_extractor.extract_book_title": {
                "side_effect": app_module.EncryptedPdfError("already typed")
            }
        }):
            with self.assertRaises(app_module.EncryptedPdfError):
                app_module.process_pdf("dummy.pdf", "book_id", "/tmp")

    def test_corrupted_pdf_error_reraises(self):
        # すでに CorruptedPdfError であれば変換せずそのまま再送出される
        # （app.py:66 の isinstance チェックに含まれているが専用テストがなかったため追加）
        import app as app_module
        with self._happy_stack({
            "pdf_extractor.extract_book_title": {
                "side_effect": app_module.CorruptedPdfError("corrupted")
            }
        }):
            with self.assertRaises(app_module.CorruptedPdfError):
                app_module.process_pdf("dummy.pdf", "book_id", "/tmp")

    def test_pdfminer_parse_error_raises_CorruptedPdfError(self):
        # pdfminer 由来の解析例外（型名に PDFSyntaxError 等を含む）は CorruptedPdfError に変換される。
        # 例外名で判定するため、pdfminer のバージョン差でモジュールが変わっても対応できる。
        import app as app_module

        class PDFSyntaxError(Exception):
            pass

        with self._happy_stack({
            "pdf_extractor.run_final_engine": {
                "side_effect": PDFSyntaxError("Syntax Error: Invalid xref table")
            }
        }):
            with self.assertRaises(app_module.CorruptedPdfError):
                app_module.process_pdf("dummy.pdf", "book_id", "/tmp")

    # ---- 未知例外の再送出 -----------------------------------------------

    def test_unknown_exception_reraises_as_is(self):
        # 上記どの条件にも当てはまらない例外はラップせずそのまま再送出される
        import app as app_module
        with self._happy_stack({
            "pdf_extractor.run_final_engine": {
                "side_effect": ValueError("unexpected internal error")
            }
        }):
            with self.assertRaises(ValueError):
                app_module.process_pdf("dummy.pdf", "book_id", "/tmp")


if __name__ == "__main__":
    unittest.main()
