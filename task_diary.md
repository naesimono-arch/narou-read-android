# Task Diary

作業記録・失敗と成功のログ。

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
