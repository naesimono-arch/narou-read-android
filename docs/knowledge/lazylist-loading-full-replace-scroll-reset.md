# LazyColumn: reload の Loading で行を全置換するとスクロール位置が先頭へクランプされる

**症状**: 発見ホームでスクロール後に期間タブ（週間/月間等）を切り替えると位置がトップへ強制リセット
（キャッシュ無し時のみ顕在。2026-07-19 ユーザー報告・全スキン D/C/M/J/P で同型を確認）。

**機序**: reload のたび state が `Loading` を経由し、ランキング行群が status 行1件へ**全置換**される
→ 総コンテンツ高が崩壊→ LazyListState が可視アンカーitemを見失い先頭側へクランプ→ Content 復帰後も戻らない。
タブ切替そのものではなく **「Loading による一時的な全置換」が真因**（キャッシュがあると Loading を挟まないため
再現しない＝再現条件が「キャッシュ無し」になる理由）。

**対処パターン（stale-while-revalidate）**: 直近の `Content` を `lastContent` に控え（`LaunchedEffect(state)`）、
再取得 Loading 中は直近ランキング骨格（同 key）を出し続けてアンカーを保持。VM/データ層は非改変。
Empty/Error は真に畳む。回帰テスト＝「初回 Loading は status 表示」「再取得中は直近保持（全置換しない）」の対。

**未展開の同型**: 結果一覧(Result)系 4実装は同機序だが「新クエリは先頭表示が正」の解釈もあり要裁定（handover 参照）。
