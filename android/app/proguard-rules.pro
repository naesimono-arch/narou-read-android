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

# Play Core review-ktx 2.0.2（In-App Review）の ReviewManagerKtxKt は GMS Tasks の
# OnSuccessListener を SAM 変換で実装し、生成される合成クラスが GMS 内部アノテーション
# com.google.android.gms.common.annotation.NoNullnessRewrite への参照を残す。この
# アノテーションは play-services-base 側にのみ存在し review-ktx の推移的依存には含まれない
# ＝アプリに載らない。アノテーション参照は実行時に解決されないため欠落しても動作に影響しないが、
# R8 は Missing class をエラー扱いにして minifyReleaseWithR8 を停止させる。
# 2026-07-29 実測: In-App Review 導入後の初回 release ビルドがこれで BUILD FAILED になった
# （handover の「consumer rules で足りる＝自前 keep 不要」の見立ては誤りだったことが確定）。
# gemalto.jp2 と同じ「意図して載せていない任意依存」の宣言で収束させる。
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite

# enum 定数名の防御的固定: ReadingTheme は SharedPreferences に name を永続し
# valueOf() で復元する（MainActivity）＝アプリ更新を跨いだ名前互換が必須。
# enum の name はバイトコード上 <clinit> の文字列リテラル由来で難読化の直接影響は
# 受けないと解されるが、R8 の enum 最適化（unboxing 等）の版差挙動を排除しきれない
# ため未確定要素への保険として防御的に keep する（自アプリの enum のみ・サイズ影響は微小）。
-keepclassmembers enum com.novelreader.** { *; }

# release から android.util.Log 呼び出しを一括除去する（監査 B7: log-leaks-content-identifier）。
# なぜ: 蔵書タイトル・作品 URL・SAF パス（primary:Download/なろう_〇〇.pdf 形＝フォルダ構成と書名）が
# 計7箇所（NewEpisodeCheckWorker:161 / PdfTreeScanner:43,52,62,92 / BookshelfViewModel:525 /
# WebBookImporter:166〔Throwable の message 経由〕）で release logcat に平文で残り、バグレポート同梱・
# READ_LOGS を持つ OEM 診断アプリ・adb logcat 経由で端末外へ出うる。「ログもまた保存層」
# （PdfImportViewModel:100-103 の既存規約）を release では機械的に全面適用する＝各サイトの文言を
# 個別に薄める対処より真因側（release にログを残さない）で断つ。debug ビルドは R8 非適用のため
# ログはそのまま残り診断性は落ちない。
# R8 前提の注意:
#  ・assume 系は最適化の一部＝-dontoptimize 下では適用されない。本アプリは
#    proguard-android-optimize.txt（build.gradle release ブロック）前提で有効。
#  ・呼び出しは引数の文字列連結ごと到達不能として消えるが、戻り値（int）を使う呼び出しは
#    既定値 0 へ置換される（本リポジトリに戻り値を使う Log 呼び出しは無い）。
#  ・Log.wtf は対象外: 「回復不能の報告」でプロセス終了しうる意味論＝ログ出力ではなく挙動なので
#    除去すると振る舞いが変わる（現状使用 0 件だが、将来の追加を無音で無効化しない）。
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
