import pdfplumber

# 解析するファイル名
pdf_path = "N6169DZ.pdf"

print(f"--- {pdf_path} の3ページ目を解析します ---")

try:
    with pdfplumber.open(pdf_path) as pdf:
        
        target_page = pdf.pages[3]
        chars = target_page.chars

        if not chars:
            print("文字データが見つかりませんでした。")
        else:
            print(f"取得した総文字数: {len(chars)}文字\n")
            print("解析用のデータ（最初の100文字）を表示します:\n")
            print(f"{'文字':<4} | {'X座標(x0)':<10} | {'Y座標(top)':<10} | {'フォントサイズ(size)'}")
            print("-------------------------------------------------------")

            # 本文とルビの法則を見つけるため、少し多めに出力します
            for char in chars[:600]:
                text = char['text']
                # 座標とサイズを小数点第2位まで丸めて見やすくする
                x0 = round(char['x0'], 2)
                top = round(char['top'], 2)
                size = round(char['size'], 2)

                print(f"{text:<4} | {x0:<10} | {top:<10} | {size}")

except FileNotFoundError:
    print(f"エラー: {pdf_path} が見つかりません。")
except Exception as e:
    print(f"エラーが発生しました: {e}")