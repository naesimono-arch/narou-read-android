# Auto Backup の設計を詰める＋ON/OFF トグル（設計ドラフト・裁定待ち）

- 状態: **ドラフト**（ADR 化は人間の裁定後。裁定点は §8）
- 対象ブランチ: `feat/round-2026-08-25`
- 起点: handover「Auto Backup の設計を詰める＋ON/OFF トグル」（2026-08-26 ユーザー提起）
- 上位の判断: `docs/decisions/0015-layered-auto-backup.md`（層別＝メタデータのみ運ぶ。本文は運ばない）
- この文書が触らないもの: `docs/store/**`（掲載文は設計確定まで保存先を約束しない）・実装（Kotlin/Manifest/XML）

---

## 1. 現状の対象／除外（XML の実物から確定）

### 1.1 まず訂正すべき前提: **「除外ルールのエントリ」は 0 個**

`backup_rules.xml` と `data_extraction_rules.xml` のどちらにも `<exclude>` 要素は**1つも無い**。
両ファイルとも `<include>` だけの列挙で、除外は次の 2 種類でしか成立していない。

1. **構造的除外** — 公式ガイドの規則「`<include>` を1つでも書くと既定の全件バックアップは止まり、列挙したものだけが対象になる」。
   `novels/`（本文）はこれで落ちている。XML のコメントも「include を1つでも書くと対象は include 列挙のみ」と自ら書いている。
   明示 `<exclude path="novels/">` を書けないのは、`<exclude>` が `<include>` した経路の**内側**にしか置けない仕様のため
   （`domain="file"` の include は `datastore/` だけ＝`novels/` はその外側）。この lint 制約は `backup_rules.xml` のコメントに記録済み。
2. **プラットフォーム既定の除外** — `getCacheDir()` / `getCodeCacheDir()` / `getNoBackupFilesDir()` は
   **XML で include しても必ず除外される**（公式ガイド明記）。§3 の分岐判定にそのまま効く。

Manifest 側（`android/app/src/main/AndroidManifest.xml:34-37`）は
`allowBackup="true"` ＋ `dataExtractionRules="@xml/data_extraction_rules"` ＋ `fullBackupContent="@xml/backup_rules"` の3属性のみ。
**`android:backupAgent` も `android:fullBackupOnly` も宣言していない＝バックアップ経路に自前のコードは1行も無い**（この事実が §6 案A の最大の資産）。

### 1.2 include エントリ → 実ファイルの対応

`cloud-backup` と `device-transfer` は同一3行（ADR 0015 の「全経路で結果を揃える」判断どおり）。

| include 行 | 実ディレクトリ | 実際に入る中身 | コード根拠 |
|---|---|---|---|
| `domain="database" path="."` | `/data/data/com.novelreader/databases/` 配下**全部** | `novel_reader_db`（＋WAL/SHM） | `data/AppDatabase.kt:390`（`Room.databaseBuilder(..., "novel_reader_db")`） |
| 同上（**意図していない副作用**） | 同上 | WorkManager の内部 DB（`androidx.work.workdb` 系） | `build.gradle:320` で `work-runtime-ktx` を導入・`NovelReaderApplication.kt:290` で `WorkManager.getInstance` を呼ぶ＝`databases/` に生える。ルールは DB を**選別していない** |
| `domain="sharedpref" path="."` | `/data/data/com.novelreader/shared_prefs/` 配下**全部** | `app_prefs.xml`（アプリ設定の単一置き場） | `PrefKeys.kt:19`（`FILE_APP_PREFS = "app_prefs"`）。読み書き箇所は `MainActivity`/`BookshelfViewModel`/各スキン等 |
| 同上（**意図していない副作用**） | 同上 | WebView が置く prefs 等、ライブラリ由来の `*.xml` | WebView は取込導線で常用（ADR 0011/0012）。実ファイル名は**実機で要確認**（§7） |
| `domain="file" path="datastore/"` | `/data/data/com.novelreader/files/datastore/` | `narou_search_history.preferences_pb`（検索履歴） | `narou/SearchHistoryStore.kt:81-84`（`preferencesDataStore(name = PrefKeys.FILE_NAROU_SEARCH_HISTORY)`） |

### 1.3 対象外になっているもの（実パス）

| 実パス | 落ちる理由 | コード根拠 |
|---|---|---|
| `files/novels/<bookId>/index.html`・`chap_N.html` | include に無い（構造的除外）＝ADR 0015 の意図どおり | `BookEntity.NOVELS_SUBDIR` / `BookEntity.resolveHtmlDir`（`data/BookEntity.kt:100-107`） |
| `cache/pdf_import/<ncode>.pdf` | **プラットフォームが常に除外**（`getCacheDir()`）。include で救うことすら不可能 | `NarouPdfCache.kt:22-25`（`SUBDIR = "pdf_import"` / `dir(cacheDir)`） |
| `cache/temp_<bookId>.pdf` | 同上 | `PdfBookImporter.kt:103` |
| SAF の永続 URI 権限 | そもそもバックアップの対象という概念に無い（アンインストールで必ず失効） | 実機実測＝`docs/knowledge/auto-backup-does-not-restore-uri-permissions.md` |
| 端末の共有ストレージ（`Download/` の元 PDF 等） | Auto Backup は共有ストレージを運ばない | 公式ガイドの domain 表に該当なし |

### 1.4 `sharedpref path="."` が運んでしまう「新端末では意味を持たない値」

`app_prefs` はファイル単位で丸ごと運ばれるので、以下も一緒に新端末へ着く（キー単位の除外は Auto Backup にはできない）。

| キー | 新端末での意味 | 現状の防御 |
|---|---|---|
| `PDF_LIBRARY_TREE_URI` | **端末固有の `content://`＝無意味** | `PrefKeys.kt:118-125` が自ら明記。読み出し側（`BookshelfViewModel`）が「生きた権限があるときだけ記憶済みと扱う」で防御済み |
| `DIAG_SESSION_OPEN` / `DIAG_LAST_SEEN_AT` / `DIAG_LAST_SCREEN` | 旧端末の前プロセス状態＝異常終了の誤検知要因 | 防御なし（要確認） |
| `REIMPORT_SWEEP_SEEN_IDS` | 旧端末で「あとで」した欠落の指紋 | 実害は限定的。`shouldShowReimportSweep = (missing − seen).isNotEmpty()`（`domain/ReimportPlan.kt:220-221`）で、復元直後は**全冊**が missing に入るため差集合は必ず非空＝バナーは出る |
| `SKY_HIGH_LOAD_M` / `SHIORI_HIGH_LOAD_K` / `SHIORI_DEBUG_TIP_INDEX` | debug 限定の試作トグル | release では読んでも捨てられる（`BuildConfig.DEBUG` ガード下） |

---

## 2. Auto Backup が復元される条件（公式ガイド照合）

- **復元は「インストール時」だけ**。Play からの導入・端末セットアップ時の一括再インストール・`adb install` のいずれも、
  APK 導入後・ユーザーが起動する前に走る。**アプリ内から復元を要求する手段は無い**
  （`BackupManager.requestRestore` は `@Deprecated`＝Android P 以降 no-op。AOSP `core/java/android/app/backup/BackupManager.java`）。
- **経路は2つ**で本アプリは同一ルールを宣言している。
  - `cloud-backup`（Google アカウント経由）: **1アプリ1ユーザーあたり 25MB**。超過すると `onQuotaExceeded()` が呼ばれ、
    **クラウドへは何も上がらない**（部分アップロードではない）。
  - `device-transfer`（D2D）: 公式ガイドに容量上限の記載なし。
- **25MB は本アプリの実務上の制約になっていない**。対象はメタデータ層だけ＝蔵書数に比例するが本文を持たないので数十KB〜数MB 級。
  → **「25MB に当たるからトグルが要る」という論拠は使えない**（ADR 0015 の層別が既にその問題を解いている）。
- **ユーザー側の停止手段**: 端末の Settings > System > Backup（Android 9+）。
  **公式ガイドは「アプリ単位で利用者がバックアップを切る」制御を記載していない**＝OS 側の粒度はアカウント／端末単位と読むのが安全。
- **アプリから OS のバックアップを止める API は無い**:
  `BackupManager.setBackupEnabled` / `isBackupEnabled` はいずれも `@SystemApi` ＋ `@RequiresPermission(android.Manifest.permission.BACKUP)`
  （AOSP `BackupManager.java`）＝通常アプリからは呼べない。`dataChanged()` は key-value バックアップへの変更通知で、Auto Backup の可否には効かない。

---

## 3. 「DB だけ復元され本文が無い」状態はアプリ上どう見えるか（実コードで裏取り）

### 3.1 検出と提示（実装済み・graceful degrade は完成している）

1. **起動時に全冊判定**: `viewmodel/BookshelfViewModel.kt:365-384` が `isContentMissing = { !it.hasContent(filesDir) }` で全蔵書を走査。
   `hasContent`（`data/BookEntity.kt:90-94`）は「`index.html` の実在」＋「torn（章ファイル欠け）でない」の2段。
   復元直後は `files/novels/` がディレクトリごと無いので **全冊が欠落判定**になる。
2. **棚**: ヘッダ直下に一括バナー（`ui/ReimportSweepBanner.kt:61-72`）。
   文言は「本文データが見つからない本が N冊あります」＋「端末の変更やバックアップ復元のあとに起きることがあります。本の情報と読書位置は残っています。」
   ＋ CTA「まとめて再取込」／「あとで」。冊ごとの状態行は `reimportStatusLabel`（`ReimportPlan.kt:250-253`）。
3. **本を開いたとき**: `ui/NativeReadingScreen.kt:423-475`。`resolvedFile == null` で `ReadingErrorScreen` に落ち、
   「本文データがこの端末にありません。同じ PDF を取り込み直すと続きから読めます」＋本棚へ戻る導線のみ。
   同 464-468 のコメントが明記するとおり **進捗 DB は一切触らない**＝読書位置・栞は保持される。

→ **クラッシュも行き止まりも無い。ADR 0015 が約束した graceful degrade は実装として成立している。**

### 3.2 ただし「戻せるか」は出自で決定的に分かれる（ここが案の分かれ目）

`classifyReimport`（`domain/ReimportPlan.kt:92-115`）の4+1分岐を、**新端末への復元**という条件で当てはめる。

| 蔵書の出自 | 落ちる分岐 | 新端末での結果 | 根拠 |
|---|---|---|---|
| Web 取込（`sourceUrl` あり） | ④`AutoWeb` | **自動で戻る** | `sourceUrl` は books 列＝DB と一緒に復元される |
| PDF 取込（`sourceUri` あり） | ①`AutoPdf` ではなく②`PickPdfPermissionLost` | 永続 URI 権限が復元されない＝**①は構造的に常に0件** | 実機実測＝`docs/knowledge/auto-backup-does-not-restore-uri-permissions.md` |
| なろう縦書きPDF（`ncode` と `contentSha256` のみ） | ①'`AutoCachePdf` が**成立せず** ③`PickPdfNoRecord` | `cache/pdf_import/` はプラットフォームが常に除外＝新端末に cache は無い | 公式ガイド（cacheDir 常時除外）＋ `NarouPdfCache.dir(cacheDir)` |
| ②③の救済（案X フォルダ走査） | `contentSha256` で機械照合 | **元 PDF が新端末に在るときだけ**戻る | `domain/PdfFolderScan.kt`。共有ストレージは Auto Backup が運ばない |

### 3.3 ここから出る、トグルとは独立した2つの結論

- **結論①（守れる約束）**: 現状の Auto Backup は「新端末で**棚・読書位置・栞・設定**を取り戻す」までは確実に効く。
  本文が自動で戻るのは Web 本だけ。PDF 本は、利用者が元 PDF を自力で新端末へ運んでいる場合に限り走査で戻る。
- **結論②（守れない約束・既存の穴）**: **なろう縦書きPDF 取込の本は、機種変更で ③`PickPdfNoRecord` へ落ちる。**
  ③は「PDF のある場所を教えてください」と SAF フォルダ選択を促すが、その PDF は元々アプリの cache にしか存在しない
  ＝**利用者が実行できない提案**になる。`ReimportPlan.AutoCachePdf` の KDoc が 2026-07-30 実機実測で「守れない約束」と名指した状態が、
  **もっとも起こりやすい経路（機種変更）でそのまま再発する**。
  ⚠️ この穴はトグルの有無で消えない。むしろ「バックアップ ON＝安心」という期待を作る分だけ深くなる。
  （原理的な解はある: `ncode` は DB に残るので「ncode から再ダウンロード→再変換」の分岐を足せる。
  DL 経路は `viewmodel/PdfImportViewModel.kt:113` が既に持っている。**本設計とは別件**＝§8-4 の裁定対象。）

---

## 4. トグルを付ける場合の実現手段（実装事実として確認したもの）

### 4.1 使えない手段

| 手段 | なぜ使えないか |
|---|---|
| `android:allowBackup` を実行時に切り替える | **マニフェスト属性＝ビルド時固定**。公式ガイドに実行時変更の記載は無く、そもそも `<application>` の静的属性 |
| `dataExtractionRules` / `fullBackupContent` を切り替える | XML リソース＝ビルド時固定。リソース修飾子に「ユーザー設定」の軸は無い |
| `BackupManager.setBackupEnabled(false)` | `@SystemApi` ＋ `@RequiresPermission(BACKUP)`（AOSP 実装）＝通常アプリからは呼べない |
| `BackupManager.dataChanged()` を呼ばない | key-value バックアップの変更通知であって、Auto Backup の可否には効かない |

### 4.2 唯一の正攻法＝カスタム `BackupAgent`

```
AndroidManifest.xml:
  android:backupAgent=".NovelReaderBackupAgent"
  android:fullBackupOnly="true"      ← Auto Backup + BackupAgent の組で公式が要求
```

```kotlin
class NovelReaderBackupAgent : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        // OFF なら super を呼ばない＝何も書き出さない（既存 XML ルールの適用そのものを止める）
        if (backupOptedOut()) return
        super.onFullBackup(data)
    }
    override fun onBackup(old: ParcelFileDescriptor?, data: BackupDataOutput?, new: ParcelFileDescriptor?) {} // key-value 不使用
    override fun onRestore(data: BackupDataInput, appVersionCode: Int, new: ParcelFileDescriptor?) {}         // 同上
}
```

**必ず踏まえるべき制約（公式ガイド）**:

- **restricted mode で動く**: バックアップ／復元中、アプリは制限モードで起動され、
  **`Application` のサブクラスは生成されず基底 `Application` が使われる・ContentProvider は初期化されない・メイン Activity も起動しない**。
  → `NovelReaderApplication` の初期化に依存するものは**エージェント内で一切使えない**。
  → **オプトアウト値は `SharedPreferences("app_prefs")` に置くのが唯一安全**（`Context` API だけで読める）。
  Room / DataStore をエージェント内で開くのは避ける（DataStore は suspend＋シングルトン管理、Room は**まさにバックアップ対象のファイル**を開くことになる）。
- **復元側**: `onRestoreFile` で捨てることもできるが、復元はインストール直後・ユーザーが起動する前に走るので、
  そこで読めるオプトアウト値は「旧端末で書いた値がバックアップに入っていた場合」だけ＝意味が入れ子になる。
  **設計としては「バックアップ側だけを制御し、復元は来たものを受ける」で足りる**（OFF の人はそもそもバックアップが空）。
- **既存のバックアップは消えるのか**: OFF にした時点で**既にサーバー上にあるデータセット**がどうなるかはトランスポート依存で、公式ガイドに記載が無い。
  → **「OFF にすれば消えます」とは書けない**。文言は「これ以降はバックアップされません／すでに保存されたものは端末設定または Google アカウントのバックアップ管理から削除できます」に留める必要がある。

### 4.3 BackupAgent を入れる代償（案C の実質コスト）

- 現状は「宣言 XML だけ・動くコードゼロ」＝**壊れる余地が構造的に無い**。エージェント導入はこれを捨てる。
- エージェントが例外を投げれば**そのバックアップは静かに失敗する**（ユーザーにも開発者にも通知されない）。
- **既定ゲートで守れない**: JVM 単体テストは restricted mode を再現できず、androidTest でも実質検証不能。
  検証手段は実機／エミュの `bmgr` 手順だけ（§7）＝CI に載らない永続的な検証負債になる。

---

## 5. 付けない場合の説明責任

- `docs/store/privacy-policy-draft.md` §5 が既に「この機能は Android OS が提供するもので、**端末の設定からいつでも無効にできます**」と書いている。
  ＝**「止められない」わけではない**。ただし止められる粒度がアプリ単位ではなくアカウント／端末単位である点は書けていない（§2 参照）。**ここは精密化の余地**（裁定 §8-5）。
- `docs/store/data-safety-draft.md` は Auto Backup を「収集なし」で申告する裁定済み（2026-07-29）。
  同 §Q2 の保険申告例には「必須か任意か: **任意（端末設定でオフ可）**」とある。
  → **アプリ内トグルは Play のデータセーフティ申告を有利にしない**（Q1「いいえ」で通す現行裁定では申告項目そのものが生じない）。
  **トグルの動機として Play 申告は使えない**。
- 残る説明責任の本体は「設定画面を縦に読んでも、読書記録がどこへ行くのかが**一度も出てこない**」こと。
  現行 `SettingsScreenK.kt` の節構成は 表示 / 通知 / データ（**`BuildConfig.DEBUG` 限定**・`:245`）/ 開発（debug 限定）/ つかいかた / このアプリ
  ＝**release ビルドではデータに関する節が1つも出ない**。

---

## 6. 案（A / B / C。C' はどの案とも独立に併走できる衛生改善）

### 案A: トグルを付けない（現状維持を明文化するだけ）

- 変更: ADR として「トグルを付けない」判断を記録。プライバシーポリシー §5 の1文を精密化（粒度）。**UI 変更なし。**
- 長所:
  - バックアップ経路に**動くコードがゼロ**という現状の強い安全性を維持（§4.3）。
  - 運ぶのはメタデータ数十KB。25MB 上限は実務上当たらない（§2）＝技術的な動機が無い。
  - restricted mode・`bmgr` 検証・エージェント例外という永続的な負債を1つも背負わない。
- 短所:
  - 「読んだ本の一覧が Google に上がる」ことを気にする利用者に、アプリ内で応える手段が無い。
  - 設定を縦に読んでも保存先の話が出てこない（説明すら無い）＝§3.3 の結論②（機種変更で PDF 本の本文が戻らない）も**事前に伝えられない**。

### 案B: 説明だけ置く（トグルなし・情報行1本）

- 変更: 設定に **release でも出る**「データ」節を作り、情報行「端末を替えたときの引き継ぎ」を1行置く。
  タップで説明ダイアログ（引き継がれるもの／引き継がれないもの／止め方は端末の設定）。**トグルは持たない。**
- **UI あり**（モック対象）。
- 長所:
  - 案A の安全性（バックアップ機構に触れない）を**そのまま保ったまま**、案A の短所だけを消す。
  - **§3.3 結論②を事前に伝えられる**＝「本文は引き継がれません。機種変更のあとは同じ PDF を取り込み直してください」を、
    事故が起きる前に言える。これは現状もっとも実害のある情報の非対称の解消になる。
- 短所:
  - 「説明されたが止められない」＝人によっては不満が増す。ただし止める手段（端末設定）は説明文の中で案内できる。

### 案C: トグルを付ける（BackupAgent・説明とセット）

- 変更: 設定「データ」節にトグル行「読書記録の引き継ぎ」＋副文。**既定 ON**（既存利用者の挙動を変えない）。
  実装は §4.2 の `BackupAgent`＋`fullBackupOnly`。オプトアウト値は `app_prefs` に新キー。
- **UI あり**（モック対象）。案B の説明行を内包する（トグルだけ置いて説明が無い形は採らない）。
- 長所:
  - 利用者が選べる。プライバシーポリシー §5 を「アプリの設定からも無効にできます」に強化できる。
- 短所:
  - (1) §4.3 の代償を全部背負う（動くコード・静かな失敗・CI 外の検証負債）。
  - (2) **OFF にした利用者は機種変更で棚・読書位置・栞を全損する**＝ADR 0015 が Critical と呼んだ状態へ、**自ら戻る道を UI で提供する**ことになる。
    代替のエクスポート手段は存在しない（ADR 0015 が「将来の選択肢」として保留したまま）。→ **OFF の代償を実感させる文言設計が必須。**
  - (3) 「OFF にすれば既に上がったものが消える」とは書けない（§4.2）＝トグルの意味を正確に伝える文言が案B より難しい。
  - (4) Play 申告上の利得は無い（§5）。

### 案C'（独立の衛生改善・利用者に選ばせる話ではない）

現状の include が**選別していない**ぶんを絞る。`<exclude>` は `<include>` した経路の内側になら置ける
（公式ガイドの例: `<include domain="sharedpref" path="."/>` ＋ `<exclude domain="sharedpref" path="device.xml"/>`）。

- DB: `path="."` → `novel_reader_db` に絞る（WorkManager の内部 DB を落とす）。⚠️ **WAL/SHM の扱いは実機で要確認**。
- sharedpref: `path="."` → `app_prefs` に絞る、または `<exclude>` でライブラリ由来の prefs を落とす。
  ⚠️ **`path` に `.xml` を付けるかは公式ガイドの記述が矛盾している**（例は `path="device.xml"`、本文は「拡張子なし」）＝**実機で要確認**。
- キー単位の除外は Auto Backup にはできない（ファイルが最小粒度）。`DIAG_*` 等をキー単位で捨てたいなら
  **復元後の初回起動で掃除するコード**が要る（restricted mode の外＝`Application` 側で安全に書ける）。
- どの案を採っても独立に実施できる。ただし**「宣言を絞る＝これまで運ばれていたものが運ばれなくなる」ので、
  実機 `bmgr` 検証（§7）とセットでなければ入れない**。

### 案の比較

| | 案A | 案B | 案C |
|---|---|---|---|
| バックアップ経路に動くコード | なし | なし | **あり（BackupAgent）** |
| CI で守れるか | 該当なし | 表示だけ＝守れる | **守れない（実機 bmgr のみ）** |
| 利用者が選べるか | 端末設定のみ | 端末設定のみ（案内あり） | アプリ内で選べる |
| §3.3 結論②を事前に伝えられるか | いいえ | **はい** | はい |
| OFF による全損リスクの新設 | なし | なし | **あり** |
| Play データセーフティへの影響 | なし | なし | なし |

---

## 7. どの案でも必要な実機検証（`bmgr`）

公式のテスト手順（`developer.android.com/guide/topics/data/testingbackup`）:

```
adb shell bmgr enable true
adb shell bmgr list transports
adb shell bmgr transport com.google.android.gms/.backup.BackupTransportService
adb shell bmgr backupnow com.novelreader
# D2D を試すとき:
adb shell settings put secure backup_enable_d2d_test_mode 1
adb shell bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport
adb shell bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport
```

⚠️ **蔵書のある実機で uninstall→再インストールをやらない**。本文が全損する
（`docs/knowledge/upload-signing-verify-wipes-device-library.md`＝実際に読書位置・追加日を永久に失った実例）。
**エミュレータか捨て端末で行う**（`/emulator-verify`）。

確認すべき現物（§1 の「要確認」を潰す）:

1. 復元後の `databases/` の**実ファイル一覧** — WorkManager の内部 DB が実際に運ばれているか。
2. 復元後の `shared_prefs/` の**実ファイル一覧** — WebView 等ライブラリ由来の prefs が運ばれているか。
3. 案C' を採るなら、`path` の書式（`.xml` の有無・WAL/SHM）を**変更前後の実ファイル差分**で確定する。
4. 復元直後の棚が §3.1 のとおり（バナー＋全冊欠落バッジ、読書位置は保持）になること。
5. なろう縦書きPDF 本が §3.3 結論②のとおり ③`PickPdfNoRecord` へ落ちること（＝穴の実在確認）。

---

## 8. 人間が裁定すべき点

1. **案A / B / C のどれか**。B と C は排他ではない（C は B を内包する）。
   設計者の所見: **技術的な動機は無く（25MB は当たらない・Play 申告に効かない）、動機は「選ばせるか／伝えるか」だけ**。
   伝える価値（§3.3 結論②の先出し）は明確にあるが、選ばせる価値は「OFF による全損リスクの新設」と釣り合うかが判断そのもの。
2. 案C を採るなら **既定値**（ON 固定を推す）と、**OFF の代償の文言**をどう書くか。
3. **案C'（宣言の精度上げ）を単独で進めてよいか**。これは利用者に選ばせる話ではなく衛生改善で、どの案とも独立。
4. **§3.3 結論②（なろう縦書きPDF 本が機種変更で実行不能な提案へ落ちる）を別件として handover へ立てるか。**
   原理的な解（`ncode` から再 DL→再変換の分岐追加）は既存経路の再利用で書ける。**本設計より実害が大きい可能性がある。**
5. プライバシーポリシー §5 の1文精密化（止められる粒度がアプリ単位ではなく端末／アカウント単位）を、この便でやるか。

---

## 9. 一次ソース

- 公式ガイド `developer.android.com/identity/data/autobackup` — 25MB/`onQuotaExceeded`・cacheDir 常時除外・
  `<include>` があると列挙のみが対象・`<exclude>` は include の内側のみ・domain 表・restricted mode（Application サブクラス非生成／ContentProvider 未初期化）・
  復元はインストール時のみ・BackupAgent の override 手順（`fullBackupOnly` と `super.onFullBackup`）
- 公式ガイド `developer.android.com/guide/topics/data/testingbackup` — `bmgr` 手順・D2D テストモード
- AOSP `platform/frameworks/base` `core/java/android/app/backup/BackupManager.java` —
  `setBackupEnabled`/`isBackupEnabled` が `@SystemApi` ＋ `@RequiresPermission(BACKUP)`、`requestRestore` が `@Deprecated`（P 以降 no-op）
- リポジトリ内: `docs/decisions/0015-layered-auto-backup.md`／
  `docs/knowledge/auto-backup-does-not-restore-uri-permissions.md`／`docs/knowledge/upload-signing-verify-wipes-device-library.md`
