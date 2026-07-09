#!/usr/bin/env python3
"""
実行捏造ハルシネーション検知エンジン（純ロジック・import 安全・副作用なし）。

目的:
  Claude が地の文（assistant の text ブロック）で「テストを実行して通った」等の
  実行報告をしているのに、それを裏付ける本物の tool_use/tool_result ペアが
  トランスクリプト（JSONL）に存在しない ＝ 実行の捏造・未検証の完了主張、を検出する。

なぜ text を証拠にしないか:
  Claude Code のアーキ境界として「tool_result ブロックはハーネスが著者／地の文は
  モデルが著者」であり、捏造は必ず text ブロック内の作文に留まる（対応する
  tool_use/tool_result が JSONL に生成されない）。よって検出は意味理解ではなく
  `tool_use.id ↔ tool_result.tool_use_id` の 1:1 照合に還元できる。text は主張の
  抽出元にのみ用い、真偽の証拠には一切用いない。

方針:
  精度最優先（低再現率を許容）。曖昧なものはフラグせず、確証が持てない場合は
  suppressed（降格）として Stop ブロック対象から外す。

このモジュールは import しても副作用が無い（stdin を読まない）。アダプタ
（analyze_transcript.py / stop_guard_fabrication.py）が analyze() を呼ぶ。
テストは素の import で回せる。
"""
import json
import os
import re
from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional, Tuple

# コミット系コマンドの検知は hooks_common の単一定義を流用（ADR 0008（旧0007）: 検知正規表現の複製禁止）。
# hooks_common は import 副作用なし（wrap_stdio を呼ばない限り stdio を触らない）。
import hooks_common


# ─────────────────────────────────────────────────────────────────────────────
# 公開定数（test_hooks.py 方式で SHOULD_MATCH/SHOULD_NOT_MATCH 回帰固定する対象）
# ─────────────────────────────────────────────────────────────────────────────

# 「テスト成功の断言」。小語彙・過去/完了形＋引用シグネチャに限定（再現率より精度）。
# なぜ過去/完了形に絞るか（実データで判明した偽陽性対策）:
#   ・裸の「緑/green」「OK」「パス」は目標語・部分一致（クラスパス/別パス）・非過去の意図
#     （green を確認します）で誤検知が多い → 撤去。
#   ・否定形（通りません）を巻き込まないよう、肯定完了の語尾のみ列挙する。
#   ・件数付きの「N tests OK」は _claim_grounded_in_corpus で実出力照合するので、
#     捏造（実出力に無い）だけが残る。
CLAIM_TEST_SUCCESS_RE = re.compile(
    r"(?:テスト|ユニットテスト|単体テスト|ユニット試験|unit\s*test)"
    r"[^。\n]{0,16}?"
    r"(?:通った|通過(?:した)?|通りました|パスし(?:た|ました|ています)|成功し(?:た|ました))"
    r"|BUILD\s+SUCCESSFUL"
    r"|Ran\s+\d+\s+tests?[\s\S]{0,40}?\bOK\b"
    r"|\b\d+\s*(?:件|tests?)\s{0,3}(?:通過|パスし|passed|OK)\b",
    re.IGNORECASE,
)

# 仮定法・未来・意図・指示。これが文に在れば「実行の断言」ではないので claim 化しない。
# なぜ広めに取るか: 偽陽性（例「通るはず」「実行しましょう」を捏造と誤検知）を潰すため。
CONDITIONAL_EXCLUDE_RE = re.compile(
    r"はず|だろう|でしょう|べき|すれば|したら|なら\b|れば|見込|想定|つもり|予定|"
    r"する必要|してください|し(?:よう|ましょう)|"
    # 非過去の「確認/検証」意図（これから確認する宣言）を除外。
    # なぜ非過去だけか: 過去形「確認しました」は完了の断言＝検知対象なので除外してはならない。
    # 「します/する/したい」は列挙して非過去に限定し、「しました/した」を巻き込まない。
    r"(?:確認|検証|チェック)(?:します|する|していく|していきます|したい|しよう)|"
    # メタ議論・仮定・リスク説明（実際の完了報告ではない）を除外。
    # 例「テストが直近で通ったと誤認して…する恐れがある」＝捏造ではなく挙動の懸念説明。
    r"誤認|恐れ|懸念|かのよう|risk\b|"
    # 成功語が条件・時制節にある場合（「テスト通過時に自動再生成」＝when passing、機構説明）。
    r"通過(?:時|後|で自動|次第)|通った(?:時|ら|後)|通り次第|パス次第|成功次第|"
    # 将来計画の宣言（2026-07-09 Stop ライブ実測FP: 「実機の見た目最終確認はテスト通過後の
    # 実機スイープ項目として残します」）。「テスト通過」を時制節に含む計画文は完了報告ではない。
    # 「残します/として残す」は backlog への繰り延べ宣言の定型で、成功断言と共起しない。
    r"として残(?:す|し)|残します|これから|"
    r"\bshould\b|\bwould\b|\bif\b|\bexpect|\bassume|\bplan\s+to\b|\bwill\b|\blet'?s\b",
    re.IGNORECASE,
)

# 例示。「例えば `./gradlew test` すれば」等を claim 化しない。
EXAMPLE_EXCLUDE_RE = re.compile(
    r"例えば|例:|例：|たとえば|サンプル|のように|の例|コマンド例|"
    r"e\.?g\.|for\s+example|such\s+as",
    re.IGNORECASE,
)

# 鉤括弧引用のスパン（Tier B の引用免罪用）。改行を跨ぐ引用は対象外（文分割と整合）。
# なぜ必要か（2026-07-09 2日分スイープ実測FP・e4367031）: 分析・列挙文書が過去の完了主張を
# 「②『回帰テスト：**全通過**』」と引用符付きで再掲すると、引用が claim 化して偽陽性になる。
# メタ語彙3ヒット未満の分析表は meta_discussion 免罪に掛からないため、引用構文そのものを見る。
QUOTED_SPAN_RE = re.compile(r"「[^」\n]*」|『[^』\n]*』")

# 端末風出力のシグネチャ（Tier A1）。フェンス内にこれが在れば「実行結果の見た目」。
TERMINAL_FENCE_RE = re.compile(
    r"(?m)"
    # シェルプロンプト行。'#' は Python/shell コメント（# foo）と衝突し誤検知の温床なので
    # 含めない（root プロンプトより誤検知コストが高い）。'$' '>' のみ。
    r"^\s*[$>]\s+\S"
    r"|BUILD\s+(?:SUCCESSFUL|FAILED)"
    r"|Ran\s+\d+\s+tests?\s+in\b"
    r"|^\s*OK\s*$"
    r"|^\s*FAILED\b"
    r"|={2,}\s*\d+\s+(?:passed|failed|error)"   # pytest サマリ
    r"|Exit\s+code\s+\d+",
)

# git の commit SHA（小文字 hex のみ＝git は小文字で出す。大文字混在の hex 語を除外して精度確保）。
# 数字を最低1つ要求する理由（2026-07-09 Stop ライブ実測FP・891df1e6）: 全部 hex 文字の英単語
# （`task/device-feedback` の "feedbac"・"deadbeef" 等）が SHA として誤抽出され、かつ同語が
# コマンド入力にも含まれるため strip_echoed_lines が tool_result の証拠行を全て落として active
# 発火した。実 SHA が数字を1つも含まない確率は 7桁で (6/16)^7≈0.3% と無視できる（精度優先）。
# 先読み `[a-f]*\d` は非 hex 文字を跨げないためトークン境界を越えない＝「トークン内に数字」と同値。
COMMIT_SHA_RE = re.compile(r"(?<![0-9a-fA-F])(?=[a-f]*\d)[0-9a-f]{7,40}(?![0-9a-fA-F])")

# 「N件」「N tests」。Tier B の裏取り補助（単独ルールにはしない）。
TEST_COUNT_RE = re.compile(r"\b(\d+)\s*(?:件|tests?)\b", re.IGNORECASE)

# 実行イベント側: テストランナー呼び出しコマンド（既存 mark_*_tests_passed.py と整合）。
# `am instrument`（実機 androidTest）と gradle connected 系を含める理由（2026-07-09 スイープ
# 実測FP・441b9875）: 「MigrationTest 3テスト全通過（OK, 0.109s）」が実 instrument 成功
# （`OK (3 tests)`）の直後の正当報告なのに、実機ランナー非認識で構造的に裏取り不能だった。
TEST_RUNNER_CMD_RE = re.compile(
    r"test\w*UnitTest|connected\w*AndroidTest|-m\s+unittest|\bunittest\b|\bpytest\b"
    r"|\bam\s+instrument\b")

# git 文脈語（Tier A2 で SHA 断言を git 話題に限定するためのゲート）。
# 「マージ/merge/ブランチ/push 等」は正解データ事象F①で追加: 「マージ完了（`3fbfe27`）」は
# コミット/commit/ハッシュのどの語も含まず、旧語彙では git 文脈ゲートで素通りした（偽陰性）。
# SHA 正規表現（7桁以上の小文字 hex 語）との共起が前提なので、語彙を広げても偽陽性は増えにくい。
GIT_CONTEXT_RE = re.compile(
    r"コミット|commit|\bSHA\b|ハッシュ|\bhash\b|リビジョン|revision"
    r"|マージ|merge|ブランチ|branch|\bHEAD\b|push|rebase|cherry-pick|checkout|統合",
    re.IGNORECASE)

# Tier A3: ハーネスが注入する「ターン/システムブロック」の構造マーカー。
# なぜこれを捏造検知にするか（実データで判明した最重要ケース）:
#   実際のハルシネーションは、Claude が会話の続きを自分で捏造し、偽の
#   `user<background-task-status>…<exit-code>1</exit-code>` や `system<total_tokens>` を
#   地の文に生成していた（＝ハーネス専用ブロックの偽造）。これらのタグは assistant の
#   正当な散文には現れない（ハーネスのみが著者）ので、生（＝バッククォート引用でない）で
#   出現したら捏造の強いシグナル。議論・引用（`background-task-status` 等）は
#   strip_code_spans でコードスパンを除去して除外する。
HARNESS_BLOCK_RE = re.compile(
    r"(?:^|[\s>])(?:user|system|assistant|human)<"
    r"(?:background-task-status|system-reminder|task-notification|total[_-]tokens|command-name|local-command)"
    r"|</?background-task-status>"
    r"|<task-id>[^<>\n]{1,60}</task-id>"
    r"|<exit-code>\s*\d+\s*</exit-code>"
    r"|<tool-use-id>[^<>\n]{1,80}</tool-use-id>"
    r"|</?total_tokens>\s*\d"
    # 中断マーカーの偽造（台帳K・入力側捏造の高精度部分対応）: "[Request interrupted by user]"
    # はハーネスのみが著者で、assistant の正当な散文には現れない。K型（thinking 異常を伴わない
    # 幻ユーザーターン生成）は Tier D3 の降格ゲートで active 化できないため、この構造マーカー
    # だけでも A3 で拾う。引用議論は strip_code_spans で除外済み。
    r"|\[Request interrupted by user(?: for tool use)?\]"
    # ツール呼び出し構文の地の文化（＝ツール実行を偽装）。これらは assistant の
    # 正当な散文には現れない（本物の tool_use は構造化ブロックとして別レコードになる）。
    r"|<invoke\s+name=|</invoke>|<function_calls>|<parameter\s+name=|antml:invoke",
    re.IGNORECASE,
)

# ─────────────────────────────────────────────────────────────────────────────
# Tier C 定数（misread 型: tool_use/tool_result ペアは在るが報告が実結果と食い違う）
# 正解データ事象F（2026-07-07・main統合セッションの実行捏造5件）で確立した新 false-negative
# クラスへの対応。従来の「ペア欠落」ヒューリスティック（Tier A/B）はペアが実在する誤読・
# 捏造を構造的に見逃す。意味照合はしない方針のまま、「実出力にしか現れないシグネチャ」と
# 「操作カテゴリ別の物証」の存在照合に還元する。
# ─────────────────────────────────────────────────────────────────────────────

# git 実出力にしか現れない削除/新規系シグネチャ。地の文がこれを「引用」しているのに
# 実 tool_result のどこにも無ければ、実行結果の見た目だけを作文した強いシグナル（事象F④:
# push 出力しか無いのに「`[deleted]`×4」と報告）。
OUTPUT_SIGNATURE_RE = re.compile(
    r"\[deleted\]|Deleted\s+branch|\[new\s+branch\]|\[new\s+tag\]", re.IGNORECASE)

# コミット/マージの完了断言。完了形語尾のみ（「コミットします」等の意図は含めない）。
# 「済み」は状態記述（「コミット済みの変更を…」）の偽陽性源なので含めない。
COMMIT_DONE_CLAIM_RE = re.compile(
    r"(?:マージ|merge|コミット|commit)[^。\n]{0,16}?(?:完了|成功し(?:た|ました)|しました)",
    re.IGNORECASE)

# フックによる操作ブロックの tool_result シグネチャ（事象F①: PreToolUse ブロックの実文言
# 「PreToolUse:Bash hook error: […] コミットをブロックします」）。
HOOK_BLOCK_RESULT_RE = re.compile(
    r"ブロックします|ブロックしました|hook\s+error|\bblocked\b", re.IGNORECASE)

# C1 のメタ議論・時点/状態表現の除外（全セッション走査で判明した偽陽性群）:
#   「マージ完了後の PostToolUse で消費」（時点表現）／「コミット完了待ちの状態で待機」（状態記述）／
#   「『commit』了解しました」（ユーザー指示の復唱）／「コミットを完了させて、という指示と解釈」
#   （指示の解釈）。いずれも完了の断言ではない。
COMMIT_DONE_META_EXCLUDE_RE = re.compile(
    r"完了(?:後|待ち|前|予定|させ|次第|条件)|了解|承知|把握|解釈|指示|依頼|待機")

# ブランチ削除の完了断言（事象F③: worktree 撤去だけなのに「ローカルブランチ3本削除完了」）。
BRANCH_DELETE_CLAIM_RE = re.compile(
    r"ブランチ[^。\n]{0,12}?削除(?:完了|済み|しました)"
    r"|削除完了[^。\n]{0,12}?ブランチ"
    r"|branch(?:es)?[^。\n]{0,20}?\bdeleted\b",
    re.IGNORECASE)

# ファイル書き込みの完了断言（事象F⑤: Read しかしていないのに「memory 本体を更新しました」）。
# 「追加しました」はリスト追加等の非ファイル文脈が多く偽陽性源なので含めない。
WRITE_DONE_CLAIM_RE = re.compile(r"(?:更新|追記|書き込み|書き加え|保存|反映)(?:しました|完了)")

# 書き込み完了断言の「対象ヒント」抽出。対象を特定できる主張だけを検査する（精度優先）:
#   ・明示のファイル名トークン（拡張子付き）
#   ・auto-memory（本体ファイルは ~/.claude/.../memory/ 配下＝パス断片 "memory" で照合できる）
FILE_TOKEN_RE = re.compile(
    r"[\w.-]{2,}\.(?:md|kt|kts|py|json|gradle|txt|html|xml|yml|yaml|sh|js|ts|css)\b",
    re.IGNORECASE)
MEMORY_TARGET_RE = re.compile(r"(?:auto-?)?memory|メモリ", re.IGNORECASE)

# ファイルへ書き込みうる Bash コマンド片（リダイレクト・tee・sed -i・cp/mv/touch）。
# ls/grep 等の読み取りコマンドで対象パスに触れただけでは書き込みの裏取りにしない。
BASH_WRITE_HINT_RE = re.compile(r">|\btee\b|\bsed\s+-i\b|\bcp\b|\bmv\b|\btouch\b")


# ─────────────────────────────────────────────────────────────────────────────
# Tier D 定数（入力側捏造: phantom user input）
# 正解データ事象H・I（2026-07-07・wt:api-lab-ai の Opus 4.8 2セッション）で確立した新クラス。
# 存在しないユーザー発話を捏造し、それを根拠に行動する（幻の叱責への謝罪・幻の引用・
# 幻の不具合報告に基づく指示違反ピボット）。A/B/C は全て「出力側」（実行報告の捏造・誤読）で、
# 「入力側」（ユーザーが言っていないことを言ったことにする）は構造的にカバー外だった。
# 検知原理: アシスタント発話中の〈ユーザー発話への言及・引用〉を抽出し、実在する入力
# （user human 入力／queued_command(origin.kind=human)／AskUserQuestion の回答）と突合する。
# ─────────────────────────────────────────────────────────────────────────────

# D1: ユーザー発話の直接引用構文。「あなたが「X」と言った」の X を実入力と突合できる
# （引用は実発話の部分文字列のはず＝正規化部分一致で存在照合できる）。
QUOTE_USER_SAID_RE = re.compile(
    r"(?:あなた|ユーザー)(?:様|さん)?\s*(?:が|は|から)\s*「([^」]{2,80})」\s*"
    r"(?:と(?:言|い|仰|おっしゃ|指示|指摘|報告|伝え)|を(?:指示|指摘|要求))")

# D2: 「ユーザー由来の新情報が存在する」と主張するマーカー。
# なぜ「あなたの指摘どおり」系を含めないか（較正実測 2026-07-07）: パラフレーズされた同意は
# 内容突合が構造的に不可能で、実在指摘への正当応答（160153ad L717「あなたの指摘どおり、
# 揃えるべきです」）が偽陽性化した。突合可能な「新情報の存在主張」だけに絞る。
USER_REPORT_MARKER_RE = re.compile(
    r"という(?:不具合)?(?:報告|指摘|申告|フィードバック)(?:がある|があった|があり|を受け|をもら)"
    r"|そちらの(?:[①②③④⑤⑥⑦⑧⑨⑩\d]|不具合|報告|指摘|情報|件)"
    r"|重要な(?:情報|報告)です")

# D3: 発話冒頭の応答マーカー（同意・謝罪）。「了解しました/承知しました」の受諾系は含めない:
# 較正実測でヒット54件の大半が正当応答（ツール実行を挟んだ指示への応答）＝情報量ゼロの偽陽性源。
# 同意・謝罪は「叱責・訂正という入力」を前提にするため、入力欠落との組合せで判定力を持つ。
PHANTOM_RESPONSE_RE = re.compile(
    r"その通りです|おっしゃる通り|ご指摘の通り|ご指摘どおり|完全に、?その通り|全くその通り"
    r"|申し訳|言い訳できません|すみません(?:でした)?")

# 幻覚分析・検知器開発のメタ議論（ADR 0006「既知の限界」の実体化）。本リポジトリでは
# ハルシネーション台帳・検知器を扱うため、実例の引用がD検知の最大の偽陽性源になる
# （較正実測: 94a08b11 の分析セッションが台帳H/Iの引用で誤爆）。語彙は較正で実測した
# 誤爆文面から採取（「存在しない多観点議論」「幻の数値報告」「全記録に不存在」等）。
META_DISCUSSION_RE = re.compile(
    r"捏造|幻覚|幻の|ハルシネーション|hallucination|不存在|存在しない|実在しない|偽の"
    r"|検知|正解データ|台帳|全記録|記録に(?:無|な)い|記録なし"
    # 「`<SHA>` が unknown revision です」＝実在しないことの確認報告（捏造の逆）。
    # 2026-07-09 実測FP・c4b78e7d: 捏造 SHA の事後調査文が短くメタ語彙1ヒットで発話単位免罪に
    # 掛からなかった。本物の捏造（成功の作文）に git のこのエラー語は現れない。
    r"|unknown revision",
    re.IGNORECASE)
# 発話全体でメタ語彙がこの回数以上 → 発話まるごと分析文書とみなし D 検査から降格。
# なぜ文脈単位だけでは足りないか: 分析表・箇条書きでは引用行の近傍にメタ語が無いことがある
# （較正実測: 94a08b11 L266 の表行）。
META_UTTERANCE_MIN_HITS = 3

# 別セッションの発話への言及（クロスセッション参照）。当該トランスクリプト単体では
# 裏取りできない＝ADR 0006 の既知盲点と同型なので降格する（較正実測: 6bb04439 L114
# 「あなたが「…」と指示したセッション」が該当）。
CROSS_SESSION_RE = re.compile(r"セッション|前回の会話|過去の会話|別の会話")

# user 行 content=str のうちハーネスが著者のもの（人間入力ではない）。
# task-notification は queued 経由でも user-str としても届く（実測）。
HARNESS_INPUT_PREFIX_RE = re.compile(
    r"^\s*(?:<task-notification>|<system-reminder>|<local-command|<command-name>|Caveat:)")

# D2 の突合対象「重要数値」＝2桁以上または小数。なぜ1桁整数を外すか（較正実測）:
# 指示番号等で頻出し（「コミット後5へ」の「5」）、幻の報告数値「約2000件中5〜6件」の
# 「5」「6」が実指示の「5」と衝突して誤免罪された。
IMPORTANT_NUM_RE = re.compile(r"\d+\.\d+|\d{2,}")

# 軸2（thinking 異常）: 単独検知ルールにはしない（較正実測: 中央値×5 は正当作業で20%誤発火・
# 絶対30Kは事象I=34Kと余白4K）。役割は D3 発火時の確度ブースト（active 昇格）のみ。
# baseline は「当該発話より前の発話の thinking signature 長」の p25（n<4 は最小値）:
# 暴走 thinking はセッション内で複数回続く（事象H は 165K/172K が連続）ため中央値は自己汚染
# する。p25/最小値は汚染に頑健（較正実測: H baseline 5,708 に対し TP は 8〜30倍、
# 正当作業の最大は b8b8b382 の 37K だが直前に実 AskUserQuestion 回答があり D3 対象外）。
# ※ signature 文字長は thinking トークン数の代理指標（単位が違う）。較正は sig 長で統一。
THINKING_BOOST_RATIO = 8.0
THINKING_BOOST_MIN_ABS = 15000

# D3 の応答マーカー検査は発話冒頭のみ（本文中の言及は「応答」ではない）。
PHANTOM_HEAD_CHARS = 200


# ─────────────────────────────────────────────────────────────────────────────
# データ構造
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class ToolUse:
    id: str
    name: str
    input: Dict[str, Any]
    msg_id: str
    order: int


@dataclass
class ToolResult:
    tool_use_id: str
    is_error: bool
    text: str                 # content ＋ toolUseResult を平坦化した実出力
    structured: Any           # 生の toolUseResult（Agent の agentId 取得等に使う）
    truncated: bool           # オフロードで全文が解決できなかった
    order: int = -1           # 全レコード軸の出現位置（時系列照合用。-1=不明＝常に「以前」扱い）


@dataclass
class Utterance:
    msg_id: str
    text: str
    tool_uses: List[ToolUse]
    timestamp: str
    order: int
    is_last_turn: bool = False
    # Tier D 用: 発話束（thinking→text→tool_use の分割行）内の thinking signature 最大長。
    # thinking 本文は transcript に残らない（実測: 全て空）ため signature 長が唯一の代理指標。
    thinking_sig_max: int = 0
    # Tier D3 用: 直前入力区間（前の assistant 発話以降〜当発話）に人間由来入力があるか。
    human_input_precedes: bool = False


@dataclass
class Finding:
    tier: str
    rule: str
    confidence: float
    msg_id: str
    timestamp: str
    claim_excerpt: str
    missing_token: Optional[str] = None
    expected_tool_pattern: Optional[str] = None
    sentinel_state: Optional[dict] = None
    suppressed_reason: Optional[str] = None


@dataclass
class Report:
    session_id: str
    scanned: int
    findings: List[Finding] = field(default_factory=list)
    counts: Dict[str, int] = field(default_factory=dict)
    blind_spots: List[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return asdict(self)


# ─────────────────────────────────────────────────────────────────────────────
# 低レベルヘルパ（すべて純粋関数）
# ─────────────────────────────────────────────────────────────────────────────

def parse_records(text: str) -> List[dict]:
    """JSONL を1行1レコードにパース。壊れた行はスキップ（安全側）。"""
    records = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            records.append(json.loads(line))
        except (json.JSONDecodeError, ValueError):
            continue
    return records


def _normalize(s: str) -> str:
    """照合用に空白畳み・小文字化。"""
    return re.sub(r"\s+", " ", s).strip().lower()


def split_sentences(text: str) -> List[str]:
    """日本語（。/改行）と英語（. ! ?）で文分割。粗くてよい（主張抽出の粒度）。"""
    parts = re.split(r"[。\n]+|(?<=[.!?])\s+", text)
    return [p.strip() for p in parts if p.strip()]


def fenced_blocks(text: str) -> List[str]:
    """``` … ``` フェンスの中身を列挙。"""
    return re.findall(r"```[^\n]*\n(.*?)```", text, re.DOTALL)


def strip_code_spans(text: str) -> str:
    """
    ``` フェンス・`` 二重 ``・` 単一 ` の各コードスパンを除去。
    なぜ二重バッククォートも扱うか: Markdown は内部に backtick を含む語を `` ``code`` `` で
    囲む。実データの自己分析が偽ブロックを `` ``user<...>`` `` と二重引用しており、これを
    除去しないと「議論」を「捏造」と誤検知する（生の捏造だけを残すのが目的）。
    """
    text = re.sub(r"```[\s\S]*?```", " ", text)
    text = re.sub(r"``[\s\S]*?``", " ", text)     # 二重（改行・単一backtick を内包しうる）
    text = re.sub(r"`[^`\n]*`", " ", text)         # 単一
    return text


def flatten_tool_output(content: Any, structured: Any) -> str:
    """
    tool_result の content と toolUseResult を実出力文字列へ平坦化する。
    なぜ両方を見るか: Bash は成功時 content=stdout 文字列、toolUseResult=dict{stdout,stderr}
    と二重に持ち、失敗/拒否時は str になる（mark_*_tests_passed.py と同じ 3 形態）。
    最も情報量の多い結合文字列を返し、成功判定・証拠照合の双方に使う。
    """
    parts: List[str] = []

    if isinstance(content, str):
        parts.append(content)
    elif isinstance(content, list):
        for blk in content:
            if isinstance(blk, dict):
                if isinstance(blk.get("text"), str):
                    parts.append(blk["text"])
                elif isinstance(blk.get("content"), str):
                    parts.append(blk["content"])

    if isinstance(structured, str):
        parts.append(structured)
    elif isinstance(structured, dict):
        raw = structured.get("output", "")
        if isinstance(raw, str) and raw:
            parts.append(raw)
        parts.append(str(structured.get("stdout", "")))
        parts.append(str(structured.get("stderr", "")))

    return "\n".join(p for p in parts if p)


def _looks_truncated(content: Any, structured: Any, text: str) -> bool:
    """大容量出力オフロードの痕跡（2KB プレビュー＋persistedOutputSize）を検出。"""
    if isinstance(structured, dict) and "persistedOutputSize" in structured:
        return True
    return "persistedOutputSize" in text or "Preview (first 2KB" in text or "Preview (first 2 KB" in text


def read_offload(transcript_path: Optional[str], tool_use_id: str) -> Optional[str]:
    """
    オフロード全文 <dir>/<stem>/tool-results/<tool_use_id>.txt を read-only で読む。
    読めなければ None（呼び出し側が truncated 扱いにする）。
    """
    if not transcript_path or not tool_use_id:
        return None
    d = os.path.dirname(transcript_path)
    stem = os.path.basename(transcript_path)
    if stem.endswith(".jsonl"):
        stem = stem[:-6]
    path = os.path.join(d, stem, "tool-results", f"{tool_use_id}.txt")
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return None


def is_success_test_result(command: str, output: str, is_error: bool) -> bool:
    """
    テスト実行が成功したかを判定。mark_kotlin_tests_passed.py（および Phase 5 撤去済みの
    旧 mark_python 版）の判定を移植。unittest/pytest 分岐はプロダクトの Python 撤去後も
    hook 自己テスト等の transcript 照合に使うため残す:
      gradle: 出力に "BUILD SUCCESSFUL"
      unittest: output.rstrip().endswith("\\nOK") かつ "Ran N tests in"
      pytest: "N passed" があり "N failed/error" が無い
      am instrument（実機 androidTest）: JUnit テキスト形式 "OK (N tests)" があり失敗痕跡が無い
    """
    if is_error or not output:
        return False
    # instrument を gradle 判定より先に置く必要はない（コマンドパターンは排他）が、
    # 失敗痕跡（FAILURES!!!／プロセスクラッシュ／INSTRUMENTATION_FAILED）の除外が必須:
    # instrument はテスト失敗でも exit 0 で is_error にならない（adb 経由の実測挙動）。
    if re.search(r"\bam\s+instrument\b", command):
        return (bool(re.search(r"\bOK\s*\(\d+\s*tests?\)", output))
                and not re.search(r"FAILURES!!!|Process crashed|INSTRUMENTATION_(?:FAILED|ABORTED)",
                                  output))
    if re.search(r"test\w*UnitTest|connected\w*AndroidTest", command):
        return "BUILD SUCCESSFUL" in output
    if re.search(r"-m\s+unittest|\bunittest\b", command):
        return output.rstrip().endswith("\nOK") and bool(re.search(r"Ran \d+ tests? in", output))
    if re.search(r"\bpytest\b", command):
        return bool(re.search(r"\d+\s+passed", output)) and not re.search(r"\d+\s+(?:failed|error)", output)
    return False


# ─────────────────────────────────────────────────────────────────────────────
# 証拠集合
# ─────────────────────────────────────────────────────────────────────────────

class EvidenceCorpus:
    """
    「ここに現れるトークンは捏造ではない」証拠の集合。
    含めるもの: 全 tool_result 本文／ユーザ人間入力／tool_use.input／サブエージェント全文。
    含めないもの: assistant の text（＝検査対象であり証拠ではない）。

    2層構造（正解データ事象F②で層別化）:
      ・一般層（contains）… 上記すべて。A1 のフェンス照合など「実出力の引用」判定に使う。
      ・result 層（result_contains）… tool_result 本文（エコーバック行を除外）＋ユーザ人間入力
        ＋コンパクション summary のみ。**tool_use.input を含めない**。SHA・出力シグネチャの
        存在照合はこちらを使う。
        なぜ: 捏造 SHA を自分で `git show <捏造SHA>` と調査すると、コマンド入力（tool_use.input）
        とそのエコーバック／git エラー（fatal: ambiguous argument '<捏造SHA>'）が証拠に載り、
        捏造トークンが自己免罪される。入力に書いたトークンの出現は存在証明ではない。
    """

    def __init__(self) -> None:
        self._chunks: List[str] = []
        self._blob: str = ""
        self._result_chunks: List[Tuple[int, str]] = []   # (order, 原文)
        self._result_norm: List[Tuple[int, str]] = []     # (order, 正規化済み)
        self._result_blob: str = ""
        self._numbers: Optional[set] = None
        self.has_truncation: bool = False
        self.agent_unresolved: bool = False

    def add(self, s: str) -> None:
        if s:
            self._chunks.append(s)

    def add_result_evidence(self, s: str, order: int = -1) -> None:
        """result 層（＝存在照合に使える強い証拠）のみへ追加。一般層は呼び出し側で別途 add する
        （result 層はエコーバック除去済みテキスト・一般層は全文、と中身が異なるため）。
        order は全レコード軸の出現位置。-1 は「時系列不明＝常に主張以前」扱い
        （ユーザ入力・summary 等、偽陽性防止側に倒す）。"""
        if s:
            self._result_chunks.append((order, s))

    def finalize(self) -> None:
        self._blob = _normalize("\n".join(self._chunks))
        self._result_norm = [(o, _normalize(s)) for o, s in self._result_chunks]
        self._result_blob = "\n".join(t for _, t in self._result_norm)

    def contains(self, token: str) -> bool:
        if not token:
            return False
        return _normalize(token) in self._blob

    def result_contains(self, token: str, before: Optional[int] = None) -> bool:
        """result 層でトークンの存在を照合。before を渡すと「その順序より前の証拠」に限定する。
        なぜ時系列限定が要るか（事象F③④）: 捏造の後で同種操作を本当にやり直すと（自己訂正）、
        その実出力がセッション全域照合では過去の捏造まで免罪してしまう。"""
        if not token:
            return False
        t = _normalize(token)
        if before is None:
            return t in self._result_blob
        return any(o < before and t in s for o, s in self._result_norm)

    def result_numbers_before(self, before: int) -> set:
        """result 層（エコーバック除去済み実出力）由来の重要数値集合を「before より前」に限定して
        返す（Tier D2 の数値突合用）。tool_use.input を含まない result 層を使う理由は
        result_contains と同じ: 捏造数値を自分で委譲文・コマンドに書くと自己免罪される。"""
        out: set = set()
        for o, s in self._result_chunks:
            if o < before:
                out.update(IMPORTANT_NUM_RE.findall(s))
        return out

    @property
    def numbers(self) -> set:
        if self._numbers is None:
            self._numbers = {int(n) for n in re.findall(r"\d+", "\n".join(self._chunks))}
        return self._numbers


def strip_echoed_lines(result_text: str, input_text: str) -> str:
    """
    tool_result から「コマンド入力に含まれるトークンが現れる行」を除外した証拠テキストを返す。
    なぜ行ごと落とすか: echo バック（`=== <捏造SHA> の正体 ===`）や git のエラーメッセージ
    （`fatal: ambiguous argument '<捏造SHA>'`）は入力トークンの反射であって存在証明ではない。
    一方 `git show <短縮SHA>` が出す `commit <40桁フルSHA>` 行は、行内トークン（フルSHA）が
    入力（短縮SHA）と exact 一致しないため証拠として残る＝実在 SHA の照合は壊れない。
    """
    if not input_text or not result_text:
        return result_text
    kept = []
    for ln in result_text.splitlines():
        tokens = COMMIT_SHA_RE.findall(ln) + OUTPUT_SIGNATURE_RE.findall(ln)
        if any(t and t in input_text for t in tokens):
            continue
        kept.append(ln)
    return "\n".join(kept)


# ─────────────────────────────────────────────────────────────────────────────
# 構築（レコード → 発話・ツール索引・証拠）
# ─────────────────────────────────────────────────────────────────────────────

def _content_of(rec: dict) -> Any:
    msg = rec.get("message")
    if isinstance(msg, dict):
        return msg.get("content")
    return None


def build_utterances(records: List[dict]) -> List[Utterance]:
    """
    main（非 sidechain）の assistant レコードを message.id で束ねて発話にする
    （thinking→text→tool_use の分割行を集約）。
    order は「全レコード列でのインデックス」: sidechain を含む他イベント（書き込み tool_use 等）
    と同一軸で前後関係を比較できるようにするため（Tier C3 の時系列裏取りが要る）。
    """
    groups: Dict[str, dict] = {}
    order_of: Dict[str, int] = {}
    for idx, rec in enumerate(records):
        if rec.get("type") != "assistant" or rec.get("isSidechain"):
            continue
        msg = rec.get("message") or {}
        # message.id が無い異常行は uuid で代替（束ねられず単独発話になるが安全）。
        mid = msg.get("id") or rec.get("uuid") or f"_anon{idx}"
        g = groups.setdefault(mid, {"text": [], "tools": [], "ts": rec.get("timestamp", ""),
                                    "sig": 0})
        if mid not in order_of:
            order_of[mid] = idx
        content = msg.get("content")
        if isinstance(content, list):
            for blk in content:
                if not isinstance(blk, dict):
                    continue
                if blk.get("type") == "text" and isinstance(blk.get("text"), str):
                    g["text"].append(blk["text"])
                elif blk.get("type") == "thinking":
                    # Tier D の軸2（thinking 異常）用に signature 長を発話束へ集約
                    g["sig"] = max(g["sig"], len(blk.get("signature") or ""))
                elif blk.get("type") == "tool_use":
                    g["tools"].append(ToolUse(
                        id=blk.get("id", ""),
                        name=blk.get("name", ""),
                        input=blk.get("input") or {},
                        msg_id=mid,
                        order=order_of[mid],
                    ))
        elif isinstance(content, str):
            g["text"].append(content)

    utterances = [
        Utterance(msg_id=mid, text="\n".join(g["text"]), tool_uses=g["tools"],
                  timestamp=g["ts"], order=order_of[mid], thinking_sig_max=g["sig"])
        for mid, g in groups.items()
    ]
    utterances.sort(key=lambda u: u.order)
    if utterances:
        utterances[-1].is_last_turn = True
    return utterances


def index_tool_results(records: List[dict], transcript_path: Optional[str]) -> Dict[str, ToolResult]:
    """全（main＋sidechain）user レコードから tool_result を toolu_id 索引化。オフロードは可能なら解決。"""
    index: Dict[str, ToolResult] = {}
    for rec_order, rec in enumerate(records):
        if rec.get("type") != "user":
            continue
        content = _content_of(rec)
        if not isinstance(content, list):
            continue  # 人間入力（str）はここでは扱わない
        structured = rec.get("toolUseResult")
        for blk in content:
            if not isinstance(blk, dict) or blk.get("type") != "tool_result":
                continue
            tuid = blk.get("tool_use_id", "")
            if not tuid:
                continue
            text = flatten_tool_output(blk.get("content"), structured)
            truncated = _looks_truncated(blk.get("content"), structured, text)
            if truncated:
                full = read_offload(transcript_path, tuid)
                if full is not None:
                    text = text + "\n" + full
                    truncated = False
            index[tuid] = ToolResult(
                tool_use_id=tuid,
                is_error=bool(blk.get("is_error", False)),
                text=text,
                structured=structured,
                truncated=truncated,
                order=rec_order,
            )
    return index


def collect_tool_use_inputs(records: List[dict]) -> Dict[str, str]:
    """全レコード（sidechain 含む）の tool_use を id→input JSON 文字列で索引化。
    strip_echoed_lines のエコーバック判定（入力に書いたトークンか）に使う。"""
    m: Dict[str, str] = {}
    for rec in records:
        if rec.get("type") != "assistant":
            continue
        content = _content_of(rec)
        if not isinstance(content, list):
            continue
        for blk in content:
            if isinstance(blk, dict) and blk.get("type") == "tool_use" and blk.get("id"):
                m[blk["id"]] = json.dumps(blk.get("input") or {}, ensure_ascii=False)
    return m


def collect_human_inputs(records: List[dict]) -> List[Tuple[int, str]]:
    """
    「実在する人間入力」を（全レコード軸の順序, テキスト）で列挙する（Tier D の突合正本）。
    含めるもの:
      ・user 行 content=str（ただしハーネス著者の task-notification / system-reminder 等は除外）
      ・user 行 content=list 内の text ブロック（interrupt 直後のユーザー入力等）
      ・queued_command attachment で origin.kind=="human" の prompt
        （task-notification も queued_command として届くが origin が無い＝実測で判別可能）
      ・AskUserQuestion の tool_result（ユーザーが選択肢に回答した内容＝人間由来。
        較正実測: これを含めないと「回答→ツール実行→同意」の正当応答が偽陽性化する）
      ・summary レコード（コンパクション要約は過去の人間発話を含む。order=-1＝
        「常に以前」扱いで引用突合のみに効き、D3 の直前区間判定には掛からない）
    """
    # AskUserQuestion 判定用に tool_use id → name を索引化（sidechain 含む全レコード）
    tu_names: Dict[str, str] = {}
    for rec in records:
        if rec.get("type") != "assistant":
            continue
        content = _content_of(rec)
        if isinstance(content, list):
            for blk in content:
                if isinstance(blk, dict) and blk.get("type") == "tool_use" and blk.get("id"):
                    tu_names[blk["id"]] = blk.get("name", "")

    humans: List[Tuple[int, str]] = []
    for idx, rec in enumerate(records):
        if rec.get("isSidechain"):
            continue
        t = rec.get("type")
        if t == "summary" and isinstance(rec.get("summary"), str):
            humans.append((-1, rec["summary"]))
            continue
        if t == "attachment":
            att = rec.get("attachment") or {}
            if (att.get("type") == "queued_command"
                    and isinstance(att.get("origin"), dict)
                    and att["origin"].get("kind") == "human"):
                # 画像添付付き入力では prompt が str でなく content ブロックの list になる
                # （2026-07-09 実測: 「[Image #2]」付き入力で _human_blob の join がクラッシュ）。
                # text ブロックのみ抽出して文字列化する（画像バイナリは突合対象外）。
                p = att.get("prompt") or ""
                if isinstance(p, list):
                    p = "\n".join(b.get("text", "") for b in p
                                  if isinstance(b, dict) and b.get("type") == "text")
                if isinstance(p, str) and p:
                    humans.append((idx, p))
            continue
        if t != "user":
            continue
        content = _content_of(rec)
        if isinstance(content, str):
            if content and not HARNESS_INPUT_PREFIX_RE.search(content):
                humans.append((idx, content))
        elif isinstance(content, list):
            for blk in content:
                if not isinstance(blk, dict):
                    continue
                if blk.get("type") == "text" and blk.get("text"):
                    humans.append((idx, blk["text"]))
                elif (blk.get("type") == "tool_result"
                      and tu_names.get(blk.get("tool_use_id", "")) == "AskUserQuestion"):
                    txt = flatten_tool_output(blk.get("content"), rec.get("toolUseResult"))
                    if txt:
                        humans.append((idx, txt))
    return humans


def last_turn_start_order(records: List[dict]) -> int:
    """
    最後の「ターン開始」人間入力の全レコード軸 order を返す（無ければ -1）。
    含めるもの: user 行 content=str（ハーネス著者除外）／user 行 list 内の text ブロック
    （interrupt 直後の入力）／queued_command(origin.kind=human)。
    AskUserQuestion の回答を**含めない理由**（台帳L）: 回答はターン途中の入力であり、これを
    境界にすると「捏造発話 → AskUserQuestion → 回答 → 継続」の並びで回答前の捏造発話が
    Stop の検査窓から漏れる（scope=last_turn の穴の機序そのもの）。
    """
    last = -1
    for idx, rec in enumerate(records):
        if rec.get("isSidechain"):
            continue
        t = rec.get("type")
        if t == "attachment":
            att = rec.get("attachment") or {}
            if (att.get("type") == "queued_command"
                    and isinstance(att.get("origin"), dict)
                    and att["origin"].get("kind") == "human"
                    and att.get("prompt")):
                last = idx
            continue
        if t != "user":
            continue
        content = _content_of(rec)
        if isinstance(content, str):
            if content and not HARNESS_INPUT_PREFIX_RE.search(content):
                last = idx
        elif isinstance(content, list):
            if any(isinstance(b, dict) and b.get("type") == "text" and b.get("text")
                   for b in content):
                last = idx
    return last


def build_evidence_corpus(records: List[dict], tool_index: Dict[str, ToolResult],
                          main_utterances: List[Utterance],
                          transcript_path: Optional[str]) -> EvidenceCorpus:
    corpus = EvidenceCorpus()
    tool_inputs = collect_tool_use_inputs(records)

    # 1) 全 tool_result 本文（main＋sidechain）。
    #    一般層＝全文／result 層＝エコーバック行除去後（SHA・出力シグネチャの存在照合用）。
    for tuid, tr in tool_index.items():
        corpus.add(tr.text)
        corpus.add_result_evidence(strip_echoed_lines(tr.text, tool_inputs.get(tuid, "")),
                                   order=tr.order)
        if tr.truncated:
            corpus.has_truncation = True

    # 2) ユーザ人間入力（message.content が str の user 行）＝ユーザ提示値の引用を真判定するため。
    #    ユーザが示したトークンは存在照合でも証拠（ユーザ入力の復唱は捏造ではない）→ 両層へ。
    for rec_order, rec in enumerate(records):
        if rec.get("type") == "user":
            c = _content_of(rec)
            if isinstance(c, str):
                corpus.add(c)
                corpus.add_result_evidence(c, order=rec_order)

    # 2b) コンパクション summary＝前文脈の要約。要約由来のトークン（過去コミット SHA 等）の
    #     復唱を捏造と誤検知しないため両層へ。内容は常に「過去」なので order=-1（常に以前扱い）。
    for rec in records:
        if rec.get("type") == "summary" and isinstance(rec.get("summary"), str):
            corpus.add(rec["summary"])
            corpus.add_result_evidence(rec["summary"], order=-1)

    # 3) tool_use.input（Read の file_path・Bash の command 等、モデルが正規に扱った具体値）。
    #    一般層のみ。result 層に入れると「捏造トークンを自分で調査したコマンド」が自己免罪になる
    #    （正解データ事象F②の機序）。
    for u in main_utterances:
        for tu in u.tool_uses:
            corpus.add(json.dumps(tu.input, ensure_ascii=False))

    # 4) サブエージェント全文（Agent 委譲先の tool_result を証拠へ）
    #    なぜ: 「委譲して実行した」報告は委譲先 JSONL に実体がある。読めなければ降格用フラグを立てる。
    for u in main_utterances:
        for tu in u.tool_uses:
            if tu.name != "Agent":
                continue
            tr = tool_index.get(tu.id)
            agent_id = ""
            if tr and isinstance(tr.structured, dict):
                agent_id = tr.structured.get("agentId", "") or ""
            sub_text = _read_subagent(transcript_path, agent_id) if agent_id else None
            if sub_text is None:
                corpus.agent_unresolved = True
            else:
                # サブエージェント JSONL からも tool_result 本文を抽出して証拠化（層別も同様）。
                # order は親 Agent の tool_result 位置（委譲の完了＝実行はそれ以前）。
                parent_order = tr.order if tr else -1
                sub_records = parse_records(sub_text)
                sub_inputs = collect_tool_use_inputs(sub_records)
                sub_index = index_tool_results(sub_records, None)
                for sub_tuid, str_ in sub_index.items():
                    corpus.add(str_.text)
                    corpus.add_result_evidence(
                        strip_echoed_lines(str_.text, sub_inputs.get(sub_tuid, "")),
                        order=parent_order)

    corpus.finalize()
    return corpus


def _read_subagent(transcript_path: Optional[str], agent_id: str) -> Optional[str]:
    if not transcript_path or not agent_id:
        return None
    d = os.path.dirname(transcript_path)
    stem = os.path.basename(transcript_path)
    if stem.endswith(".jsonl"):
        stem = stem[:-6]
    path = os.path.join(d, stem, "subagents", f"agent-{agent_id}.jsonl")
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return None


# ─────────────────────────────────────────────────────────────────────────────
# 検出ルール
# ─────────────────────────────────────────────────────────────────────────────

def _fence_in_corpus(fence: str, corpus: EvidenceCorpus) -> bool:
    """フェンスの意味のある行の過半が証拠に在れば「実結果の引用」とみなす（捏造ではない）。"""
    lines = [ln.strip() for ln in fence.splitlines() if len(ln.strip()) > 3]
    if not lines:
        return False
    hit = sum(1 for ln in lines if corpus.contains(ln))
    return hit >= max(1, len(lines) // 2)


def detect_tier_a1(utterances: List[Utterance], corpus: EvidenceCorpus) -> List[Finding]:
    """端末風フェンス出力なのに、同一発話に tool_use が無く、証拠にも由来しない → 捏造。"""
    findings: List[Finding] = []
    for u in utterances:
        if u.tool_uses:
            continue  # 同一発話に実ツール呼び出しがあれば構造的にセーフ
        for fence in fenced_blocks(u.text):
            if not TERMINAL_FENCE_RE.search(fence):
                continue  # ソースコード等は対象外（出力シグネチャ必須）
            if _fence_in_corpus(fence, corpus):
                continue  # どこかの実 tool_result に由来 → セーフ
            suppressed = "truncation" if corpus.has_truncation else None
            findings.append(Finding(
                tier="A", rule="fenced_output_without_tooluse",
                confidence=0.85 if not suppressed else 0.5,
                msg_id=u.msg_id, timestamp=u.timestamp,
                claim_excerpt=fence.strip()[:200],
                suppressed_reason=suppressed,
            ))
    return findings


def detect_tier_a2(utterances: List[Utterance], corpus: EvidenceCorpus,
                   sha_exists=None) -> List[Finding]:
    """git 文脈で存在しない commit SHA を断言 → 捏造。ファイルパス・行番号は対象外（精度優先）。
    照合は result 層（result_contains）: tool_use.input・エコーバックを証拠にしない
    （事象F②: 捏造 SHA を自分で `git show` 調査すると一般層では自己免罪される）。

    sha_exists（Optional[Callable[[str], bool]]）＝リポジトリに当該 SHA が実在するかの照合を
    アダプタから注入する（core は純ロジック維持＝subprocess を持たない）。
    なぜ必要か（2026-07-09 Stop ライブ実測FP・bcd69bb6）: system prompt の gitStatus
    （Recent commits）由来の実在 SHA への言及は、transcript のどのレコードにも証拠が
    **構造的に存在しない**（system prompt は JSONL に記録されない）。実在する SHA の言及は
    gitStatus・過去セッション等の正当な出所がほとんどなので降格する（免罪でなく降格＝
    CLI レビューには残す。実在 SHA への誤帰属捏造は C1 が別途拾う）。捏造 SHA は実在しない
    （実測: 20d5aa3/9f3c2e1/3fbfe27/d5f8ecb 全て not-found）ため検知力は落ちない。"""
    findings: List[Finding] = []
    for u in utterances:
        # メタ議論免罪（2026-07-09 2日分スイープ実測FP・c4b78e7d）: 捏造の事後検証セッションが
        # 「`20d5aa3` マージは完全な捏造でした」等と捏造 SHA を引用して分析すると active 発火
        # した。Tier B/D と同型の降格を適用（発話単位＋文近傍）。
        meta_utt = _is_meta_utterance(u.text)
        for sent in split_sentences(u.text):
            if EXAMPLE_EXCLUDE_RE.search(sent) or CONDITIONAL_EXCLUDE_RE.search(sent):
                continue
            if not GIT_CONTEXT_RE.search(sent):
                continue  # SHA 断言を git 話題に限定（hex 語の偽陽性を排除）
            for sha in COMMIT_SHA_RE.findall(sent):
                # before=u.order: 主張より前の実出力のみ証拠（SHA は実行後にしか知り得ない。
                # 後続の自己訂正調査で現れたトークンによる免罪を防ぐ）
                if len(sha) < 7 or corpus.result_contains(sha, before=u.order):
                    continue
                if meta_utt or META_DISCUSSION_RE.search(sent):
                    suppressed = "meta_discussion"
                elif sha_exists is not None and sha_exists(sha):
                    suppressed = "sha_exists_in_repo"
                elif corpus.has_truncation:
                    suppressed = "truncation"
                else:
                    suppressed = None
                findings.append(Finding(
                    tier="A", rule="fabricated_concrete_token",
                    confidence=0.8 if not suppressed else 0.5,
                    msg_id=u.msg_id, timestamp=u.timestamp,
                    claim_excerpt=sent.strip()[:200], missing_token=sha,
                    suppressed_reason=suppressed,
                ))
    return findings


def _claim_grounded_in_corpus(sent: str, corpus: "EvidenceCorpus") -> bool:
    """
    主張が引用する「具体的な成功シグネチャ」が実ツール出力に在るか。
    なぜ必要か（実データで判明）: Claude はしばしば実際の gradle/unittest 出力を地の文へ
    引用する（例「BUILD SUCCESSFUL in 15s」「58 tests OK」）。これは捏造ではなく実出力の
    引用なので、その具体トークンが corpus（実 tool_result 由来）に在れば裏取り成立とする。
    汎用の断言（「テストは通った」等・具体トークン無し）はここでは grounding できず、
    別途「成功実行の有無」で判定する（＝汎用捏造は取りこぼさない）。
    """
    if re.search(r"BUILD\s+SUCCESSFUL", sent, re.IGNORECASE) and corpus.contains("BUILD SUCCESSFUL"):
        return True
    for phrase in re.findall(r"Ran\s+\d+\s+tests?", sent, re.IGNORECASE):
        if corpus.contains(phrase):
            return True
    # 「N tests / N件」の件数主張 … 実出力に "Ran N tests" が在れば実測に基づく
    for n in re.findall(r"(\d+)\s*(?:件|tests?)", sent, re.IGNORECASE):
        if corpus.contains(f"Ran {n} test"):
            return True
    return False


def detect_tier_a3(utterances: List[Utterance]) -> List[Finding]:
    """
    assistant の地の文にハーネス専用ブロック（user<background-task-status> / <task-id> /
    <exit-code> 等）が生で現れる → 会話継続・タスク結果の捏造。純構造判定（corpus 不要）。
    バッククォート引用（議論）は strip_code_spans で除外して偽陽性を防ぐ。
    """
    findings: List[Finding] = []
    for u in utterances:
        stripped = strip_code_spans(u.text)
        m = HARNESS_BLOCK_RE.search(stripped)
        if not m:
            continue
        start = max(0, m.start() - 20)
        findings.append(Finding(
            tier="A", rule="fabricated_harness_block",
            confidence=0.9,
            msg_id=u.msg_id, timestamp=u.timestamp,
            claim_excerpt=stripped[start:m.start() + 80].strip()[:200],
            missing_token=m.group(0).strip()[:60],
        ))
    return findings


def _iso_to_epoch(ts: str) -> Optional[float]:
    """ISO8601（末尾 Z 可）→ epoch 秒。失敗は None。"""
    if not ts:
        return None
    try:
        from datetime import datetime
        return datetime.fromisoformat(ts.replace("Z", "+00:00")).timestamp()
    except (ValueError, TypeError):
        return None


def _sentinel_state(sentinel_dir: Optional[str], claim_ts: str) -> Optional[dict]:
    """
    既存センチネル（.kotlin_tests_passed）の存在と、
    主張時刻との mtime 前後関係を返す。sentinel_dir 未指定なら None（照合しない）。
    （.python_tests_passed は Phase 5 の Python 撤去＋mark_python hook 廃止（2026-07-06）で
    生成者が消滅したため照合対象から外した。残骸ファイルを拾うと誤ったナッジになる。）
    なぜ live 時のみ有効か: センチネルは現在の FS 状態を表すため、過去セッションの
    事後解析では意味が薄い。よって「補助的な信頼度ナッジ」に留める。
    """
    if not sentinel_dir:
        return None
    claim_epoch = _iso_to_epoch(claim_ts)
    state = {"present": False, "fresh": False}
    for name in (".kotlin_tests_passed",):
        p = os.path.join(sentinel_dir, name)
        if os.path.exists(p):
            state["present"] = True
            mt = os.path.getmtime(p)
            if claim_epoch is not None and mt >= claim_epoch:
                state["fresh"] = True
    return state


def detect_tier_b(utterances: List[Utterance], all_utterances: List[Utterance],
                  tool_index: Dict[str, ToolResult], corpus: EvidenceCorpus,
                  sentinel_dir: Optional[str]) -> List[Finding]:
    """
    テスト成功を断言しているのに、セッション内に対応する成功テスト実行が無い → 未検証主張。
    corroboration（裏取り）は主張と同順以前の成功実行。降格条件は truncation / Agent 委譲未解決。
    """
    # セッション全域の「成功テスト実行」の order を集める（scope に関わらず全発話から）。
    # gradle 実行は別建てでも持つ（成功時に件数を出力しないランナー＝件数主張の免罪判定に使う）。
    successful_run_orders: List[int] = []
    successful_gradle_orders: List[int] = []
    for u in all_utterances:
        for tu in u.tool_uses:
            if tu.name != "Bash":
                continue
            cmd = tu.input.get("command", "") if isinstance(tu.input, dict) else ""
            if not TEST_RUNNER_CMD_RE.search(cmd):
                continue
            tr = tool_index.get(tu.id)
            if tr and is_success_test_result(cmd, tr.text, tr.is_error):
                successful_run_orders.append(u.order)
                # connectedAndroidTest も gradle 同様に成功時件数を出力しないランナー
                if re.search(r"test\w*UnitTest|connected\w*AndroidTest", cmd):
                    successful_gradle_orders.append(u.order)

    findings: List[Finding] = []
    for u in utterances:
        for sent in split_sentences(u.text):
            claim_matches = list(CLAIM_TEST_SUCCESS_RE.finditer(sent))
            if not claim_matches:
                continue
            if EXAMPLE_EXCLUDE_RE.search(sent) or CONDITIONAL_EXCLUDE_RE.search(sent):
                continue
            # 具体的な件数/シグネチャ（「28件 OK」「BUILD SUCCESSFUL」「Ran N tests」）を
            # 含む主張か。含むなら、その具体値が実出力に在る時だけ裏取りする。
            # なぜ分けるか（正解データ事象Dの偽陰性対策）: 「セッション内に成功実行が1回でも
            # あれば以降を全て免罪」だと、早期の実runが後半の別作業の具体捏造（CP3-5 の
            # unittest 28件 OK 等・未実行）まで免罪してしまう。具体主張は具体照合に限定する。
            has_concrete = bool(
                re.search(r"BUILD\s+SUCCESSFUL", sent, re.IGNORECASE)
                or re.search(r"Ran\s+\d+\s+tests?", sent, re.IGNORECASE)
                or re.search(r"\d+\s*(?:件|tests?)", sent, re.IGNORECASE)
            )
            if has_concrete:
                # 具体主張: 具体値が実出力に在れば裏取り（無ければ捏造の疑い）。汎用の過去runでは免罪しない。
                if _claim_grounded_in_corpus(sent, corpus):
                    continue
                # gradle は成功時に件数を出力しない（BUILD SUCCESSFUL のみ）ため、「N件通過」型の
                # 件数だけの主張は grounding が構造的に不可能（実データ c05efed0 の偽陽性で判明）。
                # セッション内のどこかに gradle 成功実行が在れば、そのスイートの件数要約とみなして
                # 免罪する（件数の正誤は静的に検証不能＝precision 優先）。
                # 順序を問わないのは意図的: セッション冒頭の主張は前セッション実績の引き継ぎ要約
                # （クロスセッション参照＝ADR 0006 の既知盲点）でありうる。当セッション内で同
                # スイートが後に成功していれば裏は取れている。Stop フック（scope=last_turn）では
                # 主張が常に最終発話＝成功実行は必然的に主張以前なので、「実行前の完了主張」を
                # live で止める力は変わらない。unittest の「Ran N tests」形式が出るランナーの
                # 件数食い違いは従来どおり flag される。
                count_only = not re.search(r"BUILD\s+SUCCESSFUL|Ran\s+\d+\s+tests?",
                                           sent, re.IGNORECASE)
                if count_only and successful_gradle_orders:
                    continue
            else:
                # 汎用の断言（「テストは通った」等）: セッション内に成功実行があれば裏取り。
                if any(r <= u.order for r in successful_run_orders):
                    continue

            # 裏取りなし。降格条件を先に判定（Stop ブロック対象から外す）。
            # meta_discussion（2026-07-08 実測FP）: 捏造の検証・報告をする発話が捏造文言
            # （「回帰テスト：全通過」等）を引用すると、引用が claim 化して偽陽性になる。
            # Tier D の meta_discussion 降格と同型の免罪を Tier B にも適用（発話単位＋文近傍）。
            # quoted_claim（2026-07-09 実測FP・e4367031）: 成功文言のマッチが全て鉤括弧引用の
            # 内側 → 他者の主張の再掲・列挙であって発話者自身の完了断言ではない。
            # 「全て」を要求する理由: 引用外に1つでも裸の成功断言があればそれは自身の主張。
            suppressed = None
            if _is_meta_utterance(u.text) or META_DISCUSSION_RE.search(sent):
                suppressed = "meta_discussion"
            elif all(_match_inside_quote(sent, m) for m in claim_matches):
                suppressed = "quoted_claim"
            elif corpus.has_truncation:
                suppressed = "truncation"
            elif corpus.agent_unresolved:
                suppressed = "subagent_unresolved"

            confidence = 0.5 if suppressed else 0.8
            st = _sentinel_state(sentinel_dir, u.timestamp)
            # センチネルが「新鮮に存在」＝実行痕跡はあるが本文が解析範囲外の可能性 → 弱い裏取りで降格
            if not suppressed and st and st.get("fresh"):
                confidence = 0.6

            findings.append(Finding(
                tier="B", rule="unverified_test_claim",
                confidence=confidence,
                msg_id=u.msg_id, timestamp=u.timestamp,
                claim_excerpt=sent.strip()[:200],
                expected_tool_pattern=TEST_RUNNER_CMD_RE.pattern,
                sentinel_state=st, suppressed_reason=suppressed,
            ))
    return findings


# ─────────────────────────────────────────────────────────────────────────────
# Tier C 検出（misread 型: ペアは在るが報告が実結果と食い違う）
# ─────────────────────────────────────────────────────────────────────────────

def _suppression(corpus: EvidenceCorpus) -> Optional[str]:
    """Tier C 共通の降格条件。証拠が全解決でない時は Stop ブロック対象から外す。"""
    if corpus.has_truncation:
        return "truncation"
    if corpus.agent_unresolved:
        return "subagent_unresolved"
    return None


def detect_tier_c1(utterances: List[Utterance], all_utterances: List[Utterance],
                   tool_index: Dict[str, ToolResult], corpus: EvidenceCorpus) -> List[Finding]:
    """
    フックにブロックされたコミット/マージが、成功の再試行なしに「完了」報告される → 捏造。
    事象F①: `git commit`（マージコミット）が PreToolUse フックでブロックされた（実 result は
    「コミットをブロックします」）のに、再試行せず「マージ完了（`<捏造SHA>`）」と報告。
    コミット系コマンドの判定は hooks_common の単一定義（--no-commit の merge は「成功」に
    数えない＝コミットは未生成、が定義側で保証される）。
    """
    blocked_orders: List[int] = []
    success_orders: List[int] = []
    for u in all_utterances:
        for tu in u.tool_uses:
            if tu.name != "Bash":
                continue
            cmd = tu.input.get("command", "") if isinstance(tu.input, dict) else ""
            if not (hooks_common.COMMIT_CMD_RE.search(cmd)
                    or hooks_common.COMMIT_GENERATING_RE.search(cmd)):
                continue
            tr = tool_index.get(tu.id)
            if tr is None:
                continue
            if tr.is_error and HOOK_BLOCK_RESULT_RE.search(tr.text):
                blocked_orders.append(u.order)
            elif not tr.is_error:
                success_orders.append(u.order)
    if not blocked_orders:
        return []

    findings: List[Finding] = []
    for u in utterances:
        prior = [o for o in blocked_orders if o < u.order]
        if not prior:
            continue
        last_block = max(prior)
        if any(last_block < o < u.order for o in success_orders):
            continue  # ブロック後に成功の再試行あり → 完了報告は正当
        # メタ議論免罪（2026-07-09 2日分スイープ実測FP・c4b78e7d）: 捏造の事後検証セッションが
        # 過去の捏造完了報告（「マージ完了」等）を引用・分析すると active 発火した。
        # Tier A2/B/D と同型の降格（発話単位＋文近傍）。
        meta_utt = _is_meta_utterance(u.text)
        for sent in split_sentences(u.text):
            if not COMMIT_DONE_CLAIM_RE.search(sent):
                continue
            if EXAMPLE_EXCLUDE_RE.search(sent) or CONDITIONAL_EXCLUDE_RE.search(sent):
                continue
            if COMMIT_DONE_META_EXCLUDE_RE.search(sent):
                continue  # 時点表現・状態記述・指示の復唱/解釈＝完了の断言ではない
            # 同一文で自らブロックに言及している（「ブロックされたので保留」等）＝誤認ではなく
            # 状況説明なので claim 化しない。
            if re.search(r"ブロック|block", sent, re.IGNORECASE):
                continue
            if meta_utt or META_DISCUSSION_RE.search(sent):
                suppressed = "meta_discussion"
            else:
                suppressed = _suppression(corpus)
            findings.append(Finding(
                tier="C", rule="completion_after_blocked_commit",
                confidence=0.85 if not suppressed else 0.5,
                msg_id=u.msg_id, timestamp=u.timestamp,
                claim_excerpt=sent.strip()[:200],
                expected_tool_pattern="git commit/merge 成功の tool_result",
                suppressed_reason=suppressed,
            ))
    return findings


def detect_tier_c2(utterances: List[Utterance], corpus: EvidenceCorpus) -> List[Finding]:
    """
    git 実出力にしか現れないシグネチャ（`[deleted]`・`Deleted branch` 等）の引用が、
    実 tool_result のどこにも無い → 実行結果の見た目だけを作文した捏造。
    事象F④: push 出力しか無いのに「`[deleted]`×4」と報告。
    照合は result 層（エコーバック除外済み）。
    """
    findings: List[Finding] = []
    for u in utterances:
        for sent in split_sentences(u.text):
            sigs = sorted(set(OUTPUT_SIGNATURE_RE.findall(sent)))
            if not sigs:
                continue
            if EXAMPLE_EXCLUDE_RE.search(sent) or CONDITIONAL_EXCLUDE_RE.search(sent):
                continue
            for sig in sigs:
                # before=u.order: 捏造の後で同種操作を本当にやり直した場合（自己訂正）の
                # 実出力が過去の捏造を免罪しないよう、主張以前の証拠に限定（事象F④）
                if corpus.result_contains(sig, before=u.order):
                    continue
                suppressed = _suppression(corpus)
                findings.append(Finding(
                    tier="C", rule="fabricated_output_signature",
                    confidence=0.85 if not suppressed else 0.5,
                    msg_id=u.msg_id, timestamp=u.timestamp,
                    claim_excerpt=sent.strip()[:200], missing_token=sig[:60],
                    suppressed_reason=suppressed,
                ))
    return findings


def _write_events(records: List[dict], tool_index: Dict[str, ToolResult]) -> List[Tuple[int, str]]:
    """
    書き込み系 tool_use を（全レコード軸の順序, 照合対象文字列）で列挙する。
    照合対象は Edit/Write/NotebookEdit の file_path、または書き込みうる Bash の command 全文。
    result がエラーの書き込みは裏取りにしない（書けていない）。result 不在（中断等）は
    安全側＝裏取りに数える（偽陽性回避を優先する精度方針）。
    """
    events: List[Tuple[int, str]] = []
    for idx, rec in enumerate(records):
        if rec.get("type") != "assistant":
            continue
        content = _content_of(rec)
        if not isinstance(content, list):
            continue
        for blk in content:
            if not isinstance(blk, dict) or blk.get("type") != "tool_use":
                continue
            name = blk.get("name", "")
            inp = blk.get("input") or {}
            if not isinstance(inp, dict):
                continue
            tr = tool_index.get(blk.get("id", ""))
            if tr is not None and tr.is_error:
                continue
            if name in ("Edit", "Write", "NotebookEdit", "MultiEdit"):
                p = inp.get("file_path") or inp.get("notebook_path") or ""
                if p:
                    events.append((idx, p))
            elif name == "Bash":
                cmd = inp.get("command", "")
                if cmd and BASH_WRITE_HINT_RE.search(cmd):
                    events.append((idx, cmd))
    return events


def _subagent_write_blobs(main_utterances: List[Utterance], tool_index: Dict[str, ToolResult],
                          transcript_path: Optional[str]) -> List[str]:
    """Agent 委譲先 JSONL 内の書き込み tool_use を照合文字列で列挙（時系列は不明＝常に裏取り扱い。
    読めない委譲は corpus.agent_unresolved が既に立っており C3 は降格される）。"""
    blobs: List[str] = []
    for u in main_utterances:
        for tu in u.tool_uses:
            if tu.name != "Agent":
                continue
            tr = tool_index.get(tu.id)
            agent_id = ""
            if tr and isinstance(tr.structured, dict):
                agent_id = tr.structured.get("agentId", "") or ""
            sub_text = _read_subagent(transcript_path, agent_id) if agent_id else None
            if sub_text is None:
                continue
            sub_records = parse_records(sub_text)
            sub_index = index_tool_results(sub_records, None)
            for _, blob in _write_events(sub_records, sub_index):
                blobs.append(blob)
    return blobs


def detect_tier_c3(utterances: List[Utterance], all_utterances: List[Utterance],
                   records: List[dict], tool_index: Dict[str, ToolResult],
                   corpus: EvidenceCorpus, transcript_path: Optional[str]) -> List[Finding]:
    """
    対象を特定できる「書き込み完了」断言（ファイル名明示 or auto-memory）に、対応する
    書き込み tool_use が主張より前に存在しない → 捏造。
    事象F⑤: memory 本体への Edit/Write がセッション全域でゼロ（直前は Read のみ）なのに
    「memory 本体を更新しました」。近接する別ファイルへの Edit（MEMORY.md 索引）は主張の
    「後」なので時系列条件が要る（全域照合だと免罪されて見逃す）。
    """
    events = _write_events(records, tool_index)
    # Agent 委譲はセッション全域（all_utterances）から探す（scope=last_turn でも委譲の実体は全域）
    sub_blobs = [b.lower() for b in _subagent_write_blobs(
        all_utterances, tool_index, transcript_path)]

    findings: List[Finding] = []
    for u in utterances:
        for sent in split_sentences(u.text):
            if not WRITE_DONE_CLAIM_RE.search(sent):
                continue
            if EXAMPLE_EXCLUDE_RE.search(sent) or CONDITIONAL_EXCLUDE_RE.search(sent):
                continue
            hints = [os.path.basename(t).lower() for t in FILE_TOKEN_RE.findall(sent)]
            if MEMORY_TARGET_RE.search(sent):
                hints.append("memory")
            if not hints:
                continue  # 対象を特定できない汎用主張は検査しない（精度優先）
            grounded = any(
                idx < u.order and any(h in blob.lower() for h in hints)
                for idx, blob in events
            ) or any(any(h in b for h in hints) for b in sub_blobs)
            if grounded:
                continue
            suppressed = _suppression(corpus)
            findings.append(Finding(
                tier="C", rule="unverified_write_claim",
                confidence=0.8 if not suppressed else 0.5,
                msg_id=u.msg_id, timestamp=u.timestamp,
                claim_excerpt=sent.strip()[:200],
                missing_token=",".join(sorted(set(hints)))[:60],
                expected_tool_pattern="Edit|Write|NotebookEdit|Bash(リダイレクト等)",
                suppressed_reason=suppressed,
            ))
    return findings


def detect_tier_c4(utterances: List[Utterance], corpus: EvidenceCorpus) -> List[Finding]:
    """
    ブランチ削除の完了断言に、削除の実出力（`Deleted branch`＝ローカル／`[deleted]`＝リモート）が
    セッションのどこにも無い → 捏造。事象F③: `wt-rm` は worktree を撤去しただけ（ブランチ残存）
    なのに「ローカルブランチ3本削除完了」。
    """
    findings: List[Finding] = []
    for u in utterances:
        for sent in split_sentences(u.text):
            if not BRANCH_DELETE_CLAIM_RE.search(sent):
                continue
            if EXAMPLE_EXCLUDE_RE.search(sent) or CONDITIONAL_EXCLUDE_RE.search(sent):
                continue
            # before=u.order: 後続の自己訂正（本当の削除やり直し）の実出力による免罪を防ぐ（事象F③）
            if (corpus.result_contains("Deleted branch", before=u.order)
                    or corpus.result_contains("[deleted]", before=u.order)):
                continue
            suppressed = _suppression(corpus)
            findings.append(Finding(
                tier="C", rule="unverified_branch_delete_claim",
                confidence=0.85 if not suppressed else 0.5,
                msg_id=u.msg_id, timestamp=u.timestamp,
                claim_excerpt=sent.strip()[:200],
                expected_tool_pattern="Deleted branch / [deleted] を含む tool_result",
                suppressed_reason=suppressed,
            ))
    return findings


# ─────────────────────────────────────────────────────────────────────────────
# Tier D 検出（入力側捏造: 幻のユーザー発話への言及・引用・応答）
# ─────────────────────────────────────────────────────────────────────────────

def _human_blob(humans: List[Tuple[int, str]]) -> str:
    """人間入力の引用突合用ブロブ（空白除去正規化）。引用は表記ゆれで空白が変わるため。"""
    return re.sub(r"\s+", "", "\n".join(t for _, t in humans))


def _prior_sig_baseline(u: Utterance, all_utterances: List[Utterance]) -> int:
    """当該発話より前の発話の thinking sig 長の p25（n<4 は最小値）。
    なぜ中央値でないか: 暴走 thinking はセッション内で連続する（事象H は 165K/172K が連続）
    ため中央値は自己汚染する。p25/最小値は汚染に頑健（定数ブロックの較正実測を参照）。"""
    prior = sorted(x.thinking_sig_max for x in all_utterances
                   if x.order < u.order and x.thinking_sig_max > 0)
    if not prior:
        return 0
    return prior[len(prior) // 4] if len(prior) >= 4 else prior[0]


def _thinking_boosted(u: Utterance, all_utterances: List[Utterance]) -> bool:
    """軸2: 当該発話の thinking sig が先行 baseline 比で異常か（D3 の active 昇格条件）。
    単独ルールにはしない（較正実測: 中央値×5 は正当作業で20%誤発火）。"""
    base = _prior_sig_baseline(u, all_utterances)
    return (base > 0 and u.thinking_sig_max >= THINKING_BOOST_MIN_ABS
            and u.thinking_sig_max >= base * THINKING_BOOST_RATIO)


def _is_meta_utterance(text: str) -> bool:
    """発話全体が幻覚分析等のメタ文書か（分析表では引用行の近傍にメタ語が無いため発話単位）。"""
    return len(META_DISCUSSION_RE.findall(text)) >= META_UTTERANCE_MIN_HITS


def _match_inside_quote(sent: str, m: "re.Match") -> bool:
    """マッチ全体が鉤括弧引用（「」『』）の内側に収まるか（Tier B の引用免罪）。
    マッチの一部だけが引用内（例「テストは「全通過」しました」＝強調の括弧）は False
    ＝発話者自身の断言として検査を継続する。"""
    return any(q.start() <= m.start() and m.end() <= q.end()
               for q in QUOTED_SPAN_RE.finditer(sent))


def detect_tier_d1(utterances: List[Utterance], all_utterances: List[Utterance],
                   humans: List[Tuple[int, str]]) -> List[Finding]:
    """
    ユーザー発話の引用「あなたが「X」と言った」の X が、実在する人間入力のどこにも無い
    → 幻の発話の引用（事象H③: 全記録に不存在の「ツールを叩く前に」を引用符付きで“引用”）。
    証拠は人間入力のみ（tool_result は無関係）なので truncation 降格は不要。
    """
    blob = _human_blob(humans)
    findings: List[Finding] = []
    for u in utterances:
        if not u.text:
            continue
        meta = _is_meta_utterance(u.text)
        for m in QUOTE_USER_SAID_RE.finditer(u.text):
            quote = re.sub(r"\s+", "", m.group(1))
            if quote and quote in blob:
                continue  # 実在発話の再引用 → セーフ
            ctx = u.text[max(0, m.start() - 120):m.end() + 120]
            if meta or META_DISCUSSION_RE.search(ctx):
                suppressed = "meta_discussion"
            elif CROSS_SESSION_RE.search(ctx):
                # 別セッションの発話への言及は当該 transcript 単体で裏取り不能（既知盲点と同型）
                suppressed = "cross_session_reference"
            else:
                suppressed = None
            conf = 0.85 if not suppressed else 0.5
            if not suppressed and _thinking_boosted(u, all_utterances):
                conf = 0.9  # 軸2: 暴走 thinking 直後（G/H/I 共通の前兆）は確度を上げる
            findings.append(Finding(
                tier="D", rule="fabricated_user_quote",
                confidence=conf,
                msg_id=u.msg_id, timestamp=u.timestamp,
                claim_excerpt=m.group(0).strip()[:200],
                missing_token=m.group(1)[:60],
                expected_tool_pattern="実在する人間入力（user/queued_command(human)/AskUserQuestion回答）",
                suppressed_reason=suppressed,
            ))
    return findings


def detect_tier_d2(utterances: List[Utterance], all_utterances: List[Utterance],
                   humans: List[Tuple[int, str]], corpus: EvidenceCorpus) -> List[Finding]:
    """
    「ユーザー由来の新情報がある」と主張する発話（「そちらの①の不具合」「という報告がある」）
    の重要数値が、実在する人間入力にも「主張以前の」実 tool_result にも無い → 幻の報告
    （事象I①: 全記録に不存在の「約2000件中5〜6件（約0.3%）」を根拠に指示違反ピボット）。
    数値を発話全体から取る理由（較正実測）: マーカー文と数値文が離れる（I は文3に数値）。
    result 層も突合に加える理由: 実出力由来の数値なら誤帰属ではあっても捏造ではない（精度優先）。
    """
    human_nums = set()
    for _, t in humans:
        human_nums.update(IMPORTANT_NUM_RE.findall(t))

    findings: List[Finding] = []
    for u in utterances:
        if not u.text:
            continue
        m = USER_REPORT_MARKER_RE.search(u.text)
        if not m:
            continue
        ctx = u.text[max(0, m.start() - 150):m.end() + 150]
        if _is_meta_utterance(u.text) or META_DISCUSSION_RE.search(ctx):
            suppressed = "meta_discussion"
            missing: List[str] = []
        else:
            nums = set(IMPORTANT_NUM_RE.findall(u.text))
            if not nums:
                # 数値の無い帰属主張は突合不能 → 検査しない（精度優先）
                suppressed = "no_concrete_token"
                missing = []
            else:
                grounded = human_nums | corpus.result_numbers_before(u.order)
                missing = sorted(nums - grounded)
                if not missing:
                    continue  # 全数値がどこかに実在 → 免罪
                # result 層が不完全なら「無い」判定を信用できないので降格
                suppressed = _suppression(corpus)
        conf = 0.8 if not suppressed else 0.5
        if not suppressed and _thinking_boosted(u, all_utterances):
            conf = 0.85
        findings.append(Finding(
            tier="D", rule="fabricated_user_report",
            confidence=conf,
            msg_id=u.msg_id, timestamp=u.timestamp,
            claim_excerpt=m.group(0).strip()[:200],
            missing_token=",".join(missing)[:60] or None,
            expected_tool_pattern="人間入力または主張以前の実 tool_result に同じ数値",
            suppressed_reason=suppressed,
        ))
    return findings


def detect_tier_d3(utterances: List[Utterance], all_utterances: List[Utterance]) -> List[Finding]:
    """
    発話冒頭の同意・謝罪マーカーなのに、直前入力区間に人間由来入力が無い → 幻の叱責への応答
    候補（事象H②: 誰も発していない叱責へ「完全に、その通りです。言い訳できません」）。
    単独では suppressed（正当応答でも「指示→ツール実行→同意」の並びで直前区間が空になりうる）。
    軸2（暴走 thinking 直後）が重なったときのみ active 昇格する——G/H/I で共通観測された
    「幻テキスト直前の thinking signature 異常（同セッション通常比5〜30倍）」を確度に使う。
    """
    findings: List[Finding] = []
    for u in utterances:
        head = u.text[:PHANTOM_HEAD_CHARS] if u.text else ""
        if not head or not PHANTOM_RESPONSE_RE.search(head):
            continue
        if u.human_input_precedes:
            continue  # 直前に実入力あり → 正当応答
        if _is_meta_utterance(u.text) or META_DISCUSSION_RE.search(
                u.text[:PHANTOM_HEAD_CHARS + 200]):
            suppressed = "meta_discussion"
        elif not _thinking_boosted(u, all_utterances):
            # 応答マーカー＋入力欠落だけでは正当応答と区別しきれない（較正実測: 自己訂正の
            # 謝罪等）。thinking 異常の共起が無ければブロック対象から外し CLI レビューに委ねる。
            suppressed = "no_thinking_anomaly"
        else:
            suppressed = None
        findings.append(Finding(
            tier="D", rule="phantom_user_response",
            confidence=0.8 if not suppressed else 0.5,
            msg_id=u.msg_id, timestamp=u.timestamp,
            claim_excerpt=head.strip()[:200],
            expected_tool_pattern="直前入力区間の人間由来入力",
            suppressed_reason=suppressed,
        ))
    return findings


# ─────────────────────────────────────────────────────────────────────────────
# エントリポイント
# ─────────────────────────────────────────────────────────────────────────────

def analyze(text: str, transcript_path: Optional[str] = None,
            scope: str = "all", sentinel_dir: Optional[str] = None,
            tiers: str = "ABCD", sha_exists=None) -> Report:
    """
    トランスクリプト JSONL 文字列を解析し Report を返す。唯一のエントリ。
      scope="all"       … 全 assistant 発話の主張を検査
      scope="last_turn" … 最後の assistant 発話の主張のみ検査（証拠・成功実行は全域から集める）
      tiers             … A=ペア欠落系 / B=未検証テスト主張 / C=misread（報告と実結果の食い違い）
                          / D=入力側捏造（幻のユーザー発話への言及・引用・応答）
      sha_exists        … Optional[Callable[[str], bool]]。SHA がリポジトリに実在するかの照合を
                          アダプタから注入（Tier A2 の降格判定。None なら照合しない＝従来動作。
                          core を純ロジックに保つため subprocess はアダプタ側が持つ）
    """
    records = parse_records(text)

    # 主張検査は main のみ（build_utterances が sidechain をスキップ）、証拠は sidechain も含める。
    all_utterances = build_utterances(records)
    tool_index = index_tool_results(records, transcript_path)  # main＋sidechain 両方索引
    corpus = build_evidence_corpus(records, tool_index, all_utterances, transcript_path)

    # Tier D 用: 実在する人間入力を索引化し、各発話に「直前入力区間の人間入力有無」を注釈する。
    # 区間は〈前の発話の開始 order, 当発話の開始 order〉: 前発話の tool_result 等が挟まっても
    # それは人間入力ではないので判定に影響しない。
    humans = collect_human_inputs(records)
    prev_order = -1
    for u in all_utterances:  # build_utterances が order 昇順ソート済み
        u.human_input_precedes = any(prev_order < h < u.order for h, _ in humans)
        prev_order = u.order

    # 検査対象の発話（claims）を scope で絞る
    if scope == "last_turn":
        # 台帳L対策: 「最後の発話のみ」だと、捏造発話の直後に AskUserQuestion 等の tool_use が
        # 続いてターンが継続した場合に検査窓から漏れる。最終ターン（最後のターン開始入力より後）
        # の全発話を検査する。ターン開始入力が見つからない異常系のみ従来の最終発話へフォールバック。
        boundary = last_turn_start_order(records)
        if boundary >= 0:
            target = [u for u in all_utterances if u.order > boundary]
        else:
            target = [u for u in all_utterances if u.is_last_turn]
    else:
        target = all_utterances

    findings: List[Finding] = []
    if "A" in tiers:
        findings += detect_tier_a1(target, corpus)
        findings += detect_tier_a2(target, corpus, sha_exists=sha_exists)
        findings += detect_tier_a3(target)
    if "B" in tiers:
        findings += detect_tier_b(target, all_utterances, tool_index, corpus, sentinel_dir)
    if "C" in tiers:
        findings += detect_tier_c1(target, all_utterances, tool_index, corpus)
        findings += detect_tier_c2(target, corpus)
        findings += detect_tier_c3(target, all_utterances, records, tool_index, corpus,
                                   transcript_path)
        findings += detect_tier_c4(target, corpus)
    if "D" in tiers:
        findings += detect_tier_d1(target, all_utterances, humans)
        findings += detect_tier_d2(target, all_utterances, humans, corpus)
        findings += detect_tier_d3(target, all_utterances)

    # 信頼度降順で安定ソート
    findings.sort(key=lambda f: (f.suppressed_reason is not None, -f.confidence))

    session_id = ""
    if transcript_path:
        session_id = os.path.basename(transcript_path)
        if session_id.endswith(".jsonl"):
            session_id = session_id[:-6]

    blind_spots: List[str] = []
    if corpus.has_truncation:
        blind_spots.append("オフロードで全文未解決の tool_result あり（依存 finding は降格）")
    if corpus.agent_unresolved:
        blind_spots.append("サブエージェント委譲の実体を読めず（Tier B は降格）")

    counts: Dict[str, int] = {}
    for f in findings:
        key = "suppressed" if f.suppressed_reason else f.rule
        counts[key] = counts.get(key, 0) + 1

    return Report(
        session_id=session_id,
        scanned=len(target),
        findings=findings,
        counts=counts,
        blind_spots=blind_spots,
    )


if __name__ == "__main__":
    # import 安全のためのガード。CLI としての本体は analyze_transcript.py が担う。
    # ここでは簡易セルフチェックのみ（引数にファイルを渡せば要約を出す）。
    import sys
    if len(sys.argv) > 1:
        with open(sys.argv[1], encoding="utf-8", errors="replace") as _f:
            _rep = analyze(_f.read(), transcript_path=sys.argv[1])
        print(f"scanned={_rep.scanned} findings={len(_rep.findings)} counts={_rep.counts}")
    else:
        print("usage: detect_fabricated_execution_core.py <transcript.jsonl>  "
              "(通常は analyze_transcript.py を使う)")
