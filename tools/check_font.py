import pdfplumber

pdf_path = "N1453LW.pdf"
print("--- 題名と本文のフォント情報を比較します ---")

try:
    with pdfplumber.open(pdf_path) as pdf:
        # 先ほど題名と本文が混在していた4ページ目（index 3）を指定
        page = pdf.pages[3]
        chars = page.chars

        print(f"{'文字':<4} | {'サイズ':<6} | {'フォント名 (fontname)'}")
        print("-" * 60)

        # 題名と本文の違いを比較するため、最初の50文字を出力
        for char in chars[:50]:
            text = char['text']
            size = round(char['size'], 2)
            # フォント名を取得（存在しない場合は'不明'とする）
            fontname = char.get('fontname', '不明')

            print(f"{text:<4} | {size:<6} | {fontname}")

except Exception as e:
    print(f"エラーが発生しました: {e}")