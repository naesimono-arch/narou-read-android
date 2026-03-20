import pdfplumber

pdf_path = "N6169DZ.pdf"
print("--- 行間（X座標の差）と字下げ（Y座標の開始位置）を計測します（1〜50ページ） ---")

try:
    with pdfplumber.open(pdf_path) as pdf:

        # 最大50ページまで（PDFがそれ未満ならそのページ数まで）
        max_pages = min(50, len(pdf.pages))

        for page_index in range(max_pages):

            print(f"\n========== {page_index + 1}ページ目 ==========")

            page = pdf.pages[page_index]

            # 本文サイズ（14.0）のみ抽出
            chars = [c for c in page.chars if round(c['size'], 1) == 14.0]

            # X座標ごとに行をグループ化
            lines = {}
            for c in chars:
                x0 = round(c['x0'], 2)
                if x0 not in lines:
                    lines[x0] = []
                lines[x0].append(c)

            # X座標を右→左で並べる
            sorted_x = sorted(lines.keys(), reverse=True)

            print(f"{'行番号':<5} | {'X座標':<8} | {'前行との差(X)':<13} | {'開始Y座標':<10} | {'最初の文字'}")
            print("-" * 65)

            prev_x = None

            for i, x in enumerate(sorted_x):

                # 行内の文字をY座標順に並べる
                line_chars = sorted(lines[x], key=lambda c: c['top'])

                first_char = line_chars[0]
                top_y = round(first_char['top'], 2)
                text = first_char['text']

                if prev_x is None:
                    diff_x = 0.0
                else:
                    diff_x = round(prev_x - x, 2)

                print(f"{i+1:<7} | {x:<9} | {diff_x:<16} | {top_y:<11} | {text}")

                prev_x = x

except Exception as e:
    print(f"エラーが発生しました: {e}")