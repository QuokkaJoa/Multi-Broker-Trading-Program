#!/bin/bash
# Claude는 fake 모드만 실행한다. mock·dryrun·live 프로필과 실서버 시험(networkTest)은 사람만 실행한다.
# exit 2 = 차단, 이유는 Claude에게 전달된다. 시험: bash .claude/hooks/block-live.test.sh
input=$(cat)
# 명령만 꺼낸다. 꺼내지 못하면 입력 전체를 검사한다 (막는 쪽으로 실패)
cmd=$(printf '%s' "$input" | python3 -c 'import json,sys; print(json.load(sys.stdin)["tool_input"]["command"])' 2>/dev/null) || cmd=$input

# 앱이나 시험을 실제로 돌리는 명령만 본다. 문서에 글자만 쓰는 명령은 막지 않는다
printf '%s' "$cmd" | grep -Eq '(^|[^a-z])(gradlew|gradle|java|mvnw|mvn)([^a-z]|$)|bootRun' || exit 0

block() {
  echo "$1 fake가 아닌 실행 모드(mock·dryrun·live)와 networkTest는 사람만 실행할 수 있어요. fake 모드를 쓰세요." >&2
  exit 2
}

if printf '%s' "$cmd" | grep -Eqi 'profiles[._]active[^a-z]*[a-z,]*(mock|dryrun|live)|networkTest'; then
  block "명령에 fake가 아닌 모드가 있어요."
fi

# 명령에 프로필이 없으면 설정 파일의 값으로 켜진다. 거기에 fake 아닌 모드가 있어도 막는다
res="${CLAUDE_PROJECT_DIR:-.}/src/main/resources"
if grep -Eqi 'profiles[._:[:space:]]*(active|default|include)[^a-z]*[a-z, -]*(mock|dryrun|live)' \
    "$res"/application*.properties "$res"/application*.yml "$res"/application*.yaml 2>/dev/null; then
  block "설정 파일(src/main/resources/application*)에 fake가 아닌 모드가 있어요."
fi
# yml은 "profiles:" 아래 줄에 "active: ..."가 따로 온다
if grep -Eqi '^[[:space:]]*-?[[:space:]]*(active|default|include)[[:space:]]*:.*(mock|dryrun|live)' \
    "$res"/application*.yml "$res"/application*.yaml 2>/dev/null; then
  block "설정 파일(src/main/resources/application*)에 fake가 아닌 모드가 있어요."
fi
exit 0
