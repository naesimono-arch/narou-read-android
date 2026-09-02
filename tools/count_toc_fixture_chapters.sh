#!/usr/bin/env bash
# scrape ゴールデンの章数を「実装を通さずに」数え、ゴールデンテストの期待値と突き合わせる。
#
# なぜ必要か: 章数は *GoldenTest.kt の assertEquals でしか担保されておらず、その期待値は
# パーサ実装の出力を写して作られている＝オラクルが実装系譜の内側にある。実装が壊れれば
# 期待値も一緒に嘘になり、テストは緑のまま通り続ける（独立再実装実験で顕在化した構造欠陥）。
# fixture の HTML から純テキストで数え直せば、系譜の外側に第二の目を置ける。
#
# ゲート結線: .github/workflows/ci.yml の「TOC 章数の実装非依存突合」ステップ（push/PR ごと）。
# なぜ JVM テストへ入れないか: 突合の相手方は *GoldenTest.kt に書かれた**リテラルそのもの**であって
# 「66」という値ではない。テスト側へ移すと期待値をもう一度書き写すことになり、
# 「壊れた実装に合わせて golden の 66 を 65 へ書き換える」退行を新テストも一緒に素通りさせる
# （両方直せば緑）＝独立性という結線の目的そのものが消える。だから突合はソース文面を読む側に置く。
#
# 使い方: bash tools/count_toc_fixture_chapters.sh   （不一致・検査不能なら exit 1）
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIX="$ROOT/android/app/src/test/resources/scrape_fixtures"
TESTS="$ROOT/android/app/src/test/java/com/novelreader/scrape"

# テスト側が主張している章数を取り出す（突合の相手方）。
expected_from_test() {
  grep -o 'assertEquals([0-9]\+, toc\.chapters\.size)' "$1" 2>/dev/null \
    | grep -o '[0-9]\+' | head -1
}

# 暁: 話へのリンクを数える。sort -u は同一話への重複リンクを二重計上しないため。
# リンクで数えるのが要点＝章見出し行は colspan の <b>…</b> でリンクを持たないので、
# 見出し行の混入（AkatsukiGoldenTest が警戒しているドリフト）に影響されない。
count_akatsuki() {
  grep -o 'href="/stories/view/[0-9]\+/novel_id~[0-9]\+"' "$1" | sort -u | wc -l
}

# カクヨム: 目次は埋め込み JSON で、話は Episode 型のノードとして1件につき1回現れる。
# href は7件しか無く（作品トップ・レビュー等）目次の数え上げには使えない。
count_kakuyomu() {
  grep -o '"__typename":"Episode"' "$1" | wc -l
}

fail=0
check() { # 表示名  toc-fixture  test-file  counter-fn
  local site="$1" toc="$2" test_file="$3" fn="$4" got want tname
  tname="$(basename "$test_file")"
  # ⚠️ fixture 欠落・期待値の読み取り失敗を SKIP（緑）にしない。この検査は
  # 「テストが緑のまま嘘をつく」ことを見張る側なので、自分自身が黙って無効化される経路を残すと
  # 見張りごと消えて誰も気づかない（2026-07-12 のセンチネル13日間死亡と同じ失敗クラス）。
  if [ ! -f "$toc" ]; then
    printf 'NG   %s: 目次 fixture が無い (%s) ＝実装非依存の突合が成立しない\n' "$site" "$toc"
    fail=1; return
  fi
  want="$(expected_from_test "$test_file")"
  if [ -z "$want" ]; then
    printf 'NG   %s: %s から assertEquals(N, toc.chapters.size) を読めない ＝突合の相手方を見失った\n' "$site" "$tname"
    fail=1; return
  fi
  got="$("$fn" "$toc" | tr -d ' ')"
  if [ "$got" = "$want" ]; then
    printf 'OK   %s: 実装非依存カウント=%s 章 ／ %s の期待値=%s 章\n' "$site" "$got" "$tname" "$want"
  else
    printf 'NG   %s が %s 章でなく %s 章になった（%s の期待値=%s ／ fixture を実装非依存に数え直すと=%s）\n' \
      "$site" "$want" "$got" "$tname" "$want" "$got"
    printf '     どちらが動いたかを見ること: fixture=%s ／ 期待値=%s\n' "$toc" "$test_file"
    fail=1
  fi
}

check 暁       "$FIX/akatsuki/toc_4679.html" "$TESTS/AkatsukiGoldenTest.kt" count_akatsuki
check カクヨム "$FIX/kakuyomu/toc_16816927859675616240.html" "$TESTS/KakuyomuGoldenTest.kt" count_kakuyomu

exit $fail
