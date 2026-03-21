# Task Diary

作業記録・失敗と成功のログ。

---

## 2026-03-21 | 進捗バーの精度改善（ページ数・章数カウンター追加）＋ Android専用構成への移行

### 背景

10,000ページ規模のPDFを処理中、進捗バーが長時間ほぼ動かず処理状況が不明だった。
また、Web版とAndroid版でPythonファイルが二重管理になっており、Web版のみ修正してAndroid版に反映し忘れるミスが発生した。

---

### 実装内容

#### フェーズ1：ページ数・章数カウンターの追加

| フェーズ | 変更前 | 変更後 |
|---------|--------|--------|
| 抽出中 | `本文を抽出しています…`（固定） | `本文を抽出しています… (3,450/9,996ページ)` |
| HTML生成中 | `HTMLを生成しています…`（85%固定） | `HTMLを生成しています… (234/1,024章)` |

**変更ファイル（Android版）**：

| ファイル | 変更内容 |
|---------|---------|
| `android/.../pdf_extractor.py` | `run_final_engine` に `progress_callback=None` を追加。ループ内で `(pct, processed, body_total)` をコールバック |
| `android/.../app.py` | ページ数テキスト付き lambda を `run_final_engine` に渡す。`export_to_pwa` にもコールバック伝播 |
| `android/.../html_exporter.py` | `export_to_mobile_html` / `export_to_pwa` に `progress_callback=None` を追加。章書き出しループ内で章カウンター通知 |

#### フェーズ2：Android専用構成への移行

Web版のルートPythonファイル群・Flask関連ファイル・ツール群を全削除。
Pythonロジックを `android/app/src/main/python/` に一本化。

**削除したもの**：`app.py`, `bookshelf.py`, `pdf_extractor.py`, `html_exporter.py`, `chapter_processor.py`, `pdf_rules.py`, `index.html`, `requirements.txt`, `tools/`, `library/`, `novel_app/`

---

### 失敗・気づき

#### Web版だけ修正してAndroid版を忘れた

進捗カウンターをまず **ルートのPythonファイル（Web版）** に実装した。
「画面上の表示は何も変わっていない」と言われて気づいた。

**根本原因**：このプロジェクトはPythonロジックが2箇所に存在する。
- `pdf_extractor.py` 等（ルート直下）→ Web版（pdfplumber使用）
- `android/app/src/main/python/` → Android版（pdfminer.six使用）

AndroidにはpdfplumberのC拡張が動かないため、実装が分岐している。
**同じ機能追加は必ず両方のファイルに行う必要がある。**

→ Web版ファイルを全削除してAndroid専用構成に移行することで、この二重管理問題を根本解決した。

---

### コミット一覧

```
37915d1  feat: 進捗バーにページ数・章数カウンターを追加（Web版・10,000ページ対応）
b1e07cc  feat: Android版にも進捗バーのページ数・章数カウンターを追加
5dd5f3a  refactor: Web版ファイル群を削除、Android専用構成に移行
```

---

## 2026-03-21 | PDF処理 進捗バー追加（Web版 + Android版）

### 背景

PDF処理（`/api/add`）は同期ブロッキングで数分かかることがあるが、スピナーのみで進捗が不明だった。
Web版・Android版それぞれにフェーズ別進捗バーを実装した。

---

### 設計判断

#### Web版：バックグラウンドスレッド + ポーリング方式

| 選択肢 | 採否 | 理由 |
|---|---|---|
| SSE（Server-Sent Events） | 不採用 | Flask dev server での安定性が不確か |
| WebSocket | 不採用 | 依存ライブラリが増える |
| バックグラウンドスレッド + GET ポーリング | **採用** | バニラJS のみで完結、シンプル |

- `/api/add` は即座に `job_id` を返す（非同期化）
- フロントが `GET /api/job/<job_id>` を 500ms 間隔でポーリング
- `job_id` と `book_id` を分離（ジョブ追跡用 vs ディレクトリ名）

#### Android版：Chaquopy SAM コールバック方式

- Python → Kotlin のコールバックは `fun interface` を Chaquopy に渡すことで実現
- Chaquopy 15.0.1 では `fun interface`（SAM）が Python から直接 `callback(percent, phase)` として呼び出せる
- コールバックは IO スレッドから呼ばれるが、`MutableStateFlow.value =` への代入はスレッドセーフなので `withContext(Main)` 不要

---

### 実装のポイント

#### 単調増加保証（Web版）

複数スレッドから `_set_progress` が呼ばれても percent が後退しないよう `max(job["percent"], percent)` で保証。

#### エラー時クリーンアップ（Web版）

`_run_job` の except で `shutil.rmtree(target_dir)` を実行し孤立ディレクトリを削除。
`process_pdf` 本体ではなく呼び出し元でクリーンアップする設計にすることで責務を分離。

#### TTL によるメモリ管理（Web版）

完了・失敗後 600 秒で `threading.Timer` が `jobs.pop(job_id)` を実行。
長期稼働時にメモリが増え続けるのを防ぐ。

#### finally でのリセット保証（Android版）

`addBook()` を try/finally 構造にし、成功・失敗いずれの場合も `_processingState.value = ProcessingState()` でリセットされるよう保証。
以前は成功後にのみ `_isProcessing.value = false` を呼ぶ構造だったため、例外時にフラグが残るリスクがあった。

#### ProcessingState への一本化（Android版）

`_isProcessing: Boolean` を `ProcessingState(isProcessing, percent, phase)` に置き換えることで、
「処理中かどうか」「何%か」「どのフェーズか」を単一の StateFlow で管理。UI 側の collectAsState も1箇所で済む。

---

### アーキテクチャの気づき

- **WebアプリとAndroidアプリは完全に独立した実装経路を持つ**。
  Web版は Flask API 経由、Android版は Chaquopy でローカル Python を直接呼ぶ。
  Web版の `/api/add` 非同期化はAndroid版に影響しない（そもそも /api/add を呼んでいない）。

- **`android/app/src/main/python/app.py` は Web版の `app.py` とは別ファイル**。
  Android 専用の引数（`output_dir`, `android_mode`）を持ち、それぞれ独立して管理する必要がある。
  両者に同じ機能を追加する場合は両方を忘れずに変更する。

---

### コミット粒度ルールの策定

今回の実装を機に「Atomic Commit」ルールを CLAUDE.md に追加した。

- 以前：複数ファイルをまとめて 1 コミット（粒度が大きすぎた）
- 以後：1 論理的変更 = 1 コミット、git commit 前に要約提示・承認を得る（Human-in-the-loop）

---

### コミット一覧

```
44131e8  feat(pdf_extractor): run_final_engine に progress_callback 引数を追加
536e505  feat(app): process_pdf に progress_callback を追加・各フェーズで進捗通知
3bbd923  feat(bookshelf): /api/add を非同期化・ジョブ管理システムを追加
a3ac680  feat(index): プログレスバーUI・ポーリングJSを追加
0146fb1  feat(android/app.py): process_pdf に progress_callback 追加・フェーズ通知
7d62784  feat(BookRepository): ProgressCallback インターフェース追加・Chaquopy に渡す
2fce13b  feat(BookshelfViewModel): ProcessingState で進捗管理・_isProcessing を置き換え
a7d999d  feat(BookshelfScreen): CircularProgressIndicator → LinearProgressIndicator + フェーズテキストに変更
fc30f54  docs(CLAUDE.md): Atomic Commit ルール（3-1）を追加
```

---

## 2026-03-21 | ビルドエラー修正 Vol.3（jlink.exe 非ゼロ終了エラー → AGP 8.6.1 アップグレード）

### 背景

`./gradlew assembleDebug` 実行時に以下のエラーで失敗。

```
Execution failed for task ':app:compileDebugJavaWithJavac'.
Error while executing process C:\Program Files\Android\Android Studio\jbr\bin\jlink.exe
finished with non-zero exit value 1
```

---

### 根本原因

AGP と Gradle のバージョン互換性の不一致。

| 項目 | 状態 |
|------|------|
| AGP | 8.1.4 |
| Gradle | **8.9**（非対応） |
| Android Studio JBR | Java 21.0.9 |

AGP 8.1.4 の対応 Gradle は 8.0〜8.1 のみ。Gradle 8.9 が JBR 21 を使って
`jlink`（Java モジュールシステム関連）を呼び出す際、AGP 8.1.4 がその挙動に
対応していないため jlink が非ゼロで終了する。

---

### 試みたアプローチと結果

#### アプローチA（Gradle を 8.3 にダウングレード）: 失敗 ❌

`gradle-wrapper.properties` の `distributionUrl` を `gradle-8.3-bin.zip` に変更。
Android Studio の `Use Gradle from` は `Wrapper` になっていたが、
同じエラーが再発。Android Studio のキャッシュか内部挙動が原因と推測。

#### アプローチB（AGP を 8.6.1 にアップグレード）: 採用 ✅

AGP 8.6.x は Gradle 8.7+ に正式対応。Gradle 8.9 をそのまま維持して
AGP 側を合わせることで解消。

| ファイル | 変更内容 |
|----------|---------|
| `android/settings.gradle` | AGP `8.1.4` → `8.6.1` |
| `android/gradle/wrapper/gradle-wrapper.properties` | Gradle `8.9`（アプローチ A で 8.3 にしたのを戻す） |

Kotlin 1.9.22、Chaquo 15.0.1、KSP 1.9.22-1.0.17 は変更不要（AGP 8.6.1 と互換）。

---

### 学んだこと

- AGP と Gradle のバージョンは Google の互換性マトリクスに厳密に従う。
  - AGP 8.1.x → Gradle 8.0〜8.1
  - AGP 8.6.x → Gradle 8.7+
- Gradle のダウングレードより AGP のアップグレード（方針 B）の方が
  Android Studio のキャッシュ問題を回避できて確実。
- Chaquo Python は AGP 8.1+ 対応を謳っており、AGP 8.6 でも動作する。

---

## 2026-03-21 | ビルドエラー修正 Vol.2（PyMuPDF → pdfminer.six ＋ Compose BOM 更新）

### 背景

前回（Vol.1）の `buildPython/cgi エラー` と `FAB enabled エラー` 修正に続き、新たに 2 種のビルドエラーが発生した。

---

### 発生したエラー

#### エラー1: PyMuPDF の pip install 失敗

```
AssertionError: No match found for pattern=
  'C:\\Program Files*\\Microsoft Visual Studio\\2022\\*'
```

Chaquopy が PyMuPDF を Windows ホスト上でソースビルドしようとするが、
MSVC（C++ ビルドツール）がインストールされていないため失敗。

#### エラー2: HorizontalDivider の Unresolved reference

Compose BOM `2023.10.01` + Kotlin `1.9.10` の組み合わせで
`androidx.compose.material3.HorizontalDivider` が解決できない。

---

### 試みたアプローチと結果

#### アプローチA（PyMuPDF を継続使用）: 却下

- PyMuPDF（fitz）は C 拡張を含む。
- Chaquopy の pip は Android ターゲット向けにホストでクロスビルドを試みるが、
  MSVC がなければビルド不可。
- ホスト環境に Visual Studio を別途インストールする方法は環境汚染リスクがあり不採用。

#### アプローチB（pdfminer.six に切り替え）: 採用 ✅

- `pdfminer.six` は純 Python（C 拡張なし）。
- Chaquopy の pip でそのままインストール可能。
- `LTChar` オブジェクトで文字座標・フォント名・サイズを取得でき、
  PyMuPDF の `rawdict` と同等の情報が得られる。
- **唯一の差分**: Y 軸の原点が異なる。
  - PyMuPDF: 上原点（`top` = ページ上端からの距離）
  - pdfminer: 下原点（`y0`, `y1` = ページ下端からの距離）
  - → `top = page_height - y1` で変換することで既存ロジックをそのまま再利用。

#### HorizontalDivider: Compose BOM 更新で解消 ✅

| 項目 | 変更前 | 変更後 |
|------|--------|--------|
| Kotlin | 1.9.10 | 1.9.22 |
| KSP | 1.9.10-1.0.13 | 1.9.22-1.0.17 |
| Compose Compiler | 1.5.3 | 1.5.10 |
| Compose BOM | 2023.10.01 | 2024.04.01 |

BOM `2024.04.01` は `material3 1.2.1` にマップされており、
`HorizontalDivider` が正式に含まれている。
Kotlin 1.9.22 + Compose Compiler 1.5.10 は公式の対応ペア。

---

### 変更ファイル一覧

| ファイル | 変更内容 |
|----------|---------|
| `android/settings.gradle` | Kotlin / KSP バージョン更新 |
| `android/app/build.gradle` | pip パッケージ変更、Compose Compiler / BOM 更新（2 箇所） |
| `android/app/src/main/python/pdf_extractor.py` | fitz → pdfminer API に全面書き換え |

---

### 学んだこと

- Chaquopy で pip を使う場合は **純 Python パッケージのみ** を選ぶ。
  C 拡張を含むパッケージはホスト環境のビルドツールに依存するため失敗しやすい。
- pdfminer.six の Y 軸は下原点なので、PyMuPDF 互換にするには `page_height - y` の変換が必要。
- Compose BOM のバージョンと Kotlin/Compiler のバージョンは公式の対応表に従って揃える。

---

### コミット

```
1d5f125  fix: PyMuPDF → pdfminer.six に変更し Compose BOM を 2024.04.01 に更新
7cb04ff  docs: 修正後のコミット必須ルールを CLAUDE.md に追加
```
