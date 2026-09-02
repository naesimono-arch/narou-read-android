#!/usr/bin/env bash
# scrape ゴールデンの章数を「実装を通さずに」数え、ゴールデンテストの期待値と突き合わせる。
#
# なぜ必要か: 章数は *GoldenTest.kt の assertEquals でしか担保されておらず、その期待値は
# パーサ実装の出力を写して作られている＝オラクルが実装系譜の内側にある。実装が壊れれば
# 期待値も一緒に嘘になり、テストは緑のまま通り続ける（独立再実装実験で顕在化した構造欠陥）。
# fixture の HTML から純テキストで数え直せば、系譜の外側に第二の目を置ける。
#
# 使い方: bash tools/count_toc_fixture_chapters.sh   （不一致があれば exit 1）
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
check() { # site  toc-fixture  test-file  counter-fn
  local site="$1" toc="$2" test_file="$3" fn="$4" got want
  if [ ! -f "$toc" ]; then echo "SKIP $site: fixture が無い ($toc)"; return; fi
  got="$("$fn" "$toc" | tr -d ' ')"
  want="$(expected_from_test "$test_file")"
  if [ -z "$want" ]; then echo "SKIP $site: テスト側の期待値を読めない ($test_file)"; return; fi
  if [ "$got" = "$want" ]; then
    printf 'OK   %-9s 実装非依存カウント=%-4s テスト期待値=%-4s\n' "$site" "$got" "$want"
  else
    printf 'NG   %-9s 実装非依存カウント=%-4s テスト期待値=%-4s  ← 不一致\n' "$site" "$got" "$want"
    fail=1
  fi
}

check akatsuki "$FIX/akatsuki/toc_4679.html" "$TESTS/AkatsukiGoldenTest.kt" count_akatsuki
check kakuyomu "$FIX/kakuyomu/toc_16816927859675616240.html" "$TESTS/KakuyomuGoldenTest.kt" count_kakuyomu

exit $fail
