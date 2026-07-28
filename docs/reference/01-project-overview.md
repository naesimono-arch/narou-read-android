# 01 プロジェクト概要 — novel-reader_andloid

API機能を検討する前提として、対象アプリが「今どういうものか」を押さえる。
（一次情報の正本はリポジトリ内 `CLAUDE.md` / `STATUS.md` / `.claude/skills/architecture`）

## 何をするアプリか

日本語Web小説（なろう系）の **PDF** を、**ふりがな（ルビ）対応のHTML** に変換して
アプリ内で縦組み風に読む Androidリーダー。ユーザーが手元のPDFを取り込む使い方。

- UI: Jetpack Compose ネイティブ描画（WebViewではない）。ルビは `RubyText` で自前描画。
- 抽出: Kotlin ネイティブ実装（**PDFBox-Android** 2.0.27.0）。
  - 旧構成の Chaquopy(Python 3.12)+pdfminer は 2026-07-05 に完全撤去済み（APK 67→24MiB）。
- ビルド: compileSdk 36 / minSdk 26 / targetSdk 36 / AGP 8.9.1 / Gradle 8.11.1 / JDK 17（正本＝`android/app/build.gradle`）。

## 処理パイプライン（現状）

```
ユーザーがPDFを選択（SAF）
  → PdfProcessingService（前景サービス＋WakeLock）
    → BookRepository.addBook()
      → PdfBookExtractor.process(pdf, bookId, outputDir, onProgress)   ← facade / 4ステップ進捗
          step0 メタ抽出（title/author）
          step1 本文抽出（PDFBoxで縦書き座標解析・CID→Unicode解決・波ダッシュ正規化）
          step2 章分割（【題名】マーカー）＋前後書き整形＋ |base《ruby》 → <ruby>
          step3 HTML出力（index.html + chap_N.html／旧Python出力とバイト等価）
  → context.filesDir/novels/{bookId}/*.html に保存
  → Room（books / progress）に登録
  → 読書画面（ChapterHtmlParser → LazyColumn + RubyText）
```

構成の詳細は `../novel-reader_andloid/.claude/skills/architecture/SKILL.md` を参照。

## API検討にとって決定的な「現状の前提」

| 項目 | 現状 | API化への含意 |
|---|---|---|
| **ネットワーク** | **一切なし**。`INTERNET` 権限も無い。HTTPクライアント（OkHttp/Ktor/Retrofit）も無い。ネットワークコード0行 | API連携は**ネットワーク層をゼロから新設**する話になる（権限・HTTPクライアント・JSONパーサ・Repository/ViewModel/UI） |
| **入力の起点** | ローカルPDF（`content://` 一時権限）を SAF で取り込む | 「作品をどこから持ってくるか」の起点が increase する。APIは"発見"の起点を足す |
| **本文の生成元** | PDFのグリフ座標解析。ルビ・章分割・前後書きが**PDFレイアウト前提**に作り込まれている | APIやWebから本文を取る場合、この抽出ロジックは**そのままは使えない**（HTMLソースは座標を持たない）。別パーサが要る |
| **保存/読書** | `filesDir/novels/{id}/*.html` ＋ Room。読書はローカルHTMLをネイティブ描画 | ここは資産として再利用可能。**「発見→本文をHTML化→この保存/読書系に流し込む」**が繋げれば体験が完結する |
| **権限/OEM** | 前景サービス＋WAKE_LOCK でOPPO/ColorOSの積極killに対処済み | ネットワーク取得も長時間なら同様の前景サービス設計を踏襲できる |

## 既にある「外部連携」の宿題（handover.md §D より）

- **Phase3 外部連携**（未着手）:
  - ① 内部ブラウザからPDF直接取込＆動線追加
  - ② 「小説家になろう」公式API連携・ランキング表示（`docs/reference/narou_api_manual.md` 参照）

→ 本フォルダの検討は、この②（＋関連して①）を具体化するもの。
