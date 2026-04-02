"""
test_logic.py
Pythonロジックのユニットテスト
実行: cd android/app/src/main/python && python -m unittest test_logic -v
"""
import unittest
from pdf_rules import check_is_title
from chapter_processor import split_into_chapters, process_foreword_afterword


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

    def test_afterword_title_not_split(self):
        # 「後書き」を含む題名は新しい章にならず本文に追加される
        paragraphs = [
            "【題名】第一話",
            "本文1",
            "【題名】後書き",
        ]
        result = split_into_chapters(paragraphs)
        self.assertEqual(len(result), 1)
        self.assertIn("後書き", result[0]["body"])


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


if __name__ == "__main__":
    unittest.main()
