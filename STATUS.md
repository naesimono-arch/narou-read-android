# STATUS — 現況台帳（正本 / main）

> **「今どうなっているか」の現在値だけ**を置く（目安60行・**上限 2,500字＝現在値でなくなった記述を消す合図**。縮めて収めない）。
> **完了の履歴＝git log が正本**（ここには書かない）／やること＝`handover.md`／人間待ち＝`awaiting-human.md`。
> それ以外（知見・ADR・一次情報）の割り振りは **CLAUDE.md「管理ドキュメントの体系」が正本**——再掲すると片方だけ古くなる。
> **git から導出できる値（SHA・コミット数・差分行数）とブランチ名は書かない**——書いた瞬間から嘘になる。

## 0. 現在の状態

- **公開準備（Google Play）**: **ブランド名＝`Yosari` 確定**。残＝applicationId 変更（`app.yosari.reader`・**§1 実機ツアーの後**）
  ／プライバシーポリシー公開＋鍵バックアップ（ユーザー作業）／**ストア用スクショ6枚（実機作業）**。

- **UI の既定は「明快K」**＝`Skin.MEIKAI_K`（既存の明示保存 D/M/P/J/C は不変。⚠️ 装いの間は
  `SKIN_SWITCHING_ENABLED` で**公開ビルドでは閉じる**＝ADR 0027）。
  **実機目視待ちが2件**＝①K の6面ツアー（本棚／さがす／設定／目次／読書／装いの間）＝`awaiting-human.md` §1
  ／②横向き T1 の検分＝同 §1-3。

- **Room v21**。⚠️ **旧APKへの逆走は禁止**（migration N→N-1 が無くクラッシュ＝古い→新しいの一方向のみ）。変更手順＝`/db-migration`。

- **実機**: OPPO PGEM10（IP は DHCP で変動＝ハードコードせず `adb-bridge` で張り直す）。作法＝`/device-verify`。
  **実蔵書7冊・全冊とも本文健在**（id・progress とも開始時バックアップと一致＝無傷。**絶対に消さない**）。
  **検証用の残置物**＝捨て本2冊（`6c726cfe` カクヨム26話・`cf4ee71b` PDF18章。どちらも本文は復旧済み＝**欠落させ直せば欠落系を踏める**）／
  Web カード `N7415ML`。books は 7＋2＝9冊。
  ⚠️ **端末の APK は 2026-08-19 投入の debug＝以後の意匠・バグ修正はまだ端末に無い**（`awaiting-human.md` §1 の前置き）。
  ⚠️ 同一 WiFi 上に**第三者端末（Huawei P30）が居る**＝操作前に model を確認（機序＝memory `adb-bridge-stale-tcp-holds-wrong-device`・
  **他人の端末なので読み取り以外はしない**＝`docs/knowledge/emui-p30-jank-log-collection.md`）。

- **端末内診断 `diagnostics/`（外部送信ゼロ）の書き出しUIは未実装**——UI追加はモック先行が要るため別ラウンド
  （※この1件だけ handover/backlog に受け皿が無いのでここに残置。着手するなら handover へ移す）。

- **ゲート**: ローカル（`testDebugUnitTest`／public シグネチャを変えたときの `:app:assembleDebugAndroidTest`）も
  CI（`.github/workflows/ci.yml` の全ゲート）も**緑**。内訳と対象外の理由は YAML 側が正本。
  ⚠️ **Gradle は `tools/gwlock.sh <task>` 経由で回す**（同一ツリーの並列実行が出す偽の赤をツリー単位ロックで潰す。作法は `/build`）。

## 1. 観察ログ（未確定の所見のみ・確定したら handover か ADR へ）

- **#2 章往復で章末着地**（⚠️未確認）: Claude 側で2回観察したがユーザー手元で再現せず＝確定バグでない。フレーキー or 操作アーティファクトの可能性。深追い不要だが頭の片隅に。
