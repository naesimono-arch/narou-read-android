# STATUS — 現況台帳（正本 / main）

> **「今どうなっているか」の現在値だけ**を置く（目安60行・**上限 2,500字＝現在値でなくなった記述を消す合図**。縮めて収めない）。
> **完了の履歴＝git log が正本**（ここには書かない）／やること＝`handover.md`／人間待ち＝`awaiting-human.md`。
> それ以外（知見・ADR・一次情報）の割り振りは **CLAUDE.md「管理ドキュメントの体系」が正本**——再掲すると片方だけ古くなる。
> **git から導出できる値（SHA・コミット数・差分行数）とブランチ名は書かない**——書いた瞬間から嘘になる。

## 0. 現在の状態

- **公開準備（Google Play）**: **ブランド名＝`Yosari` 確定**。**ストア用スクショ6枚は撮影・検算済み**（`docs/store/assets/screenshots/`）。
  残＝applicationId 変更（`app.yosari.reader`・**§1 実機ツアーの後**）／プライバシーポリシー公開（ユーザー作業）。
  ⚠️ **release 用の正式な鍵がまだ無い**（`local.properties` に `release.*` が無く Gradle は未署名 APK しか出さない）
  ＝撮影は debug 鍵で署名した release ビルドで代用した。**提出には鍵が要る**（ユーザー作業）。

- **UI の既定は「明快K」**＝`Skin.MEIKAI_K`（既存の明示保存 D/M/P/J/C は不変。⚠️ 装いの間は
  `SKIN_SWITCHING_ENABLED` で**公開ビルドでは閉じる**＝ADR 0027）。
  **実機目視待ち＝K の6面ツアー**（`awaiting-human.md` §1）。横向き T1 と未投入の意匠は 2026-08-26 に**エミュで撮影済み**
  （実機でしか出ないのは ColorOS 固有の挙動と実 GPU の描画性能だけ＝`/emulator-verify` §5）。

- **Room v22**（2026-08-25 に v21→v22＝`pending_jobs.attempts`。取込の再起動ループを止める止め金）。
  ⚠️ **旧APKへの逆走は禁止**（migration N→N-1 が無くクラッシュ＝古い→新しいの一方向のみ）。変更手順＝`/db-migration`。

- **実機**: OPPO PGEM10（IP は DHCP で変動＝ハードコードせず `adb-bridge` で張り直す）。作法＝`/device-verify`。
  **実蔵書7冊・全冊とも本文健在**（id・progress とも開始時バックアップと一致＝無傷。**絶対に消さない**）。
  **検証用の残置物**＝捨て本2冊（`6c726cfe` カクヨム26話・`cf4ee71b` PDF18章・本文は復旧済み）／
  Web カード `N7415ML`。books は 7＋2＝9冊。
  **端末の APK は 2026-09-04 投入の debug（最新）**。⚠️ 実機 DB は投入時に **Room v21→v22 へ移行済み**＝旧 APK へは戻せない。
  ⚠️ 同一 WiFi 上に**第三者端末（Huawei P30）が居る**＝操作前に model を確認（機序＝memory `adb-bridge-stale-tcp-holds-wrong-device`・
  **他人の端末なので読み取り以外はしない**＝`docs/knowledge/emui-p30-jank-log-collection.md`）。

- **ゲート**: ローカル（`testDebugUnitTest`／public シグネチャを変えたときの `:app:assembleDebugAndroidTest`）は緑。
  **golden 走査(c) の赤は解消済み**（`DiscoveryCommon.kt` の `Row`→`FlowRow` 化。同一スキャナを新旧の golden ツリーへ
  当てる対照で確定＝スキャナを緩めて緑にしたのではない）。内訳と対象外の理由は YAML 側が正本。

- **[分析中] 他社 APK の逆解析**（2026-08-25 開始・**ユーザーが実行中**）: 骨（遷移スケルトン）と重い処理の扱いの
  **規範を借りる**のが目的。⚠️ **骨の濃さは「借りられない」と判明**＝他社 CR 帯が測る量の違う値の混成で比較不能
  （ADR 0040）。同じ定義で測り直すまで現行値のまま。取得の優先順＝①`assets/dexopt/baseline.prof`（＝開発側が「重い」と判断したメソッド一覧がそのまま出る）
  ②`META-INF/*.version`（依存ライブラリとバージョンが平文・難読化されない）③骨の定数（**骨を出すまでの遅延閾値 ms**・
  shimmer の duration/easing・色差・角丸）④ページングの設定値（`pageSize`/`prefetchDistance`/`initialLoadSize`/`maxSize`・画像キャッシュ上限）。
  ⚠️ **縦書き組版だけは先例がほぼ無く借りられない**＝自前で決める領域（Compose で縦書きを本気でやっている例が見当たらない）。
  ⚠️ 展開先は **ext4**（`/mnt/c` は drvfs で数万ファイル展開が桁違いに遅い）。

## 1. 観察ログ（未確定の所見のみ・確定したら handover か ADR へ）

- **#2 章往復で章末着地**（⚠️未確認）: Claude 側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
