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
from typing import Any, Dict, List, Optional


# ─────────────────────────────────────────────────────────────────────────────
# 公開定数（test_hooks.py 方式で SHOULD_MATCH/SHOULD_NOT_MATCH 回帰固定する対象）
# ─────────────────────────────────────────────────────────────────────────────

# 「テスト成功の断言」。小語彙・保守的（再現率より精度）。
CLAIM_TEST_SUCCESS_RE = re.compile(
    r"(?:テスト|ユニットテスト|単体テスト|ユニット試験|unit\s*test)"
    r"[^。\n]{0,24}?(?:通(?:った|過|り(?:ました)?)|パス(?:しました|した)?|成功|緑|green|\bOK\b)"
    r"|BUILD\s+SUCCESSFUL"
    r"|Ran\s+\d+\s+tests?[\s\S]{0,40}?\bOK\b"
    r"|\d+\s*(?:件|tests?)[^。\n]{0,10}?(?:通(?:過|った)|パス|成功|\bOK\b|緑)"
    r"|(?:全て|すべて|全部)[^。\n]{0,12}?(?:通(?:過|った|り)|パス|成功|グリーン|green)",
    re.IGNORECASE,
)

# 仮定法・未来・意図・指示。これが文に在れば「実行の断言」ではないので claim 化しない。
# なぜ広めに取るか: 偽陽性（例「通るはず」「実行しましょう」を捏造と誤検知）を潰すため。
CONDITIONAL_EXCLUDE_RE = re.compile(
    r"はず|だろう|でしょう|べき|すれば|したら|なら\b|れば|見込|想定|つもり|予定|"
    r"する必要|してください|し(?:よう|ましょう)|確認しよう|"
    r"\bshould\b|\bwould\b|\bif\b|\bexpect|\bassume|\bplan\s+to\b|\bwill\b|\blet'?s\b",
    re.IGNORECASE,
)

# 例示。「例えば `./gradlew test` すれば」等を claim 化しない。
EXAMPLE_EXCLUDE_RE = re.compile(
    r"例えば|例:|例：|たとえば|サンプル|のように|の例|コマンド例|"
    r"e\.?g\.|for\s+example|such\s+as",
    re.IGNORECASE,
)

# 端末風出力のシグネチャ（Tier A1）。フェンス内にこれが在れば「実行結果の見た目」。
TERMINAL_FENCE_RE = re.compile(
    r"(?m)"
    r"^\s*[$>#]\s+\S"                       # シェルプロンプト行
    r"|BUILD\s+(?:SUCCESSFUL|FAILED)"
    r"|Ran\s+\d+\s+tests?\s+in\b"
    r"|^\s*OK\s*$"
    r"|^\s*FAILED\b"
    r"|={2,}\s*\d+\s+(?:passed|failed|error)"   # pytest サマリ
    r"|Exit\s+code\s+\d+",
)

# git の commit SHA（小文字 hex のみ＝git は小文字で出す。大文字混在の hex 語を除外して精度確保）。
COMMIT_SHA_RE = re.compile(r"(?<![0-9a-fA-F])[0-9a-f]{7,40}(?![0-9a-fA-F])")

# 「N件」「N tests」。Tier B の裏取り補助（単独ルールにはしない）。
TEST_COUNT_RE = re.compile(r"\b(\d+)\s*(?:件|tests?)\b", re.IGNORECASE)

# 実行イベント側: テストランナー呼び出しコマンド（既存 mark_*_tests_passed.py と整合）。
TEST_RUNNER_CMD_RE = re.compile(r"test\w*UnitTest|-m\s+unittest|\bunittest\b|\bpytest\b")

# git 文脈語（Tier A2 で SHA 断言を git 話題に限定するためのゲート）。
GIT_CONTEXT_RE = re.compile(r"コミット|commit|\bSHA\b|ハッシュ|\bhash\b|リビジョン|revision", re.IGNORECASE)


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


@dataclass
class Utterance:
    msg_id: str
    text: str
    tool_uses: List[ToolUse]
    timestamp: str
    order: int
    is_last_turn: bool = False


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
    テスト実行が成功したかを判定。mark_python/kotlin_tests_passed.py の判定を移植:
      gradle: 出力に "BUILD SUCCESSFUL"
      unittest: output.rstrip().endswith("\\nOK") かつ "Ran N tests in"
      pytest: "N passed" があり "N failed/error" が無い
    """
    if is_error or not output:
        return False
    if re.search(r"test\w*UnitTest", command):
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
    """

    def __init__(self) -> None:
        self._chunks: List[str] = []
        self._blob: str = ""
        self._numbers: Optional[set] = None
        self.has_truncation: bool = False
        self.agent_unresolved: bool = False

    def add(self, s: str) -> None:
        if s:
            self._chunks.append(s)

    def finalize(self) -> None:
        self._blob = _normalize("\n".join(self._chunks))

    def contains(self, token: str) -> bool:
        if not token:
            return False
        return _normalize(token) in self._blob

    @property
    def numbers(self) -> set:
        if self._numbers is None:
            self._numbers = {int(n) for n in re.findall(r"\d+", "\n".join(self._chunks))}
        return self._numbers


# ─────────────────────────────────────────────────────────────────────────────
# 構築（レコード → 発話・ツール索引・証拠）
# ─────────────────────────────────────────────────────────────────────────────

def _content_of(rec: dict) -> Any:
    msg = rec.get("message")
    if isinstance(msg, dict):
        return msg.get("content")
    return None


def build_utterances(main_records: List[dict]) -> List[Utterance]:
    """assistant レコードを message.id で束ねて発話にする（thinking→text→tool_use の分割行を集約）。"""
    groups: Dict[str, dict] = {}
    order_of: Dict[str, int] = {}
    for idx, rec in enumerate(main_records):
        if rec.get("type") != "assistant":
            continue
        msg = rec.get("message") or {}
        # message.id が無い異常行は uuid で代替（束ねられず単独発話になるが安全）。
        mid = msg.get("id") or rec.get("uuid") or f"_anon{idx}"
        g = groups.setdefault(mid, {"text": [], "tools": [], "ts": rec.get("timestamp", "")})
        if mid not in order_of:
            order_of[mid] = idx
        content = msg.get("content")
        if isinstance(content, list):
            for blk in content:
                if not isinstance(blk, dict):
                    continue
                if blk.get("type") == "text" and isinstance(blk.get("text"), str):
                    g["text"].append(blk["text"])
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
                  timestamp=g["ts"], order=order_of[mid])
        for mid, g in groups.items()
    ]
    utterances.sort(key=lambda u: u.order)
    if utterances:
        utterances[-1].is_last_turn = True
    return utterances


def index_tool_results(records: List[dict], transcript_path: Optional[str]) -> Dict[str, ToolResult]:
    """全（main＋sidechain）user レコードから tool_result を toolu_id 索引化。オフロードは可能なら解決。"""
    index: Dict[str, ToolResult] = {}
    for rec in records:
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
            )
    return index


def build_evidence_corpus(records: List[dict], tool_index: Dict[str, ToolResult],
                          main_utterances: List[Utterance],
                          transcript_path: Optional[str]) -> EvidenceCorpus:
    corpus = EvidenceCorpus()

    # 1) 全 tool_result 本文（main＋sidechain）
    for tr in tool_index.values():
        corpus.add(tr.text)
        if tr.truncated:
            corpus.has_truncation = True

    # 2) ユーザ人間入力（message.content が str の user 行）＝ユーザ提示値の引用を真判定するため
    for rec in records:
        if rec.get("type") == "user":
            c = _content_of(rec)
            if isinstance(c, str):
                corpus.add(c)

    # 3) tool_use.input（Read の file_path・Bash の command 等、モデルが正規に扱った具体値）
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
                # サブエージェント JSONL からも tool_result 本文を抽出して証拠化
                sub_records = parse_records(sub_text)
                sub_index = index_tool_results(sub_records, None)
                for str_ in sub_index.values():
                    corpus.add(str_.text)

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


def detect_tier_a2(utterances: List[Utterance], corpus: EvidenceCorpus) -> List[Finding]:
    """git 文脈で存在しない commit SHA を断言 → 捏造。ファイルパス・行番号は対象外（精度優先）。"""
    findings: List[Finding] = []
    for u in utterances:
        for sent in split_sentences(u.text):
            if EXAMPLE_EXCLUDE_RE.search(sent) or CONDITIONAL_EXCLUDE_RE.search(sent):
                continue
            if not GIT_CONTEXT_RE.search(sent):
                continue  # SHA 断言を git 話題に限定（hex 語の偽陽性を排除）
            for sha in COMMIT_SHA_RE.findall(sent):
                if len(sha) < 7 or corpus.contains(sha):
                    continue
                suppressed = "truncation" if corpus.has_truncation else None
                findings.append(Finding(
                    tier="A", rule="fabricated_concrete_token",
                    confidence=0.8 if not suppressed else 0.5,
                    msg_id=u.msg_id, timestamp=u.timestamp,
                    claim_excerpt=sent.strip()[:200], missing_token=sha,
                    suppressed_reason=suppressed,
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
    既存センチネル（.python_tests_passed / .kotlin_tests_passed）の存在と、
    主張時刻との mtime 前後関係を返す。sentinel_dir 未指定なら None（照合しない）。
    なぜ live 時のみ有効か: センチネルは現在の FS 状態を表すため、過去セッションの
    事後解析では意味が薄い。よって「補助的な信頼度ナッジ」に留める。
    """
    if not sentinel_dir:
        return None
    claim_epoch = _iso_to_epoch(claim_ts)
    state = {"present": False, "fresh": False}
    for name in (".python_tests_passed", ".kotlin_tests_passed"):
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
    # セッション全域の「成功テスト実行」の order を集める（scope に関わらず全発話から）
    successful_run_orders: List[int] = []
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

    findings: List[Finding] = []
    for u in utterances:
        for sent in split_sentences(u.text):
            if not CLAIM_TEST_SUCCESS_RE.search(sent):
                continue
            if EXAMPLE_EXCLUDE_RE.search(sent) or CONDITIONAL_EXCLUDE_RE.search(sent):
                continue
            # 主張(order)以前に成功実行があれば裏取り成立 → フラグしない
            if any(r <= u.order for r in successful_run_orders):
                continue

            # 裏取りなし。降格条件を先に判定（Stop ブロック対象から外す）。
            suppressed = None
            if corpus.has_truncation:
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
# エントリポイント
# ─────────────────────────────────────────────────────────────────────────────

def analyze(text: str, transcript_path: Optional[str] = None,
            scope: str = "all", sentinel_dir: Optional[str] = None,
            tiers: str = "AB") -> Report:
    """
    トランスクリプト JSONL 文字列を解析し Report を返す。唯一のエントリ。
      scope="all"       … 全 assistant 発話の主張を検査
      scope="last_turn" … 最後の assistant 発話の主張のみ検査（証拠・成功実行は全域から集める）
    """
    records = parse_records(text)

    # isSidechain を分離: 主張検査は main のみ、証拠は sidechain も含める。
    main_records = [r for r in records if not r.get("isSidechain")]

    all_utterances = build_utterances(main_records)
    tool_index = index_tool_results(records, transcript_path)  # main＋sidechain 両方索引
    corpus = build_evidence_corpus(records, tool_index, all_utterances, transcript_path)

    # 検査対象の発話（claims）を scope で絞る
    if scope == "last_turn":
        target = [u for u in all_utterances if u.is_last_turn]
    else:
        target = all_utterances

    findings: List[Finding] = []
    if "A" in tiers:
        findings += detect_tier_a1(target, corpus)
        findings += detect_tier_a2(target, corpus)
    if "B" in tiers:
        findings += detect_tier_b(target, all_utterances, tool_index, corpus, sentinel_dir)

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
