import pdfplumber
import math
import pdf_rules

# ==========================================
# 複数ページ（最大100ページ）の例外チェックツール
# 定義から外れる「異常なデータ」だけを探し出します
# ==========================================

pdf_path = "N6169DZ.pdf"

print("--- 複数ページの例外パトロールを開始します ---")

with pdfplumber.open(pdf_path) as pdf:
    # 1. PDFの全ページ数を調べ、チェックするページ数（最大100）を決定します
    total_pages = len(pdf.pages)
    check_pages = min(total_pages, 8000) 
    
    print(f"全 {total_pages} ページ中、最初の {check_pages} ページを検証します。")
    print("私たちの定義（サイズ 14.0, 7.0, 12.0）に当てはまらない謎の文字を探しています...")
    print("-" * 60)
    
    # 異常を見つけた回数をカウントする箱（最初はゼロ）
    error_count = 0 
    
    # 2. 0ページ目から順番に、指定したページ数までループ（繰り返し）します
    for page_num in range(check_pages):
        page = pdf.pages[page_num]
        chars = page.chars
        
        # 3. そのページの中のすべての文字を1つずつチェックします
        for c in chars:
            text = c['text']
            size = c['size']
            font = c['fontname']
            y = c['top']
            
            # 4. あなたのルールに合致しているか、math.isclose（誤差許容）で判定します
            is_body_or_title = math.isclose(size, pdf_rules.FONT_SIZE_BODY_TITLE, abs_tol=pdf_rules.TOLERANCE)
            is_ruby = math.isclose(size, pdf_rules.FONT_SIZE_RUBY, abs_tol=pdf_rules.TOLERANCE)
            is_page_num = math.isclose(size, pdf_rules.FONT_SIZE_PAGE, abs_tol=pdf_rules.TOLERANCE)
            
            # チェックA：サイズが14.0でも7.0でも12.0でもない「未知の文字」ではないか？
            if not (is_body_or_title or is_ruby or is_page_num):
                # ただし、目に見えない「空白文字」はサイズが異常な場合があるため除外します
                if text.strip() != "":
                    print(f"【警告】{page_num + 1}ページ目: 想定外のサイズの文字を発見！ -> 文字: {text} | サイズ: {size:.2f} | フォント: {font}")
                    error_count += 1
                    
            # チェックB：ページ数（サイズ12.0）の場合、Y座標が「528.98」からズレていないか？
            if is_page_num:
                if not math.isclose(y, pdf_rules.PAGE_NUM_Y, abs_tol=pdf_rules.TOLERANCE):
                     print(f"【警告】{page_num + 1}ページ目: ページ数のY座標がズレています！ -> 文字: {text} | Y座標: {y:.2f} (想定: {pdf_rules.PAGE_NUM_Y})")
                     error_count += 1

    print("-" * 60)
    
    # 5. 最終報告
    if error_count == 0:
        print("素晴らしい！指定ページを検証しましたが、定義から外れるエラー文字は1つもありませんでした！")
        print("あなたの定義したルールは完璧に機能しています。")
    else:
        print(f"パトロール完了。合計 {error_count} 個の想定外データが見つかりました。")
        print("この結果をもとに、ルールの追加や修正を検討しましょう。")