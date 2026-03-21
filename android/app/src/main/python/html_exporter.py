"""
html_exporter.py (Android版)
本棚へのバックリンクは特殊URL（WebView 側で傍受）を使用する。
"""
import os

# WebView が本棚に戻るリクエストとして傍受するための特殊 URL
_BOOKSHELF_URL = "https://novelreader.app/bookshelf"


def export_to_mobile_html(
    final_chapters,
    output_dir,
    book_title=None,
    book_id=None,
    progress_callback=None,
):
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    style = """
    <style>
        body { background-color: #fcfaf2; color: #333; font-family: "MS Mincho", "Hiragino Mincho ProN", serif; line-height: 1.8; margin: 0; padding: 0; -webkit-text-size-adjust: 100%; }
        .container { max-width: 600px; margin: 0 auto; padding: 20px 15px 80px 15px; background-color: #ffffff; min-height: 100vh; }
        h1 { font-size: 1.4em; border-bottom: 2px solid #e0dcd0; padding-bottom: 10px; color: #111; }
        .content { font-size: 1.15em; white-space: pre-wrap; word-wrap: break-word; }
        .nav-footer { position: fixed; bottom: 0; left: 0; width: 100%; background: rgba(252, 250, 242, 0.95); border-top: 1px solid #ddd; display: flex; justify-content: space-around; padding: 15px 0; backdrop-filter: blur(5px); }
        a { color: #8b4513; text-decoration: none; font-weight: bold; }
        ruby rt { font-size: 0.55em; color: #777; ruby-position: over; }
        ruby { ruby-align: center; }
        hr { border: 0; border-top: 1px dashed #ccc; margin: 30px 0; }
        .index-list { list-style: none; padding: 0; }
        .index-list li { padding: 15px 0; border-bottom: 1px solid #eee; }
        .back-link-top { display: inline-block; margin-bottom: 15px; font-family: "Zen Kaku Gothic New", sans-serif; font-size: 0.9em; padding: 8px 12px; background-color: #f4efdf; border-radius: 6px; color: #555; text-decoration: none; font-weight: normal; }
        .back-link-bottom { display: block; text-align: center; margin-top: 30px; padding: 15px; background-color: #f4efdf; border-radius: 8px; font-family: "Zen Kaku Gothic New", sans-serif; color: #555; text-decoration: none; font-weight: normal; }
    </style>
    """

    # 本棚への遷移リンク（WebView 側で傍受する特殊URL）
    bookshelf_href = _BOOKSHELF_URL

    index_heading = (book_title if book_title else "作品目次").strip()
    index_page_title = f"{index_heading} - 目次" if book_title else "小説リーダー - 目次"

    index_html = f"""
    <!DOCTYPE html>
    <html lang="ja">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>{index_page_title}</title>
        {style}
    </head>
    <body>
        <div class="container">
            <a href="{bookshelf_href}" class="back-link-top">← 本棚に戻る</a>
            <h1>{index_heading}</h1>
            <ul class="index-list">
    """

    total_chapters = len(final_chapters)
    for i, chap in enumerate(final_chapters):
        filename = f"chap_{i + 1}.html"
        index_html += f'<li><a href="{filename}">{chap["title"]}</a></li>'

        prev_page = f"chap_{i}.html" if i > 0 else "index.html"
        next_page = f"chap_{i + 2}.html" if i < len(final_chapters) - 1 else "index.html"

        chapter_html = f"""
        <!DOCTYPE html>
        <html lang="ja">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>{chap['title']}</title>
            {style}
        </head>
        <body>
            <div class="container">
                <h1>{chap['title']}</h1>
                <div class="content">
{chap['body']}
                </div>
            </div>

            <div class="nav-footer">
                <a href="{prev_page}">← 前へ</a>
                <a href="index.html">目次</a>
                <a href="{next_page}">次へ →</a>
            </div>

        </body>
        </html>
        """

        with open(os.path.join(output_dir, filename), "w", encoding="utf-8") as f:
            f.write(chapter_html)

        if progress_callback is not None:
            pct = 88 + int((i + 1) / total_chapters * 11)
            progress_callback(pct, f"HTMLを生成しています… ({i+1:,}/{total_chapters:,}章)")

    index_html += f"""
            </ul>
            <a href="{bookshelf_href}" class="back-link-bottom">本棚に戻る</a>
        </div>
    </body>
    </html>
    """

    with open(os.path.join(output_dir, "index.html"), "w", encoding="utf-8") as f:
        f.write(index_html)


def export_to_pwa(final_chapters, book_id, real_title, output_dir, progress_callback=None):
    export_to_mobile_html(
        final_chapters,
        output_dir=output_dir,
        book_title=real_title,
        book_id=book_id,
        progress_callback=progress_callback,
    )
