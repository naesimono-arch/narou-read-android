# kotlin-pdf-extractor-proto

Chaquopy(Python) 廃止に向けた、Kotlin + Apache PDFBox による縦書きPDFルビ抽出プロトタイプ（JVM スタンドアロン）。

移植元: `novel-reader_andloid/android/app/src/main/python/`
（`pdf_extractor.py` / `pdf_rules.py` / `chapter_processor.py` / `app.py` / `test_logic.py`）を Kotlin へ忠実移植。

## 構成

| ファイル | 役割 | 移植元 |
|---|---|---|
| `Models.kt` | CharBox / JSON モデル（@Serializable） | char dict 相当 |
| `ParserRules.kt` | 判定ルール定数 + isClose / checkIsTitle | `pdf_rules.py` |
| `PdfExtractor.kt` | PDFTextStripper 派生 + タイトル/著者/本文抽出 | `pdf_extractor.py` |
| `TextProcessor.kt` | 列グルーピング・ルビ紐付け・段落縫合 | `_process_pages` 他 |
| `ChapterProcessor.kt` | 章分割・前書き/後書き畳み込み・ノード化 | `chapter_processor.py` |
| `GoldenComparator.kt` | ゴールデン精度比較（文字一致率・ルビ P/R・グリフ正規化） | `test_logic.py` の検証思想 |
| `Main.kt` | CLI（抽出 / --dump / --bench / --compare） | `app.py` |
| `generate_golden.py` | Python リファレンスから合意JSON形式のゴールデン生成 | （検証用ツール） |

## ビルド・実行（Windows）

ビルドには Android Studio 同梱の **JBR(JDK 21)** を使う。

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat build                                          # コンパイル + 36 ユニットテスト
.\gradlew.bat run -q --args="../sample_pdfs/N6169DZ.pdf output"            # JSON 出力
.\gradlew.bat run -q --args="../sample_pdfs/N6169DZ.pdf --dump"            # 座標キャリブレーション
.\gradlew.bat run -q --args="../sample_pdfs/N6169DZ.pdf --bench 5"         # 抽出時間ベンチ
.\gradlew.bat run -q --args="../sample_pdfs/N6169DZ.pdf --compare golden_spec/N6169DZ.json"  # 精度比較
```

ゴールデン精度比較は、先に Python リファレンスでゴールデンを生成しておく（要 `pdfminer.six`）：

```powershell
cd .\ ; python generate_golden.py     # golden_spec/*.json を生成（合意JSON形式）
```

> **コンソール文字化け**: 標準出力の日本語は Shift-JIS 環境で化けるが、生成 JSON(UTF-8)と
> `--compare` の数値出力（英数字）は正常。本文の確認は JSON ファイルを直接開くこと。
>
> **【重要】非ASCIIパスで動かさない**: プロジェクトを `開発` のような非ASCII文字を含むパスから
> `gradlew` 起動すると、フォークされるテストワーカーのクラスパスが解決できず、全テストが
> `ClassNotFoundException` で落ちる（コンパイル自体は通るため気付きにくい）。
> ASCII のみのパス（例: `...\project\...`）から実行すること。

## 検証結果（実測 2026-06-20・JBR21）

- **座標キャリブレーション**: 本文 14.0pt / ページ番号 12.0pt / ルビ 7.0pt が Python 定数と一致。
- **章構造**: 前書き/後書き畳み込み後の章数が Python リファレンスと完全一致（3 / 131 / 951）。
- **`--compare`（Python ゴールデン照合）**:

  | PDF | title/author | 章数 | 章題一致 | 行カバレッジ | ルビ P / R |
  |---|---|---|---|---|---|
  | N1453LW（短・ルビ無） | 一致 | 3=3 | 100% | 100.00% | 100% / 100% |
  | N2959KI（中・多章） | 一致 | 131=131 | — | 100.00% | (ルビ無) |
  | N6169DZ（長・ルビ多） | 一致 | 951=951 | 99.47% | 92.95% | 80.80% / 81.59% |

  > 行カバレッジ・ルビ P/R の残差は **pdfminer↔PDFBox のグリフ CID→Unicode 差**が主因
  > （波ダッシュ 〜/～、罫線ダッシュ ―/— 等）。`GoldenComparator` は確証のある対のみ正規化して
  > 偽の不一致を除く。title は正規化により3本とも一致。
- **速度（ウォーム後・5回中央値）**: 短編 22ms / 中編 346ms / 長編(951章) 4,083ms。
  タイトル/著者抽出は表紙を1回だけストリップして両方算出（旧実装の2回ストリップを解消）。

## JSON 形式

```json
{
  "title": "...", "author": "...",
  "chapters": [
    { "title": "...", "paragraphs": [
      { "type": "plain", "text": "..." },
      { "type": "ruby", "base": "名前", "reading": "なまえ" }
    ] }
  ]
}
```

## 既知の挙動（移植元と同一）

- ルビ親文字が縦列の境界をまたぐ場合、ルビノードが列単位で分割される（Python 版と同じ）。
- 先頭3ページ（表紙・注意書き）と最終1ページ（クレジット）は除外。
