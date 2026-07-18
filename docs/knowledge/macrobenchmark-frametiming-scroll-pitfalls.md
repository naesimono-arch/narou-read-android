# Macrobenchmark スクロール計測（FrameTimingMetric）の沈黙失敗3クラス

2026-07-17・本棚スクロール jank ベンチ（`BookshelfScrollBenchmark`）の実装で実測した、
**エラーにならずに壊れる**（または原因から遠い場所で壊れる）3つの落とし穴。OEM 非依存の一般則。

## 1. `startupMode = COLD` は「setupBlock の後」に対象プロセスを force-stop する

起動ベンチの公式サンプルが launch を **measureBlock** に書くのはこの仕様のため。
スクロール計測で `setupBlock { startActivityAndWait() }` × `startupMode=COLD` と書くと、
起動→1秒強で force-stop→**ホーム画面をフリングして計測**になる（logcat 実測:
`START …` の 1.2 秒後に `Force stopping …`・`Displayed` は一度も出ない）。
失敗の出方は遠い場所＝計測完了後の `IllegalArgumentException: At least one result is
necessary, 0 found for frameDurationCpuMs`（対象パッケージのフレームがトレースに1枚も無い）。

**処方**: スクロール系は `startupMode = null` にし、反復間のコールド性が要るなら
setupBlock 冒頭で `killProcess()` を自前で呼ぶ。

## 2. launcher も scrollable を持つ＝「scrollable 待ち」では未起動を検知できない

`device.wait(Until.hasObject(By.scrollable(true)))` はホーム画面でも真になる
（launcher のワークスペース/検索面が scrollable）。上記1の事故が素通りした共犯。
**処方**: 起動後に `By.pkg(TARGET_PACKAGE)` の出現を検証し、来なければ即 fail する
「前面ガード」を setupBlock に置く（実装現物＝`BookshelfScrollBenchmark` の setupBlock）。

## 3. Compose の UI は UiObject2 の使い回しで StaleObjectException

スクロールでセマンティクスツリーが変わる UI（本棚は発見帯の退避 collapse 等で変わる）では、
最初に `findObject` した参照を2回目以降の `fling` に使うと `StaleObjectException`。
**処方**: フリング毎に `findObjects(By.scrollable(true))` から取り直す。複数 scrollable
（水平チップ列など）からの誤爆は「可視領域が最大高のもの」を選んで回避。取り直し直後の
stale（アニメ中のツリー変化との競合）は1回だけ再取得・再試行し、2連続は異常として伝播。

## 検証切り分けの型（0 found になったら）

トレースは嘘をつかない。端末の `/data/local/tmp/trace_processor_shell` で
`select p.name, count(*) from actual_frame_timeline_slice s join process p using(upid) group by 1`
を引くと「フレームが誰名義か」が出る（本件では launcher/quicksearchbox のみ＝未起動が確定した）。
frametimeline データソースの有無は `perfetto --query`。

シード配達の沈黙失敗（ColorOS 固有）は [[coloros-broadcast-silent-drop]]。
経緯の一次情報＝`.claude/plans/macrobenchmark-kickoff-2026-07-17.md` ②節。
