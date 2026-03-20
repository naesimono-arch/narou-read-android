import pdfplumber

pdf_path = "N6169DZ.pdf"

# ここにルビを見つけた実際のページ数を入力してください
target_page_number = 4

print(f"--- {target_page_number}ページ目のルビを直接解析します ---")

try:
    with pdfplumber.open(pdf_path) as pdf:
        # プログラムの配列は0から始まるため1を引いて調整する
        page_index = target_page_number - 1
        page = pdf.pages[page_index]
        chars = page.chars

        # 本文(14.0)でもページ番号(12.0)でもない文字を抽出
        ruby_chars = [c for c in chars if 2.0 < round(c['size'], 1) < 12.0]

        if ruby_chars:
            print(f"条件に一致する文字を {len(ruby_chars)} 文字発見しました。")
            print("文字 | X座標      | Y座標      | サイズ")
            print("--------------------------------------------------")
            
            # 全体像を把握するため少し多めに30文字出力
            for c in ruby_chars[:30]:
                print(f"{c['text']:<4} | {round(c['x0'], 2):<10} | {round(c['top'], 2):<10} | {round(c['size'], 2)}")
        else:
            print("指定されたページに該当サイズの文字は見つかりませんでした。")

except Exception as e:
    print(f"エラーが発生しました: {e}")