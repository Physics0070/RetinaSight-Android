#!/usr/bin/env bash
# bootstrap.sh — check and install everything this project needs (macOS / Linux).
#
#   bash scripts/bootstrap.sh            # check, then install what's missing
#   bash scripts/bootstrap.sh --check    # report only, install nothing
#   bash scripts/bootstrap.sh --yes      # never prompt (for CI / agents)
#
# Reads project-manifest.yml. No dependencies beyond coreutils + awk.
# Exit codes: 0 = all required deps satisfied, 1 = something is still missing.

set -uo pipefail

CHECK_ONLY=0
ASSUME_YES=0
for arg in "$@"; do
  case "$arg" in
    --check) CHECK_ONLY=1 ;;
    --yes|-y) ASSUME_YES=1 ;;
    --help|-h) sed -n '2,10p' "$0"; exit 0 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MANIFEST="$ROOT/project-manifest.yml"

if [ -t 1 ]; then
  R=$'\033[31m'; G=$'\033[32m'; Y=$'\033[33m'; B=$'\033[1m'; N=$'\033[0m'
else
  R=""; G=""; Y=""; B=""; N=""
fi

FAILED=0
declare -a REPORT=()

say()  { printf '%s\n' "$*"; }
head1() { printf '\n%s%s%s\n' "$B" "$*" "$N"; }

# --- platform + package manager -------------------------------------------
OS="$(uname -s)"
PKG=""
PKG_INSTALL=""
case "$OS" in
  Darwin)
    if command -v brew >/dev/null 2>&1; then
      PKG="brew"; PKG_INSTALL="brew install"
    fi ;;
  Linux)
    if   command -v apt-get >/dev/null 2>&1; then PKG="apt";    PKG_INSTALL="sudo apt-get install -y"
    elif command -v dnf     >/dev/null 2>&1; then PKG="apt";    PKG_INSTALL="sudo dnf install -y"
    elif command -v pacman  >/dev/null 2>&1; then PKG="apt";    PKG_INSTALL="sudo pacman -S --noconfirm"
    elif command -v brew    >/dev/null 2>&1; then PKG="brew";   PKG_INSTALL="brew install"
    fi ;;
esac

head1 "Environment"
say "  OS             : $OS ($(uname -m))"
say "  Package manager: ${PKG:-none detected}"
say "  Project root   : $ROOT"

[ -f "$MANIFEST" ] || { say "${R}ERROR${N}: $MANIFEST not found."; exit 1; }

# --- tiny YAML reader ------------------------------------------------------
# Emits one "key=value" line per field for each item under the given block.
# Items are separated by blank "---ITEM---" markers.
read_block() {
  awk -v block="$1" '
    $0 ~ "^" block ":[[:space:]]*$" { inb=1; next }
    inb && /^[a-z_]+:/              { inb=0 }
    inb && /^[[:space:]]*#/         { next }
    inb && /^[[:space:]]*-[[:space:]]/ { print "---ITEM---" }
    inb {
      line=$0
      sub(/^[[:space:]]*-[[:space:]]*/, "", line)
      sub(/^[[:space:]]+/, "", line)
      if (line ~ /^[a-z_]+:/) {
        key=line; sub(/:.*/, "", key)
        val=line; sub(/^[a-z_]+:[[:space:]]*/, "", val)
        gsub(/^"|"$/, "", val)
        sub(/[[:space:]]*#.*$/, "", val)
        gsub(/[[:space:]]+$/, "", val)
        if (val != "") print key "=" val
      }
    }
  ' "$MANIFEST"
}

# major.minor comparison: returns 0 if $1 >= $2
version_ok() {
  local have="$1" want="$2"
  local h_maj h_min w_maj w_min
  h_maj=${have%%.*}; h_min=$(printf '%s' "$have" | cut -d. -f2)
  w_maj=${want%%.*}; w_min=$(printf '%s' "$want" | cut -d. -f2)
  h_min=${h_min:-0}; w_min=${w_min:-0}
  [ "$h_maj" -gt "$w_maj" ] && return 0
  [ "$h_maj" -lt "$w_maj" ] && return 1
  [ "${h_min:-0}" -ge "${w_min:-0}" ]
}

# --- system tools ----------------------------------------------------------
head1 "System tools"
printf '  %-14s %-10s %-14s %s\n' "TOOL" "REQUIRED" "FOUND" "ACTION"
printf '  %-14s %-10s %-14s %s\n' "----" "--------" "-----" "------"

name=""; cmd=""; vflag=""; minv=""; wpkg=""; bpkg=""; apkg=""; req="true"

flush_tool() {
  [ -z "$cmd" ] && return 0
  local found="-" action="-" status
  local raw pkgid installcmd

  if command -v "$cmd" >/dev/null 2>&1; then
    if [ -n "$vflag" ]; then
      raw="$("$cmd" "$vflag" 2>&1 | head -n1)"
      found="$(printf '%s' "$raw" | grep -oE '[0-9]+\.[0-9]+(\.[0-9]+)?' | head -n1)"
      [ -z "$found" ] && found="present"
    else
      found="present"
    fi
    if [ -n "$minv" ] && [ "$found" != "present" ] && ! version_ok "$found" "$minv"; then
      status="WRONG_VERSION"
    else
      status="PRESENT"
    fi
  else
    status="MISSING"
  fi

  if [ "$status" = "PRESENT" ]; then
    printf '  %-14s %-10s %-14s %s\n' "$name" "${minv:-any}" "$found" "${G}ok${N}"
    name=""; cmd=""; vflag=""; minv=""; wpkg=""; bpkg=""; apkg=""; req="true"
    return 0
  fi

  case "$PKG" in
    brew) pkgid="$bpkg" ;;
    apt)  pkgid="$apkg" ;;
    *)    pkgid="" ;;
  esac

  if [ "$CHECK_ONLY" = "1" ] || [ -z "$pkgid" ] || [ -z "$PKG_INSTALL" ]; then
    action="${R}install manually${N}"
    [ "$req" = "true" ] && FAILED=1
  else
    installcmd="$PKG_INSTALL $pkgid"
    say ""
    say "  ${Y}$name is $status.${N} Installing with: $installcmd"
    if [ "$ASSUME_YES" != "1" ] && [ -t 0 ]; then
      read -r -p "  Proceed? [Y/n] " reply
      case "$reply" in [nN]*) action="${Y}skipped${N}"; [ "$req" = "true" ] && FAILED=1;; *) reply=y;; esac
    else
      reply=y
    fi
    if [ "${reply:-y}" = "y" ]; then
      if eval "$installcmd"; then
        action="${G}installed${N}"
        command -v "$cmd" >/dev/null 2>&1 || { action="${R}install failed${N}"; [ "$req" = "true" ] && FAILED=1; }
      else
        action="${R}install failed${N}"
        [ "$req" = "true" ] && FAILED=1
      fi
    fi
  fi

  printf '  %-14s %-10s %-14s %s\n' "$name" "${minv:-any}" "$found" "$action"
  name=""; cmd=""; vflag=""; minv=""; wpkg=""; bpkg=""; apkg=""; req="true"
}

while IFS= read -r line; do
  case "$line" in
    "---ITEM---") flush_tool ;;
    name=*)     name="${line#name=}" ;;
    command=*)  cmd="${line#command=}" ;;
    version=*)  vflag="${line#version=}" ;;
    min=*)      minv="${line#min=}" ;;
    winget=*)   wpkg="${line#winget=}" ;;
    brew=*)     bpkg="${line#brew=}" ;;
    apt=*)      apkg="${line#apt=}" ;;
    required=*) req="${line#required=}" ;;
  esac
done < <(read_block system)
flush_tool

# --- project dependencies --------------------------------------------------
head1 "Project dependencies"
cd "$ROOT" || exit 1
detect=""; installc=""
ran_any=0

flush_proj() {
  [ -z "$detect" ] && return 0
  if [ -f "$ROOT/$detect" ]; then
    say "  ${B}$detect${N} found -> $installc"
    if [ "$CHECK_ONLY" = "1" ]; then
      say "    ${Y}(check-only, not run)${N}"
    else
      if eval "$installc"; then say "    ${G}ok${N}"; else say "    ${R}failed${N}"; FAILED=1; fi
    fi
    ran_any=1
    detect=""; installc=""
    return 1   # signal: stop after first match per ecosystem
  fi
  detect=""; installc=""
  return 0
}

npm_done=0; py_done=0
while IFS= read -r line; do
  case "$line" in
    "---ITEM---")
      case "$detect" in
        package-lock.json|pnpm-lock.yaml|yarn.lock|package.json)
          [ "$npm_done" = "1" ] && { detect=""; installc=""; continue; }
          flush_proj || npm_done=1 ;;
        uv.lock|requirements.txt|pyproject.toml)
          [ "$py_done" = "1" ] && { detect=""; installc=""; continue; }
          flush_proj || py_done=1 ;;
        *) flush_proj ;;
      esac ;;
    detect=*)  detect="${line#detect=}" ;;
    install=*) installc="${line#install=}" ;;
  esac
done < <(read_block project)
flush_proj

[ "$ran_any" = "0" ] && say "  ${Y}No recognised dependency file in $ROOT — nothing to install.${N}"

# --- env vars --------------------------------------------------------------
head1 "Environment variables"
[ -f "$ROOT/.env" ] && set -a && . "$ROOT/.env" >/dev/null 2>&1; set +a
missing_env=0
while IFS= read -r line; do
  [ "$line" = "---ITEM---" ] && continue
  key="$line"
  [ -z "$key" ] && continue
  if [ -n "${!key:-}" ]; then
    printf '  %-28s %s\n' "$key" "${G}set${N}"
  else
    printf '  %-28s %s\n' "$key" "${R}MISSING — add it to .env${N}"
    missing_env=1
  fi
done < <(awk '/^env:[[:space:]]*$/{i=1;next} i&&/^[a-z_]+:/{i=0} i&&/^[[:space:]]*-[[:space:]]/{gsub(/^[[:space:]]*-[[:space:]]*/,"");print}' "$MANIFEST")
[ "$missing_env" = "0" ] && say "  (none declared or all set)"

# --- verdict ---------------------------------------------------------------
head1 "Result"
if [ "$FAILED" = "0" ]; then
  say "  ${G}Environment ready.${N}"
  exit 0
else
  say "  ${R}Some required dependencies are still missing (see above).${N}"
  exit 1
fi
