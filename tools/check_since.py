#!/usr/bin/env python3
"""@since coverage check for the public API surface (api/ package).

Requires a @since tag on:
  * every declared type (interface / class / record / enum) that is not `private`, and
  * every explicitly-declared public method that introduces its own contract.

Excluded by design (they inherit @since via standard javadoc semantics, so stamping them
would be redundant and misdate the element):
  * implicit record component accessors (never declared, so never matched here),
  * `@Override` methods (inherit the supertype method's @since),
  * private / package-private members (not part of the published surface).

Exit 0 with "OK" when coverage is 100%; exit 1 listing every gap otherwise.
Run: python tools/check_since.py
"""

import pathlib
import re
import sys

API_ROOT = (
    pathlib.Path(__file__).resolve().parent.parent
    / "src/main/java/com/kuronami/isekaiapi/api"
)

TYPE_RE = re.compile(
    r"^\s*(?:@\w[\w.]*\s+)*"
    r"(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+)*"
    r"(interface|class|record|enum)\s+(\w+)"
)
# A method: has a (params) list and ends the signature with { ; or nothing-on-next-line.
METHOD_RE = re.compile(
    r"^\s*(?:public\s+|protected\s+|private\s+|static\s+|final\s+|abstract\s+|default\s+|synchronized\s+)*"
    r"(?:<[^>]*>\s*)?"
    r"[\w.<>\[\],?\s&]+\s+(\w+)\s*\([^)]*\)\s*(?:\{|;|$)"
)

KEYWORDS = {
    "interface",
    "class",
    "record",
    "enum",
    "return",
    "new",
    "if",
    "for",
    "while",
    "switch",
    "catch",
    "throws",
    "throw",
}


def preceding_since(lines, decl_idx):
    """True if the javadoc block immediately above line decl_idx contains @since."""
    i = decl_idx - 1
    # skip annotation lines directly above the declaration
    while i >= 0 and re.match(r"^\s*@\w", lines[i]):
        i -= 1
    if i < 0:
        return False
    # single-line /** ... @since ... */
    if "*/" in lines[i] and "/**" in lines[i]:
        return "@since" in lines[i]
    if "*/" not in lines[i]:
        return False
    # walk up the block to its /**
    j = i
    block = []
    while j >= 0:
        block.append(lines[j])
        if "/**" in lines[j]:
            break
        j -= 1
    return any("@since" in b for b in block)


def main():
    gaps = []
    checked = 0
    for path in sorted(API_ROOT.rglob("*.java")):
        if path.name == "package-info.java":
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        rel = path.relative_to(API_ROOT.parent.parent.parent.parent.parent.parent)
        for idx, line in enumerate(lines):
            tm = TYPE_RE.match(line)
            if tm:
                if re.match(r"^\s*(?:@\w[\w.]*\s+)*private\s", line):
                    continue
                checked += 1
                if not preceding_since(lines, idx):
                    gaps.append(f"{rel}:{idx+1}  TYPE  {tm.group(1)} {tm.group(2)}")
                continue
            # methods: skip @Override (inherits) and non-public in classes
            mm = METHOD_RE.match(line)
            if mm and mm.group(1) not in KEYWORDS and mm.group(1)[0].islower():
                # A real method has a return type before its name; a bare `name(...)` with
                # nothing before it is a call statement or the record's own accessor use.
                head = line.split("(")[0]
                mods = {
                    "public",
                    "private",
                    "protected",
                    "static",
                    "final",
                    "abstract",
                    "default",
                    "synchronized",
                }
                toks = [
                    t for t in re.sub(r"<[^>]*>", " ", head).split() if t not in mods
                ]
                if len(toks) < 2:
                    continue
                # `return foo(...);` inside a method body reads like a declaration whose
                # "return type" is the word `return`. Anything led by a statement keyword
                # is a call site, not a declaration.
                if toks[0] in KEYWORDS:
                    continue
                # exclude @Override
                prev = lines[idx - 1].strip() if idx > 0 else ""
                if prev.startswith("@Override") or "@Override" in line:
                    continue
                # in a class/record body a method needs explicit `public`; interface
                # methods are implicitly public. Approximate: require `public` OR be an
                # interface-body abstract/default/const method (no access modifier, ends ; or {).
                is_public = re.match(r"^\s*(?:@\w[\w.]*\s+)*public\s", line)
                is_iface_member = re.match(
                    r"^\s*(?:default\s+|static\s+)?[\w.<>\[\],?\s&]+\s+\w+\s*\([^)]*\)\s*(?:\{|;)\s*$",
                    line,
                ) and not re.match(r"^\s*(?:private|protected)\s", line)
                if not (is_public or is_iface_member):
                    continue
                checked += 1
                if not preceding_since(lines, idx):
                    gaps.append(f"{rel}:{idx+1}  METHOD {mm.group(1)}()")

    print(f"checked {checked} public elements in api/")
    if gaps:
        print(f"MISSING @since on {len(gaps)} element(s):")
        for g in gaps:
            print("  " + g)
        return 1
    print("OK: @since coverage 100%")
    return 0


if __name__ == "__main__":
    sys.exit(main())
