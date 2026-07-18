# ============================================================
# アプリ固有の R8 keep ルール
#
# 方針: 依存ライブラリの consumer ルール（AAR の proguard.txt / JAR の
# META-INF/proguard/*.pro。R8 が自動でマージ適用する）で守られるものは
# ここに重複して書かない。以下は Gradle キャッシュの実物で同梱を確認済み:
#   - retrofit 2.11.0 …… @GET 等を持つ interface・Signature 属性・Continuation
#     を keep → NarouApiService はこれでカバー
#   - okhttp 4.12.0 / room-runtime 2.6.1（RoomDatabase サブクラス keep）
#   - work-runtime 2.9.1 …… ListenableWorker サブクラスの <init> keep
#     → NewEpisodeCheckWorker（クラス名文字列で WM の永続DBから復元）をカバー
#   - pdfbox-android 2.0.27.0 …… SecurityHandler のリフレクション生成を keep。
#     フォント/CMap 資産は AAR の assets/ 配下＝shrinkResources の対象（res/）外で不可侵。
# ============================================================

# pdfbox-android の JPXFilter は任意プラグイン JP2Android（com.gemalto.jp2）への
# シンボリック参照を持つ（JPEG2000 画像のデコード用）。本アプリは JP2Android に
# 依存しない（テキスト抽出のみで JPX 画像デコード不要・pdfbox は実行時に不在を許容する
# 設計）ため、「意図して載せていない任意依存」であることを宣言する。R8 の実測エラー
# （Missing class com.gemalto.jp2.JP2Decoder ← JPXFilter.readJPX）への根本対処。
-dontwarn com.gemalto.jp2.**

# Moshi codegen の生成アダプタ: 実行時に Util.generatedAdapter() が
# Class.forName(モデルの実行時クラス名 + "JsonAdapter") で解決する（moshi 1.15.1 の
# 実装を逆アセンブルで確認）。文字列組み立てのため R8 は参照を追跡できず、
# 無指定だとアダプタが削除され Narou API の JSON パースが実行時クラッシュする。
# moshi 同梱の moshi.pro にアダプタ keep は含まれない＝アプリ側で書く唯一の必須分。
# アダプタ本体＋コンストラクタの keep に加え、名前ペアリングが崩れないよう
# @JsonClass モデル側の実行時名も保持する。
-keep class com.novelreader.**JsonAdapter { <init>(...); }
-keepnames @com.squareup.moshi.JsonClass class com.novelreader.**

# enum 定数名の防御的固定: ReadingTheme は SharedPreferences に name を永続し
# valueOf() で復元する（MainActivity）＝アプリ更新を跨いだ名前互換が必須。
# enum の name はバイトコード上 <clinit> の文字列リテラル由来で難読化の直接影響は
# 受けないと解されるが、R8 の enum 最適化（unboxing 等）の版差挙動を排除しきれない
# ため未確定要素への保険として防御的に keep する（自アプリの enum のみ・サイズ影響は微小）。
-keepclassmembers enum com.novelreader.** { *; }
