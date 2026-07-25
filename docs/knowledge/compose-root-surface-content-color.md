# 画面ルートは Surface で配色接地する（素の Box+background は LocalContentColor が既定黒）

**症状**（2026-07-23・明快K実機検分→ユーザー指摘）: K本棚のタイトル「本棚」が全テーマで黒描画され、
ダークテーマで地に沈んで読めない。同じ NavHost 配下でも「さがす」「設定」は正常に見えた。

**真因**: Compose の `LocalContentColor` は **Surface（または明示 provider）が無い限り既定 Color.Black**。
`Box + Modifier.background(colorScheme.background)` は背景を塗るだけで content color を供給しない。
明示 `color=` を持つ Text（さがすK は全 Text 明示）だけが正しく見え、既定色頼みの Text が黒に落ちる
＝「画面によって見え方が違う」のは各画面の明示色の徹底度の差であって、環境の差ではない。

**対処**（`MainActivity.kt` NovelReaderApp）: NavHost＋K恒常ナビを包む位置に `Surface` を1枚敷き、
`color=background / contentColor=onBackground` を供給。**星図M のみ透明＋現在色素通し**
（常駐 SkyBackdropM を透かす必要があるため）。既存スキンへの波及は Roborazzi golden 全枚数無差分で機械確認済み。

**教訓**:
- 新スキン/新画面を素の Box/Column から生やさない。ルートは Surface（または既存 Scaffold）で配色接地してから。
- 「Text に色を明示しているから動く」は個人の徹底に依存した偶然＝ルート接地が構造的な正解。
- 委譲仕様に「Text 色は原則明示」を書いても、根の Surface が無い限り既定色頼みの1枚が事故る。

関連: 栞書影の紙色はライト=Surface・セピア=Background と**地色同値**（`SkinD.shiori`）＝書影を地に直置きすると
輪郭が消える。グリッド等で並べる場合は輪郭線（outline 半透明）か面差しが必須（明快K は 1dp outline@50%）。
