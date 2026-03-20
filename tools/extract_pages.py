import pdfplumber

pdf_path = "N6169DZ.pdf"
print(f"--- {pdf_path} のページ番号を抽出します ---")
print(f"{'実際のページ':<8}| {'文字':<4} | {'X座標':<10} | {'Y座標':<10} | {'フォントサイズ'}")
print("-" * 60) 

try:
    with pdfplumber.open(pdf_path) as pdf:
        # 2ページ目（index 1）から20ページ目（index 19）までループ
        # 総ページ数を超えないように調整しています
        end_page = min(100, len(pdf.pages))

        for i in range(1, end_page):
            page = pdf.pages[i]
            chars = page.chars

            # フォントサイズが12.0の文字だけを探す
            page_num_chars = [c for c in chars if round(c['size'],1) == 12.0]

            if page_num_chars:
                # 1ページ内に複数文字（例：'1'と'0'）ある場合は結合して表示
                text = ''.join([c['text'] for c in page_num_chars])
                x0 = round(page_num_chars[0]['x0'], 2)
                top = round(page_num_chars[0]['top'], 2)
                size = round(page_num_chars[0]['size'], 2)

                print(f"{i+1:<10} | {text:<4} | {x0:<10} | {top:<10} | {size}")

               
except Exception as e:
    print(f"エラーが発生しました: {e}")