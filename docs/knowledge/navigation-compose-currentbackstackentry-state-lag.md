# currentBackStackEntryAsState はライブ値でない — タブガードに使うと稀にタップを握り潰す

**事象（2026-07-23）**: K恒常ボトムナビで「さがす→本棚へ稀に遷移不能」（再現条件不明）。

**機序（navigation-compose 2.7.5 のバイトコードで確認）**:
- `currentBackStackEntryAsState()` の実体は `currentBackStackEntryFlow`（`MutableSharedFlow(…, BufferOverflow.DROP_OLDEST)`）の `collectAsState`。
- よって値は実バックスタックより**最大1フレーム遅延**し、連打時は**中間値が drop** される。
- この遅延スナップショットで `if (route != currentRoute) navigate(route)` とガードすると、「バックスタックは既に目的ルート・表示は旧画面」の乖離窓でタップが無音で握り潰される（症状は再現不能のため推定＝防御的是正と明記して修正）。

**対処**: 遷移可否の判定は navigate/pop と**同期更新されるライブ値 `navController.currentDestination?.route`** で行う（`MainActivity.kt` の `navigateKTab` に集約）。選択タブの**表示**はスナップショットのままで良い（1フレームで自己修復する装飾）。

**一般則**: `currentBackStackEntryAsState` は「表示」用、`currentDestination` は「判定」用。ガード・分岐ロジックに as-State 系スナップショットを使わない。

関連: `KTabNavigationTest.kt`（往復・相互到達・再タップ no-op の回帰3件）。
