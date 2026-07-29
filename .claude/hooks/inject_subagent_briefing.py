#!/usr/bin/env python3
"""SubagentStart: サブエージェント種別に応じた定型規律を additionalContext で自動注入する。

なぜ hook で注入するか（2026-07-19 ユーザー指示）:
  司令塔運用（/orchestration）の委譲仕様には毎回同じ定型規律（コミット禁止・真因規律・
  ゲートコマンド・報告様式）を書いており、監督の委譲プロンプトが肥大していた。
  SubagentStart は公式にサブエージェント起動時の additionalContext 注入をサポートする
  （memory `hook-subagent-start-injection`）＝定型部分を機械注入し、委譲仕様は
  タスク固有の内容だけにする。/orchestration スキル側に「定型は自動注入済み」と明記してある。

設計上の注意:
  - agent_type はスクリプト側で判定する（matcher の対象仕様が非明確なため。
    memory `hook-agent-type-confirmed` の実測で全 hook 入力に agent_type が来る）。
  - 注入なし種別（claude-code-guide / statusline-setup / antigravity 系）は
    プロジェクト規律と無関係な外部調査・設定エージェントのため対象外。
  - stdout がモデルに届くのは additionalContext の JSON のみ（task_diary #28）。
  - 失敗は常に fail-open（exit 0・注入なし）＝起動を妨げない。
"""
import json
import os
import sys

from hooks_common import read_payload, wrap_stdio

# 実装・汎用系: 司令塔運用の定型規律をフル注入
IMPLEMENTER_TYPES = {"general-purpose", "claude"}
# 読み取り調査系: 報告様式だけ軽く注入
RESEARCH_TYPES = {"explore", "plan"}


def briefing_for(agent_type, project_dir):
    t = (agent_type or "").lower()
    if t in IMPLEMENTER_TYPES:
        # ゲートコマンドは worktree 間で可搬にするため project_dir から動的に組む
        gate = (
            f'cd {project_dir}/android && '
            'export JAVA_HOME="$HOME/opt/jdk-17" && export ANDROID_HOME="$HOME/Android/Sdk" && '
            'export PATH="$JAVA_HOME/bin:$PATH" && '
            "java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest"
        )
        # androidTest は「コンパイルだけ」を条件付きで回す（CLAUDE.md 自己検証必須節と対応）。
        # 既定ゲートの testDebugUnitTest は androidTest をコンパイルしないため、本番の public
        # シグネチャ変更に追従しないまま壊れて潜伏する（実際に2回発生）。毎回回すと重いので条件付き。
        androidtest_gate = gate.replace(
            "GradleWrapperMain testDebugUnitTest",
            "GradleWrapperMain :app:assembleDebugAndroidTest")
        return (
            "【プロジェクト定型規律（SubagentStart 自動注入・委譲仕様より優先度は低い＝矛盾時は委譲仕様に従う）】\n"
            "- コミット・push・adb/実機操作・docs/design-candidates/（正本モック）の変更は禁止（監督が実施）。\n"
            "- 症状だけ隠す修正・try/catch での握り潰し禁止。真因を特定し報告に明記する。\n"
            "- 完了前に git diff で自分の変更全量を自己確認（意図しないファイル・既存動作パスへの影響が無いこと）。\n"
            "- Kotlin の src/main・src/test を変更したら必ずゲートを回す:\n"
            f"  {gate}\n"
            "  （./gradlew は Permission denied・非対話シェルは .bashrc を読まないため env 明示が必須）\n"
            "- 本番の public シグネチャを変えたら androidTest のコンパイルも確認する:\n"
            f"  {androidtest_gate}\n"
            "- コード内コメントは日本語・自明でないロジックには「なぜ」を書く（what のみのコメント禁止）。\n"
            "- 報告様式: 25行以内。必須＝変更ファイル一覧／機序・真因／較正値と自己決定した判断／ゲート出力要約（実行数・失敗数）。"
        )
    if t in RESEARCH_TYPES:
        return (
            "【調査報告の定型（SubagentStart 自動注入）】報告は25行以内・結論から書く・"
            "参照は file_path:行番号 の形式・推測は「推測」と明記して確定事実と区別する。"
        )
    return None


def main():
    wrap_stdio()
    payload = read_payload()
    if payload is None:
        return  # fail-open: 入力が壊れていても起動は妨げない
    project_dir = os.environ.get("CLAUDE_PROJECT_DIR") or payload.get("cwd") or "."
    msg = briefing_for(payload.get("agent_type"), project_dir)
    if not msg:
        return
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "SubagentStart",
        "additionalContext": msg,
    }}, ensure_ascii=False))


if __name__ == "__main__":
    main()
