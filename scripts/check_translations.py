#!/usr/bin/env python3
"""Verify every locale against values/strings.xml.

Run before any release build:

    python scripts/check_translations.py

Checks, per locale:
  * every English key is present (a missing key silently falls back to
    English, which on a referral screen means a patient reads advice in a
    language they may not speak)
  * placeholders match exactly - a dropped %1$s is a crash, not a typo
  * no key exists that English does not have (a typo'd key is dead weight)

Exits non-zero on any failure so it can gate a build.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

RES = Path(__file__).resolve().parents[1] / "RetinaSightApp" / "app" / "src" / "main" / "res"
STRING = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)
PLACEHOLDER = re.compile(r"%\d+\$[sd]")

# Brand name. Deliberately identical in every language, so it lives only in
# values/ and is not expected in any locale file.
NEVER_TRANSLATED = {"app_name"}


def parse(path: Path) -> dict[str, str]:
    return {m.group(1): m.group(2) for m in STRING.finditer(path.read_text(encoding="utf-8"))}


def main() -> int:
    english = parse(RES / "values" / "strings.xml")
    print(f"values (English): {len(english)} strings")

    locales = sorted(d for d in RES.iterdir() if d.name.startswith("values-"))
    failures = 0

    for d in locales:
        f = d / "strings.xml"
        if not f.is_file():
            continue
        tr = parse(f)
        missing = [k for k in english if k not in tr and k not in NEVER_TRANSLATED]
        extra = [k for k in tr if k not in english or k in NEVER_TRANSLATED]
        bad = [
            k for k in tr
            if k in english
            and sorted(PLACEHOLDER.findall(english[k])) != sorted(PLACEHOLDER.findall(tr[k]))
        ]

        status = "ok" if not (missing or extra or bad) else "FAIL"
        print(f"{d.name:14s} {len(tr):3d} strings  {status}")
        if missing:
            print(f"    missing ({len(missing)}): {', '.join(missing[:8])}"
                  + (" …" if len(missing) > 8 else ""))
        if extra:
            print(f"    not in English: {', '.join(extra[:8])}")
        if bad:
            print(f"    placeholder mismatch: {', '.join(bad)}")
        if missing or extra or bad:
            failures += 1

    print()
    if failures:
        print(f"{failures} locale(s) failed.")
        return 1
    print(f"All {len(locales)} locales complete and consistent with English.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
