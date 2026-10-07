#!/bin/bash
# block-live.sh 시험. 실행: bash .claude/hooks/block-live.test.sh
hook="$(dirname "$0")/block-live.sh"
fail=0

# 가짜 프로젝트 폴더: 설정 파일의 프로필만 바꿔 가며 시험한다
proj=$(mktemp -d)
mkdir -p "$proj/src/main/resources"
trap 'rm -rf "$proj"' EXIT

props() { printf '%s\n' "$1" > "$proj/src/main/resources/application.properties"; }

check() { # 기대(block|pass) 설명 명령
  local input
  input=$(python3 -c 'import json,sys; print(json.dumps({"tool_input":{"command":sys.argv[1]}}))' "$3")
  printf '%s' "$input" | CLAUDE_PROJECT_DIR="$proj" bash "$hook" 2>/dev/null
  local got=$([ $? -eq 2 ] && echo block || echo pass)
  if [ "$got" != "$1" ]; then echo "실패: $2 (기대 $1, 실제 $got)"; fail=1; fi
}

props 'spring.profiles.default=fake'
check pass  "fake로 실행"               "./gradlew bootRun --args='--spring.profiles.active=fake'"
check pass  "시험 실행"                 "./gradlew test"
check block "인자로 live"               "./gradlew bootRun --args='--spring.profiles.active=live'"
check block "환경변수로 dryrun"         "SPRING_PROFILES_ACTIVE=dryrun ./gradlew bootRun"
check block "jar를 mock으로"            "java -jar build/libs/app.jar --spring.profiles.active=mock"
check block "실서버 시험"               "./gradlew networkTest"
# 빈틈 2: 실행이 아닌 명령에 글자만 있는 경우
check pass  "문서에 글자만 씀"          "echo 'networkTest는 사람만 돌린다' >> docs/plan.md"
check pass  "grep으로 찾기"             "grep -rn 'profiles.active=dryrun' docs"

# 빈틈 1: 설정 파일의 기본 프로필을 바꾼 뒤 그냥 실행
props 'spring.profiles.default=dryrun'
check block "기본 프로필이 dryrun"      "./gradlew bootRun"
check block "기본 프로필이 dryrun인데 시험" "./gradlew test"
props 'spring.profiles.active=live'
check block "설정 파일에 active=live"   "./gradlew bootRun"
props 'spring.profiles.default=fake'
printf 'spring.profiles.include=mock\n' > "$proj/src/main/resources/application-fake.properties"
check block "fake가 mock을 끌어옴"      "./gradlew bootRun"
rm "$proj/src/main/resources/application-fake.properties"
printf 'spring:\n  profiles:\n    active: dryrun\n' > "$proj/src/main/resources/application.yml"
check block "yml에 active: dryrun"      "./gradlew bootRun"

# 비밀값 파일 .env는 Claude가 셸로도 읽지 못한다
check block ".env 읽기"                 "cat .env"
check block ".env 경로로 읽기"          "grep toss ./.env"
check block ".env 복사"                 "cp .env /tmp/x"
check pass  ".env.example은 괜찮다"     "cat .env.example"
check pass  "gitignore 보기"            "cat .gitignore"

[ $fail -eq 0 ] && echo "block-live 시험 전부 통과"
exit $fail
