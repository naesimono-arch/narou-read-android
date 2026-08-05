# 0029. 依存バンプの天井は compileSdk ではなく **Kotlin 1.9.22**（Roborazzi 連鎖）＝次は1便でまとめる

- 状態: **Accepted（2026-07-30 に構造確定）／2026-08-05 に1便で実行完了**（顛末は末尾「増補1」）
- 関連: ADR 0009（Robolectric で Compose UI テストを回す＝Roborazzi 依存の出所）／`android/settings.gradle` のコメント（Roborazzi 版固定の正本）／`android/app/build.gradle`

## 背景

依存を上げようとするたび個別に「今回は別便で」と据え置いてきたが、**据え置きが5件溜まって**
2026-07-30 のバンプでまとめて解消することになった。その過程で、据え置きの原因が
個々の依存の事情ではなく**単一の構造**であることが判明した。

天井は **compileSdk ではない**。**Kotlin 1.9.22** である。

## 機序

Kotlin コンパイラは依存 artifact の `JvmMetadataVersion` に受入上限を持つ。
Kotlin 1.9.22 の上限は **1.9.0**（次版が 2.0.0）。ここから2段階に分かれる:

| artifact の `mv` | 該当する依存 | 結果 |
|---|---|---|
| `mv = 2.1.0` | Compose 1.11 系・work 2.11 系 | **機械的に確実死**（読み込み時点で弾かれる） |
| `mv = 2.0.0` | Compose 1.9+・lifecycle 2.9+・tracing 1.3+ | ベンダが **KGP 2.0.0+ 必須と明言** |

そして **Kotlin 自体を上げられない理由が Roborazzi にある**——
`Roborazzi 1.30.1` が Kotlin 1.9.22 ビルドであるため、Kotlin を上げると
golden 画像回帰（ADR 0009 で採用した検証基盤）が壊れる。連鎖はここで閉じる。

## 決定

**次に依存を動かすときは「Kotlin 2.x ＋ compose compiler plugin ＋ Roborazzi」を1便で上げる。**
個別の依存だけを上げようとしても上の表に必ず突き当たるので、分割しても進まない。

## なぜ ADR にするか

この構造を記録しないと、**また「別便で」の据え置きが溜まる**。
実際それが5件溜まったのが本 ADR の発端で、原因は「毎回その依存固有の問題として扱っていた」こと。
天井が単一であると分かっていれば、次回は最初から1便として計画できる。

## 現時点の残り

- `tracing-ktx` は 2026-07-30 のバンプでも**据え置き**（1.3.0 が Kotlin 2.0 ビルド＝上限超え）。
  本 ADR の1便に同乗させる。→ **2026-08-05 に解消**（増補1 の表を参照。座標は `tracing` へ寄せた）
- `activity-compose` は宣言 1.8.1 に対し**解決 1.8.2**（material3 が推移要求・従前から）。
  天井とは無関係だが、宣言を実態へ揃えると読み違いが減る（`handover.md` に小項目として残置）。

## 帰結

Kotlin 2.x 便は Compose コンパイラのプラグイン移行（KGP 2.0 で `kotlin("plugin.compose")` へ分離）を伴い、
**golden の再記録が必要になる可能性が高い**（レンダリング差分が出れば `recordRoborazziDebug`）。
1便が大きくなるのは避けられないので、他の変更と混ぜず単独のブランチで通すこと。

---

## 増補1（2026-08-05・実行完了）: 連鎖は3点ではなく**5点**だった

本文の段階表は Kotlin・compose compiler・Roborazzi の3点で天井を説明していたが、実際に1便を通すと
**Room** と **AGP/R8** が第4・第5の連鎖として現れた。**どちらも本文の表からは導けない**——表は
「依存 artifact の metadata バージョンを Kotlin コンパイラが受理できるか」という単一の軸で書かれており、
下の2つはその軸の外側（アノテーション処理系の実装／出荷ビルドの shrinker）で起きるためである。

### 採用した組合せ（すべて一次ソース確認済み・全ゲート通過で実証）

| | 前 | 後 | 決め手 |
|---|---|---|---|
| Kotlin (KGP/parcelize) | 1.9.22 | **2.2.21** | JetBrains の KGP 互換表で 2.2.20–2.2.21 は Gradle 7.6.3–8.14・AGP 7.3.1–8.11.1 が fully supported＝wrapper 8.11.1 を動かさずに済む最上位 |
| compose compiler plugin | （`composeOptions` 1.5.10） | **2.2.21** | KGP 2.0 で Kotlin リポジトリへ移管。版は Kotlin 本体と一致必須 |
| KSP | 1.9.22-1.0.17 | **2.2.21-2.0.5** | 2.2.x 系の配信は `-2.0.x`＝KSP2 のみ（KSP1 の `-1.0.x` は 2.1.20 で打ち止め） |
| **Room** | 2.6.1 | **2.8.4** | 下記の第4連鎖 |
| Roborazzi | 1.30.1 | **1.70.0** | 1.30.1 が Kotlin 1.9.22 ビルドで Kotlin 側と不可分 |
| **AGP** | 8.9.1 | **8.10.1** | 下記の第5連鎖 |
| tracing | tracing-ktx 1.2.0 | **tracing 1.3.0** | 本文「現時点の残り」の解消。1.3.0 で ktx は空 shim 化し実体が tracing-android へ移るため、座標も基底へ寄せた（`Trace.beginSection` しか使っておらず ktx の拡張関数は不要） |

Gradle wrapper（8.11.1）・compileSdk/targetSdk（36）・JDK（17）・Compose BOM（2025.07.00）は**動かしていない**。

### 第4の連鎖: Room 2.6.1 は KSP2 で動かない

Kotlin 2.2.x には **KSP1 が存在しない**（KSP2 のみ）。ところが Room 2.6.1 の XProcessing は
KSP2 が返す void の JVM シグネチャ `V` を解釈できず、DAO の `Unit` 返却メソッドを処理した時点で
`java.lang.IllegalStateException: unexpected jvm signature V` を投げて `kspDebugKotlin` が落ちる（実測）。
つまり **Kotlin を上げると Room も上げざるを得ない**。Room の KSP2 対応は 2.7.0 が最初
（公式リリースノート: "Support for KSP2 is also added and is recommended when using Room with Kotlin 2.0 or higher."）。
本便は最新安定の **2.8.4** を採った（room-compiler 2.8.4 が要求する kotlin-stdlib が 2.2.0＝KGP 2.2.21 と同系列）。

副作用として `room-ktx` は 2.7.0 で `room-runtime` へ統合され空 artifact になったので宣言から削除した
（唯一の利用 API `androidx.room.withTransaction` は runtime 側にあり、呼び出し側は無変更）。

**スキーマ JSON は書式だけ変わる**（Room 2.7+ の直列化が既定値を省略するようになったため）:
`"notNull": false` と 空の `indices` / `foreignKeys` / `views` が出力されなくなり、`21.json` が 33 行減る。
**identityHash・createSql・列定義はすべて不変**であることを機械照合済み（既定値を補って正規化すると完全一致）。
`MigrationShapeCoverageTest` は `optJSONArray("indices")` で読むため省略に耐える。
過去版の JSON（3〜20）は再生成されず旧書式のまま残るが、新しいパーサは明示値も読めるので混在して問題ない。

### 第5の連鎖: AGP 同梱の R8 が Kotlin 2.2 の @Metadata を読めない

Kotlin だけ上げると `assembleRelease` は**通るが**
`R8: An error occurred when parsing kotlin metadata` を多数吐く。原因は AGP が R8 を同梱していること:
AGP 8.9.1 の同梱は **R8 8.9.32**（mapping.txt 冒頭の `compiler_version` で実測）で、Android 公式の
Kotlin/D8/R8 互換表が定める Kotlin 2.2 の必要 R8 **8.10.21** を下回る。R8 は読めなかったクラスの
メタデータを書き換えずに素通しするため、ビルドは緑のまま**出荷成果物だけが公式非対応の状態**になる
——「テストが緑でも壊れている」型なので、警告を放置せず AGP 8.10.1（同梱 R8 **8.10.24**）へ上げて解消した。
8.11 以降にしなかったのは最小 Gradle が 8.13 になり wrapper 更新が連鎖するため（8.10 の最小 Gradle は 8.11.1＝現状と同値）。

同時に、KGP 2.x は `:app` と `:macrobenchmark` が別々に kotlin プラグインを適用する構成に対し
「多重ロードは非対応・ビルドを壊しうる」と警告するようになったため、ルート `build.gradle` で
AGP と KGP を `apply false` 宣言して単一クラスローダに寄せた（KGP だけをルートへ上げると AGP のクラスを
解決できず構成が落ちる＝両方を同じ階層に置く必要がある）。

### 本文の予想との差

- **golden の再記録は不要だった**。本文は「レンダリング差分が出る可能性が高い」と予想したが、
  100枚すべて `unchanged`（changed=0）で verify を通った。Compose BOM を動かさなかったため、
  コンパイラ更新だけでは描画結果が変わらなかったということ。
- **アプリのコード（`src/main`/`src/test`/`androidTest`）は1行も変えていない**。Kotlin 2.x 化で要った
  追従はビルド構成ファイルだけだった。
- 残った警告: Kotlin 2.2 が新設した「アノテーションの既定ターゲットが将来 property にも及ぶ」告知
  （KT-73255）が Moshi の `@Json` 付きコンストラクタ引数で多数出る。**挙動を変える指定なので本便では触らない**
  （`-Xannotation-default-target` の指定は現状維持側・変更側のどちらにも倒せる＝意図を決めてから別便で）。

### 教訓（次に同種の1便を組むとき）

天井を「metadata バージョンの受理」だけで捉えると連鎖を読み違える。**Kotlin を上げるときは
〈コンパイラが読むもの〉に加えて〈コンパイラの外でバイトコードを触るもの〉——注釈処理系（KSP→Room 等）と
shrinker（R8＝AGP 同梱）——も同じ表に載せること**。後者2つは「ビルドが通るか」では検出できず、
前者は KSP1 廃止で退路が無い。
