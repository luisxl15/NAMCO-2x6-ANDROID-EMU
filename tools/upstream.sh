#!/usr/bin/env bash
#
# What an upstream update will actually cost, before you start it.
#
# This fork sits on ARMSX2 with the pcsx2x6 arcade layer transplanted in. Both move. The
# expensive question is never "what changed upstream" -- it is "what changed upstream IN THE
# FILES WE ALSO TOUCHED", because those are the only ones that can conflict. Everything else
# merges without anyone reading it.
#
#   tools/upstream.sh            report against ARMSX2
#   tools/upstream.sh pcsx2x6    report against PS2Homebrew-arcade/pcsx2x6
#
# Read-only: fetches, and never checks anything out or rewrites history.
set -euo pipefail

# The upstream commit this fork's own work starts from. Everything after it is ours.
FORK_BASE="6e1e8f0a18f2f5bbcd2e75344d89ff6ba03b5616"

MODE="${1:-armsx2}"
case "$MODE" in
  armsx2)  REMOTE="armsx2-upstream"; URL="https://github.com/ARMSX2/ARMSX2.git"; BRANCH="main" ;;
  pcsx2x6) REMOTE="pcsx2x6-upstream"; URL="https://github.com/PS2Homebrew-arcade/pcsx2x6.git"; BRANCH="master" ;;
  *) echo "uso: $0 [armsx2|pcsx2x6]" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/.."

git remote get-url "$REMOTE" >/dev/null 2>&1 || git remote add "$REMOTE" "$URL"
echo "buscando $REMOTE ..."
# The branch may be named differently; try the configured one, then the remote's HEAD.
git fetch --quiet "$REMOTE" "$BRANCH" 2>/dev/null || git fetch --quiet "$REMOTE"
UP="$(git rev-parse --verify --quiet "$REMOTE/$BRANCH" || git rev-parse "$REMOTE/HEAD")"

# pcsx2x6 is NOT an ancestor of this tree. It and ARMSX2 are separate forks of PCSX2, so
# diffing our fork point against pcsx2x6's tip compares two different histories and reports ten
# thousand "changed" files -- true, and useless. What we actually take from pcsx2x6 is the arcade
# delta, so the question there is "what changed in the arcade code since we transplanted it".
# That needs a recorded pcsx2x6 commit and a path filter; both live below.
ARCADE_PATHS=(
  "pcsx2/DEV9"
  "pcsx2/VMManager.cpp"
  "pcsx2/IopBios.cpp" "pcsx2/IopMem.cpp" "pcsx2/IopModuleNames.cpp" "pcsx2/IopHw.h"
  "pcsx2/Input/InputManager.cpp" "pcsx2/USB/usb-lightgun/guncon2.cpp"
  "pcsx2/GameList.cpp" "pcsx2/Config.h" "pcsx2/Pcsx2Config.cpp"
  "bin/resources/GameIndex.yaml"
)
if [ "$MODE" = "pcsx2x6" ]; then
  STAMP="$(dirname "$0")/pcsx2x6-base.txt"
  if [ ! -f "$STAMP" ]; then
    echo "$UP" > "$STAMP"
    echo "Primeira execucao: gravei $UP em $(basename "$STAMP")."
    echo "Daqui para a frente este relatorio mostra o que mudou no codigo de arcade desde agora."
    echo "Se voce sabe de qual commit do pcsx2x6 o transplante veio, coloque-o nesse arquivo."
    exit 0
  fi
  FROM="$(tr -d '[:space:]' < "$STAMP")"
  echo
  echo "arcade do pcsx2x6, de $FROM ate $(git log -1 --format='%h %ad' --date=short "$UP")"
  echo
  CHANGED="$(git diff --name-only "$FROM".."$UP" -- "${ARCADE_PATHS[@]}" 2>/dev/null || true)"
  if [ -z "$CHANGED" ]; then
    echo "Nada mudou no codigo de arcade nesse intervalo."
    exit 0
  fi
  git diff --numstat "$FROM".."$UP" -- "${ARCADE_PATHS[@]}" |
    awk '{ printf "%6d  %s", $1+$2, $3; print "" }' | sort -rn
  echo
  echo "FORK.md diz o que cada um desses arquivos carrega do nosso lado."
  exit 0
fi

# Files this fork modified in files that already existed upstream. Added files are ours alone
# and can never conflict, so they are deliberately not counted here.
OURS="$(git diff --diff-filter=M --name-only "$FORK_BASE"..HEAD)"
# Files upstream changed since we branched.
THEIRS="$(git diff --name-only "$FORK_BASE".."$UP")"

BOTH="$(comm -12 <(echo "$OURS" | sort) <(echo "$THEIRS" | sort))"

echo
echo "upstream em $(git log -1 --format='%h %ad %s' --date=short "$UP")"
echo "nosso base   $(git log -1 --format='%h %ad' --date=short "$FORK_BASE")"
echo
printf 'arquivos que NOS modificamos      : %d\n' "$(echo "$OURS"   | grep -c . || true)"
printf 'arquivos que o upstream mudou     : %d\n' "$(echo "$THEIRS" | grep -c . || true)"
printf 'INTERSECAO (o trabalho real)      : %d\n' "$(echo "$BOTH"   | grep -c . || true)"
echo

if [ -z "$BOTH" ]; then
  echo "Nenhum conflito possivel: o upstream nao tocou em nada que tocamos."
  exit 0
fi

echo "Arquivos a revisar, do mais mexido para o menos:"
echo "  (nossas linhas / linhas deles)"
while IFS= read -r f; do
  [ -n "$f" ] || continue
  o=$(git diff --numstat "$FORK_BASE"..HEAD  -- "$f" | awk '{print $1+$2}')
  t=$(git diff --numstat "$FORK_BASE".."$UP" -- "$f" | awk '{print $1+$2}')
  printf '%6s / %-6s %s\n' "${o:-0}" "${t:-0}" "$f"
done <<< "$BOTH" | sort -rn

echo
echo "Para cada um deles, FORK.md diz o que colocamos ali e por que."
