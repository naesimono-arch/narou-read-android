# HANDOVER — UI-n「視覚言語D」実機確認調整ループ（D完全制作）

> 作成: 2026-06-30 / 対象ブランチ: **`UI-n`** / 種別: 次セッション単独で走れる手順書（一次情報アーカイブ）
> 正本サマリは `STATUS.md`（現況）/ `handover.md` A2（やること）。本ファイルは実行手順の細部を持つ。

## 0. このセッションでやること（一言）

UI-n の「視覚言語D（和モダン・余白）」は配色・読書テーマ・自動書影まで Compose 翻訳済みだが、
**実機で見ると D の再現が粗い（モックの完成度に届いていない）**。画面を増やすより先に、
**実機スクショ ↔ D モック を突き合わせて差分を潰す「確認→調整ループ」を回し、D を完全に仕上げ切る**。

> ⚠️ **このループは別セッション（UI-n 上で新規起動）で実行する前提で書いてある。**
> 2026-06-30 の引継ぎ作成セッションは `main` 起動だったため、ブランチ跨ぎで hook が破綻し
> Bash（＝コミット）が使えなかった（§5 参照）。**必ず UI-n を起点にセッションを始めること。**

## 1. D Compose 翻訳の現況（着手前の事実）

| 対象 | 状態 | 根拠コミット |
|---|---|---|
| `theme/Color.kt`（M3全配色） | ✅ D化済 | `cb09392` |
| `theme/Theme.kt`（ReadingColors） | ✅ D化済 | `c6da6cf` |
| `components/BookCover.kt`（自動書影） | ✅ D化済 | `20dcc00` |
| 読書 hr シーン区切り | ✅ D化済 | `cd4853f` |
| `BookshelfScreen.kt`（書影まわり） | 一部 | lab→UI-n 差分あり |
| `NativeTableOfContentsScreen.kt`（目次） | ❌ 未着手 | — |
| `theme/Typography.kt`（D明朝） | ❌ 未着手 | — |
| `NativeReadingScreen.kt` の読書本体（hr以外） | 未確認 | — |

※ **主眼は「画面の網羅」より「既存D画面の実機再現品質をモック水準へ引き上げる」こと。**
未着手画面（目次・Typography）は品質ループの中で自然に取り込む。

## 2. モック正本（突合の基準）

- claude.ai/design プロジェクト `Novel Reader UI`（projectId `bb5a35c8-70ac-4efa-bb03-1579d3f11d93`）の
  `ui-n-phase0/` 配下: `reading-D.html` / `toc-D.html` / `settings-D.html` ＋ 本棚 D。
- 再取得は `DesignSync: get_file`。**この HTML が見た目の正本**＝実機をこれに寄せる。
- D の意匠メモ（handover A2 由来）: 目次＝現在章を左藍ルール＋淡背景＋明朝太字＋空状態／
  読書＝3テーマ（ライト/セピア/ダーク）＋クローム表示↔没入／設定シート＝テーマ3択(藍選択)・文字サイズ・行間。

## 3. ループ（1画面 = 1サイクル）

> ワークフロー: `[[workflow-autonomous-device-verification]]`（Claudeがadb自律駆動）＋
> `[[workflow-notify-each-step-visual-check]]`（各ステップ完了で通知し目視ダブルチェック→承認後コミット）。

1. **ビルド**: `cd android` →
   `gw --init-script /home/qingj/ext-build/novel-reader-init.gradle assembleDebug`
   - WSL 必須ワークアラ: `gw` 関数（CRLF gradlew 回避）＋ ext4 退避 init（AAPT2 EPERM 回避）。`/build` スキル参照。
2. **インストール＆起動**: `adb install -r <apk>` → 起動 → 対象画面へ `adb shell input` で遷移。
3. **実機スクショ**: `adb exec-out screencap -p > <画面>.png` を取得（Read で画像確認）。
4. **突合**: 対応 D モック（例 `toc-D.html`）と横並びで差分を列挙
   ＝色・字面（明朝/ゴシック）・余白・行間・区切り線・選択/現在地ハイライト・空状態。
5. **調整**: Compose を修正。**色は必ず `theme/Color.kt` / `theme/Theme.kt` 経由＝直書き禁止**
   （handover A2 の制約）。字面は `theme/Typography.kt`。
6. **再ビルド→再目視**。1画面がモック水準でOKになったら **ユーザーへ通知して目視ダブルチェック→
   承認後に `feat:` 単独コミット** → 次画面へ。

**推奨順**: ① 本棚（一覧の第一印象）→ ② 読書本体（`reading-D.html`：3テーマ＋没入）→
③ `Typography.kt`（明朝の土台。全画面に波及）→ ④ 目次（`toc-D.html`：未着手）→ ⑤ 設定シート。

## 4. 検証ルール（プロジェクト規約）

- Kotlin `src/main`/`src/test` 変更時はコミット計画提示前に必ず
  `cd android && ./gradlew testDebugUnitTest`（WSL は `gw` で。`androidTest` は端末必須＝対象外）。
- 構成・描画方式を変えるリファクタの後は `.claude/skills/`（architecture 等）の陳腐化を同ターンで確認。
- WSL の adb: 端末を直接見ない。`usbipd-win` で WSL に attach するか、Windows 側 adb サーバへ
  `adb connect <host>:5555`（`[[workflow-autonomous-device-verification]]` 参照）。

## 5. 既知の落とし穴・loose ends（次セッションが踏まないように）

- **🔴 ブランチ跨ぎ hook 破綻（最重要）**: セッションを `main` 等で起動して `UI-n` へ
  `git checkout` すると、起動時に読まれた main の hook config が UI-n に無いフックファイル
  （`guard_commit_branch.py` 等）を指し、**PreToolUse:Bash が落ちて以降の Bash（=git commit）が
  全ブロック**される（ADR0001 記載の現象を 2026-06-30 に実地で踏んだ）。
  → **必ず最初から `UI-n` 上でセッションを起動する**こと（UI-n の settings.json が指すフックは
  UI-n ツリーに存在＝正常動作する）。
- **main の stash 未処理**: `main` に `stash@{0}`「main: CRLF churn + .gitignore tweak（UI-n切替の退避）」
  が残っている。中身は 89ファイルの CRLF churn（ノイズ）＋ `.gitignore` への実4行追加
  （`statusline-local.sh` / `.monthly_cost_cache` を ignore）。churn が意図的 EOL 正規化でなければ
  最終的に `git stash drop`。**ただし stash 操作は `main` 上で（UI-n では関係ない）。**
- **UI-n の `.gitignore` に statusline ignore が無い**: そのため UI-n 作業ツリーで
  `.claude/statusline-local.sh` と `.claude/.monthly_cost_cache` が `??`（未追跡）で出る。
  機械固有ファイルなので **コミット時に誤って `git add` しない**こと。必要なら UI-n の
  `.gitignore` に上記4行を取り込む（独立した `chore:` コミット）。
- **未追跡スクラッチ**: `UI_FIXES_TODO.md`（lab で対応済みの古いTODO）, `commit_details.md`,
  `narou_api_manual.md`（Phase3 なろうAPI連携の資料・handover D 参照）, `planmode-bypass-incident.txt`,
  `アーキテクチャ解説.txt` がツリーに同居。D ループのコミットに混ぜないこと。

## 6. 2026-06-30 引継ぎ作成セッションでやったこと

- `main` → `UI-n` へ切替（churn を stash 退避）。
- 本 HANDOVER 作成＋`handover.md` A2 を「画面網羅翻訳」から「実機ループで D 仕上げ」へ更新。
- **これらのドキュメント変更は §5 の hook 破綻で未コミット**（working tree に残置）。
  次セッション（UI-n 起動）で内容確認のうえ `docs:` 単独コミットすること。
