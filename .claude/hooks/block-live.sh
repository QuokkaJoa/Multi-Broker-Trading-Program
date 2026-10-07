#!/bin/bash
# Claude는 fake 모드만 실행한다. mock·dryrun·live 프로필과 실서버 시험(networkTest)은 사람만 실행한다.
# exit 2 = 차단, 이유는 Claude에게 전달된다.
input=$(cat)
if printf '%s' "$input" | grep -Eqi 'profiles[._]active[^a-z]*[a-z,]*(mock|dryrun|live)|networkTest'; then
  echo "fake가 아닌 실행 모드(mock·dryrun·live)와 networkTest는 사람만 실행할 수 있어요. fake 모드를 쓰세요." >&2
  exit 2
fi
exit 0
