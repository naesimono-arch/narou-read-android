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
# 実機検証系: 実装系の規律に加えて実機固有の禁忌を注入し、adb 禁止だけ解除する。
#   なぜ分けるか（2026-07-31）: 実機の禁忌は「破ると取り返しがつかない」種類（蔵書DBの消失・
#   他人の端末への誤操作）なのに、委譲のたびに監督が手でブリーフへ転記していた（同日3体で3回）。
#   転記は漏れる——実際 /device-verify skill を「読め」と指示しても読んだ保証は無いので、
#   結局ブリーフにも重複して書く羽目になっていた。起動時注入なら漏れようがない。
DEVICE_TYPES = {"device-verify"}

# 長走行の待ち方（2026-08-06 に実機検証2便が連続で踏んだ駐機事故の焼き込み。機序の正本＝
#   docs/knowledge/subagent-idle-stop-parks-forever.md）:
#   サブエージェントは「追跡中のバックグラウンド子ゼロ」でターンを終えるとその場で完了扱いになり、
#   自走で再開される経路が無い。メインセッションの「background 完了でターン再起動」仕様はサブでは
#   当てにできず、script が fork して親 bash が即 return する形（adb 経由の非同期実行等）は
#   実作業が生きていても追跡上は子ゼロになる。
FOREGROUND_RULE = (
    "- **長走行コマンドは run_in_background や `&` で切り離さず、フォアグラウンドで timeout を長めに取って待つ**。\n"
    "  「完了通知を待つ」と宣言してツールを呼ばずターンを終えない——あなたの環境では追跡子ゼロの停止＝\n"
    "  完了扱いで駐機となり**自走では二度と再開されない**（2026-08-06 に実機検証2便がこれで停止・監督の手動再開\n"
    "  で復旧した実害）。script が fork して即 return する形（adb 経由の非同期実行など）は、同ターン内で\n"
    "  出力ファイルを自分でポーリングして回収する。10分を超える見込みの単発コマンドは分割する。"
)

# 実機固有の禁忌。いずれも「過去に実害が出た」か「今日実際に踏みかけた」もののみ載せる
#   （一般論を並べると読まれなくなるため）。詳細と症状表は /device-verify skill が正本。
DEVICE_RULES = (
    "【実機検証の禁忌（SubagentStart 自動注入・破ると復旧不能なものだけ列挙）】\n"
    "※ 実機操作そのものは本種別の職務＝躊躇せず行ってよい。制約は以下の範囲。\n"
    "- **実蔵書を絶対に消さない**。削除フローの確認はダイアログを開いて文言を読み、必ずキャンセルまで。\n"
    "  破壊的な検証は捨て本を作って行う（以前これで読書位置・栞・追加日を永久に失った実害あり）。\n"
    "- **実蔵書の読書画面で章を送らない**（送るなら捨て本で）。読書進捗は furthest-wins＝`ReadingProgressStore` は\n"
    "  `episode > 既存` のときだけ更新するため**下げる経路が構造上存在せず、誤って進めると元に戻せない**\n"
    "  （2026-07-31 に自動化の座標タップが「次章」へ誤着弾し、実蔵書の進捗が第5話→第12話へ進んだ）。\n"
    "- `adb uninstall` 禁止／`connectedAndroidTest` 直叩き禁止（AGP が実行後にアプリ本体ごと自動 uninstall し\n"
    "  蔵書DBが消える）。APK 投入は `installDebug` か `install -r`＝上書きで蔵書を保持する。\n"
    "- **2台以上繋がっているときの取り違えに注意**: `adb-bridge` は既存 TCP があると早期リターンするため、\n"
    "  USB で挿した別端末を調べるつもりで既存端末を掴む。`adb devices -l` と `adb.exe devices -l` の両側を\n"
    "  突合し、model を確認してから触る。USB 側にしか居ない端末は `adb.exe -s <serial>` で名指しする。\n"
    "- PATH に `platform-tools` を前置きしない（素の `adb` が生 adb に化けて実機を見失う）。\n"
    "- `adb shell input text` を使わない（ASCII 限定＋IME 状態を壊す）。IME を切り替えたら\n"
    "  **終了時に必ず開始時の値へ戻す**（`settings get secure default_input_method` で先に控える）。\n"
    "- ColorOS では `screenrecord` が全パスで使用不能。動画での検証は諦めて静止画と画素検査で判定する。\n"
    "- **他人の端末では起動系（monkey/am）を使わない**。相手の操作に割り込む（ゲーム中にアプリが展開した実害あり）。\n"
    "- 報告は必ず実際のコマンド出力に基づくこと。screencap の実画素値・dumpsys の数値・uiautomator の\n"
    "  テキストなど根拠を添える。**判定できないものは「判定不能・理由」と書く**（推測で PASS にしない）。"
)


def briefing_for(agent_type, project_dir):
    t = (agent_type or "").lower()
    if t in IMPLEMENTER_TYPES or t in DEVICE_TYPES:
        on_device = t in DEVICE_TYPES
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
        if on_device:
            # 実機系だけ文面を分ける理由: 共通規律は「adb/実機操作は監督が実施」だが、本種別は
            # それ自体が職務なので当たらない。他（コミット・push・正本モック）は実機系でも監督が持つ。
            # 報告様式も実装系（変更ファイル一覧）ではなく検証系（項目ごとの4値判定）が要る。
            return (
                "【プロジェクト定型規律（SubagentStart 自動注入・委譲仕様より優先度は低い＝矛盾時は委譲仕様に従う）】\n"
                "- コミット・push・docs/design-candidates/（正本モック）の変更は禁止（監督が実施）。\n"
                "- 症状だけ隠す修正・try/catch での握り潰し禁止。真因を特定し報告に明記する。\n"
                + FOREGROUND_RULE + "\n"
                "- APK を作り直す必要があるときのゲート:\n"
                f"  {gate}\n"
                "  （./gradlew は Permission denied・非対話シェルは .bashrc を読まないため env 明示が必須）\n"
                "- コード内コメントは日本語・自明でないロジックには「なぜ」を書く（what のみのコメント禁止）。\n"
                "- 報告様式: 項目ごとに PASS / NG / 判定不能 / 人間裁定が要る の4値で。NG には真因と\n"
                "  ファイル:行 を、判定不能には理由を、人間裁定には「何を見れば決まるか」を書く。\n"
                "\n" + DEVICE_RULES
            )
        return (
            "【プロジェクト定型規律（SubagentStart 自動注入・委譲仕様より優先度は低い＝矛盾時は委譲仕様に従う）】\n"
            "- コミット・push・adb/実機操作・docs/design-candidates/（正本モック）の変更は禁止（監督が実施）。\n"
            "- 症状だけ隠す修正・try/catch での握り潰し禁止。真因を特定し報告に明記する。\n"
            "- 完了前に git diff で自分の変更全量を自己確認（意図しないファイル・既存動作パスへの影響が無いこと）。\n"
            + FOREGROUND_RULE + "\n"
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
