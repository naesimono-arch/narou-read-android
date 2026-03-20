import pdfplumber

# ==========================================
# 検証用：PDFの生データを抽出し、ソートして確認するスクリプト
# ==========================================

# 読み込むPDFのファイル名（実際に使用するファイル名に合わせてください）
pdf_path = "N6169DZ.pdf" 

print("--- PDFデータの抽出と検証を開始します ---")

# 1. PDFファイルを開く
with pdfplumber.open(pdf_path) as pdf:
    
    # 2. 最初のページ（0ページ目）を取得する
    first_page = pdf.pages[3]
    
    # 3. ページ内のすべての「文字情報（座標やフォントなど）」を取得する
    chars = first_page.chars
    
    # 4. あなたの要件に従い、X座標の降順（右から左）、Y座標の昇順（上から下）で並び替える
    # x0の前に「-（マイナス）」をつけることで降順（大きい順）になります
    sorted_chars = sorted(chars, key=lambda c: (-c['x0'], c['top']))
    
    print("■ 最初の50文字のデータを表示します（右上の文字から順番に表示されるはずです）")
    print("-" * 60)
    
    # 5. 並び替えた文字のうち、最初の50文字だけを取り出して画面に表示する
    for i in range(600):
        # 万が一、50文字未満のページだった場合のエラー防止
        if i >= len(sorted_chars):
            break
            
        c = sorted_chars[i]
        
        # 各種データをわかりやすく変数に分ける
        text = c['text']           # 文字そのもの
        x = c['x0']                # X座標（左端の位置）
        y = c['top']               # Y座標（上からの距離）
        font = c['fontname']       # フォント名
        size = c['size']           # フォントサイズ
        
        # 小数点第2位まで揃えて出力（検証しやすくするため）
        print(f"文字: {text} | X座標: {x:.2f} | Y座標: {y:.2f} | フォント: {font} | サイズ: {size:.2f}")

print("-" * 60)
print("検証完了")