# Isekai API — JSON Schemas

Edit-time validation for the two consumer-authored Isekai JSON formats:

| Schema | Validates |
|---|---|
| [`worldshape.schema.json`](worldshape.schema.json) | `data/<ns>/isekai/worldshape/<name>.json` |
| [`layered_worldshape.schema.json`](layered_worldshape.schema.json) | `data/<ns>/isekai/layered_worldshape/<name>.json` |

These cover the Isekai-specific structure and the sealed dispatch families where typos
actually happen — wrong `"type"` values, missing required fields, unknown keys. Embedded
vanilla shapes (block states, particles, music, mob-spawn entries) are intentionally lenient
(`object`) — the runtime validator and the game's own codecs catch those at load.

## Do NOT add `$schema` to the JSON itself

Mojang/Isekai codecs reject unknown top-level keys, so a `"$schema"` field inside a
worldshape file fails to decode. Map the schema in your editor by file path instead.

## VS Code

Add to `.vscode/settings.json` (paths relative to the workspace root; adjust to where you
keep the schema files):

```jsonc
{
  "json.schemas": [
    {
      "fileMatch": ["**/isekai/worldshape/*.json"],
      "url": "./schema/worldshape.schema.json"
    },
    {
      "fileMatch": ["**/isekai/layered_worldshape/*.json"],
      "url": "./schema/layered_worldshape.schema.json"
    }
  ]
}
```

You get completion, hover docs, and red squiggles on bad `type` enums / missing required
fields as you author. The `layered_worldshape` schema `$ref`s `worldshape.schema.json` for
each layer's `descriptor`, so keep the two files side by side.

## Scope

These validate **structure**, not **references** — they can't know whether
`minecraft:cherry_grove` is a real biome. Reference resolution (typos in biome / structure /
feature keys) is checked at server start by `RegistryRefChecker` (WARN), and every
`isekai/` file is auto-validated on boot. Authoritative field reference:
[`../DATAPACK_REFERENCE.md`](../DATAPACK_REFERENCE.md).
