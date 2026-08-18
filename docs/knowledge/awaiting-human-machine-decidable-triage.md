# awaiting-human に紛れていた「機械で決まる」検証タスク（2026-08-19 棚卸し）

`awaiting-human.md` は「人間の目視・裁定・外部手続き待ち」の台帳だが、全43項目を仕分けたところ
**17件（4割）が人間待ちではなかった**。内訳＝機械で今すぐ決まる 10／テストを1本書けば決まる 5／実機の数値計測で決まる 2。

## なぜ紛れるか（再発の機序）

台帳自身が §1-A で「残るのは**人間の目・耳でしか決まらないもの**と**環境が作れず未検証のもの**だけ」と書いていた。
**後者は人間待ちではない**——fixture や前提条件が作れていないだけで、テストは fixture を作れる。
「実蔵書に4桁話数の本が無い」は目視が要る理由にならず、実際 golden 3本が既に担保していた。

判定の軸＝**「実機で見る」と書いてあっても、実体が〈数値の比較〉〈ノードの有無〉〈幅・座標の一致〉なら機械側**。
本当に人間なのは**美醜・声量・読み上げの音・操作の気持ちよさ**の3クラスだけ（ほかに 16ms 窓の標本化不能
＝ColorOS は screenrecord 不可、外部手続き）。

## 機械で決着済みだった項目（台帳から削除済み・テストが記録の正本）

| 項目 | 決着の根拠 |
|---|---|
| 話数ラベル4桁 | `TocKEpisodeDigitsScreenshotTest`・`ChapterHeaderEpisodeDigitsScreenshotTest`・`ReadingCartridgePTest` |
| 新着タブ更新日時 M | M は共通実装を呼ぶ（`DiscoveryHomeSkyM.kt:707` `rememberOrderMetricLabel`）＋暦日境界は `DiscoveryCommonLabelsTest` |
| グリッド状態行 D/C | `GridStatusLineWrapTest`。C は `ShelfFace.kt:313` で D と同一描画＝D の緑で担保 |
| 表示設定 trailing の色 | `ReadingSettingsSheet.kt:466` が案C裁定どおり `infoText` |
| 章見出し話数 ①④ | `ChapterHeaderNumTest`／`ReadingCartridgePTest`／`ReadingPortalJTest`（③字間だけ人間に残る） |
| 取込エラーの重複集約 | `AggregateErrorEventsTest` |
| 通知タップ→上書き確認 | `OverwriteConfirmTeleportTest` が warm と **cold（`landing_coldStartShape_isNoOpButConsumed`）** の両方を持つ |
| モック逆同期債務（きせかえ行） | K/D/M/P/J **5枚とも新形**（`<span class="rv">`）＝債務は完済。K に残る `現在:` は旧形却下の理由を書いたコメント（`settings-K.html:130`） |

## これから書くテスト（安い順）

1. **§1-2 ⋮ メニューの座標**（M/J）: メニュー上端 ≥ ヘッダ下端を Robolectric で。
   ⚠️ 台帳の「golden が無いので機械の網が効かない＝目視が唯一の手段」は**誤り**——`GridStatusLineWrapTest` が
   「D 側に golden が1枚も無いから撮影でなくレイアウト結果で縛る」という同じ問題を既に解いている。
2. **§1-3「条件を変更」→検索画面**: `DiscoveryUpNavigationTest` は up 側7本のみで、この経路のアサートが無い。同ハーネスに1本追加。
3. **§3-1 固定トップ 108dp の実測**: `BookshelfKLandscapeScreenshotTest` の landscape qualifier で直接測れる
   （意匠の選択そのものは人間だが、**実測のために実機を待つ必要は無い**）。
4. **§1-A 電池最適化ダイアログの末尾到達**: 構造は既に解決済み（`NovelReaderAlertDialog.kt:93` が body を
   `Column(verticalScroll())` で包む＝M3 1.3.2 の AlertDialog は本文を送れない）。残るのは末尾ノード到達のアサート1本。
5. **§1-6 削除失敗 Snackbar (c)**: 権限失効を Fake で注入して文言を検証（`ShelfSnackbarScreenshotTest` の器を再利用）
   ＝実機で権限失効の機会を待つ必要は無い。
6. **§1-6 U1 Web 新着統合**: 「読了本のみ再フェッチ」の結線を Worker のフェイク実行で JVM 化（ロジックは `WebNewEpisodeCheckLogicTest` 有）。

## 実機の数値計測で決まる（目視ではない）

- **§1-4 push スケルトン**: 遷移窓の jank フレーム数を gfxinfo で取る（骨の有無は `TransitionSkeletonTest` が構造で担保済み）。
- **§1-7 release で PDF 取込**: R8 経路の実走。SAF ピッカーは uiautomator で叩けば自走可（Web 取込が adb で通ることは実証済み）。

## 併せて判明した誤記（直した）

- §1-2 の「機械の網が効かない」＝上記1のとおり誤り。
- §1-6 の cold start は未消化でなく**既に緑**。
- §3-2 モック逆同期債務は行番号も内容も実態と不一致（債務自体が完済）＝**存在しない債務を追わせる状態**だった。
