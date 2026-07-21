#!/usr/bin/env python3
"""Doc snippet gate (mechanical, no eyeballing).

For every fenced ```json / ```jsonc block in the docs it:
  1. Prefix lint — fails on a bare `isekai:` dispatch prefix (must be `isekai_api:`; the bare
     form is a deprecated alias that still *decodes*, so a decode check alone would greenlight
     stale docs — this lint is what actually enforces the v2 vocabulary).
  2. Well-formedness — strips `//` and `/* */` comments and trailing commas, then classifies:
       LITERAL  -> must parse as JSON,
       SCHEMA   -> contains placeholder tokens (`<...>`, `…`); parsing is not expected, but it is
                   still prefix-linted and counted (never silently skipped).

Real *codec* decode of the load-bearing literal snippets lives in the Java DocSnippetDecodeTest
(README apply_worldshape + hook density_function) and ExamplesDecodeTest (examples/**); this
script is the structural + vocabulary layer over all prose docs. Exit 1 on any violation.
"""

import json
import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
DOCS = [
    REPO / "README.md",
    REPO / "docs/DATAPACK_REFERENCE.md",
    REPO / "docs/COMPATIBILITY.md",
]

FENCE_RE = re.compile(r"```(json|jsonc)\n(.*?)```", re.DOTALL)
BARE_ISEKAI_RE = re.compile(r"isekai:(?!//)")  # `isekai:` not part of a URL
CANON_OK_RE = re.compile(r"isekai_api:")
SCHEMA_TOKEN_RE = re.compile(
    r"<[A-Za-z/]|…|\.\.\.|/\*"
)  # <int>, <SpatialPredicate>, …, ..., /* ... */


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)  # block comments
    text = re.sub(r"//[^\n]*", "", text)  # line comments
    text = re.sub(r",(\s*[}\]])", r"\1", text)  # trailing commas
    return text


def main():
    literal_ok = schema = 0
    violations = []
    for doc in DOCS:
        src = doc.read_text(encoding="utf-8")
        for m in FENCE_RE.finditer(src):
            block = m.group(2)
            line0 = src[: m.start()].count("\n") + 1
            where = f"{doc.name}:~{line0}"
            # 1. prefix lint: any `isekai:` that is not `isekai_api:`
            for tok in re.finditer(r"isekai:(?!//)", block):
                # allow only if it is the `isekai_api:` prefix
                start = tok.start()
                if not block[start : start + len("isekai_api:")] == "isekai_api:":
                    violations.append(
                        f"{where}  BARE-PREFIX  '{block[start:start+18].strip()}...'"
                    )
            # 2. well-formedness
            if SCHEMA_TOKEN_RE.search(block):
                schema += 1
                continue
            cleaned = strip_comments(block).strip()
            try:
                json.loads(cleaned)
                literal_ok += 1
            except json.JSONDecodeError:
                # a property fragment (`"key": value`) is valid when brace-wrapped
                try:
                    json.loads("{" + cleaned + "}")
                    literal_ok += 1
                except json.JSONDecodeError as e:
                    violations.append(f"{where}  BAD-JSON  {e}")

    print(f"literal snippets parsed OK: {literal_ok}")
    print(f"schema snippets (placeholders, prefix-linted only): {schema}")
    if violations:
        print(f"VIOLATIONS: {len(violations)}")
        for v in violations:
            print("  " + v)
        return 1
    print("OK: all json/jsonc fences are v2-canonical and well-formed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
