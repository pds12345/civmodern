# Vendoring owo-ui

CivModern used to require [owo-lib](https://github.com/wisp-forest/owo-lib) at runtime. It
now carries a pruned fork of owo's UI framework in `sh.okx.civmodern.common.ui`, so the mod
ships as a single jar and no longer needs owo-lib installed.

These scripts regenerate that fork. They exist so that re-syncing against a newer owo-lib
is a diff rather than an archaeology project — the vendored output *is* committed.

## Pipeline

Every step is idempotent, and every one runs offline against files Gradle has already cached.

```bash
OWO=~/.gradle/caches/modules-2/files-2.1/io.wispforest/owo-lib/<ver>/*/owo-lib-<ver>-sources.jar
TINY=~/.gradle/caches/fabric-loom/<mc>/loom.mappings.<mc>.layered+hash.<n>-v2/mappings.tiny

# 1. extract upstream sources
mkdir -p /tmp/owo && (cd /tmp/owo && unzip -oq $OWO 'io/wispforest/owo/**')

# 2. keep only what owo-vendor-manifest.txt lists
mkdir -p /tmp/keep
while read -r p; do
  [ -z "$p" ] && continue
  if [ -d "/tmp/owo/$p" ]; then mkdir -p "/tmp/keep/$p" && cp /tmp/owo/$p/*.java "/tmp/keep/$p/"
  else mkdir -p "/tmp/keep/$(dirname "$p")" && cp "/tmp/owo/$p" "/tmp/keep/$p"; fi
done < tools/owo-vendor-manifest.txt

# 3. intermediary -> Mojang  (owo publishes intermediary-named sources; we build Mojmap)
node tools/remap-intermediary.js "$TINY" /tmp/keep

# 4. relocate packages, rename owo$ members
node tools/vendor-owo.js /tmp/keep

# 5. drop the XML UI-model parsing and the factories for pruned components
node tools/strip-uiparsing.js src/main/java/sh/okx/civmodern/common/ui
node tools/strip-uiparsing.js src/main/java/sh/okx/civmodern/common/mixins
node tools/trim-factories.js  src/main/java/sh/okx/civmodern/common/ui
```

Steps 1–5 get the tree compiling to within a handful of errors. The remaining edits are
hand-made and are described under **Manual edits** below; after a version bump, re-apply them
by diffing the regenerated tree against the committed one.

## What was pruned, and why

| Dropped | Reason |
|---|---|
| `braid/**` (294 files, ~23k lines) | Reached only through three thin couplings — see Manual edits |
| `ui/parsing/**` | CivModern builds every screen in Java; no `.xml` UI models |
| `serialization/**` + the `endec` library | Only `NinePatchTexture` used it; replaced with a vanilla `Codec` |
| `config/**`, `itemgroup/**`, `network/**`, `particles/**` | Server/registry features CivModern does not use |
| `renderdoc`, `command/debug`, `ui/hud`, `ui/layers` | owo debug tooling |
| Most of `ui/component` and `ui/container` | CivModern uses label, box, button, text box, flow and grid only |

## Manual edits

Re-apply these after regenerating:

- **`Owo` → `CivModernUI`** — logger, debug flag and `id()` factory (`sh.okx.civmodern.common.ui.CivModernUI`).
- **`Color.toBraid()`** — deleted.
- **`LabelComponent.styleAt`** — owo used `braid`'s `RawLabel.Instance.StyleCollector`; an
  equivalent is inlined as `LabelComponent.StyleCollector`, backed by `ClickableStyleFinderAccessor`.
- **`NinePatchTexture`** — endec `StructEndec` replaced by `RecordCodecBuilder`; `Size.ENDEC`
  became `Size.CODEC`. The JSON shape is unchanged, so owo's own metadata files still parse.
- **`Surface`** — `blur`/`panorama`/`optionsBackground` removed (they needed the blur and
  cube-map render states plus owo's shaders).
- **`OwoUIPipelines`** — `GUI_HSV`/`GUI_BLUR` removed with them, so no shader assets are vendored.
  Remaining pipeline locations moved from the `owo` namespace to `civmodern`.
- **`OwoUIGraphics.drawSpectrum`** — removed, it was the only `GUI_HSV` user.
- **`BaseOwoScreen`** — `UIErrorToast` replaced with a log line.
- **`VanillaWidgetComponent`** — `SliderComponent`/`TextAreaComponent` branches removed.
- **`ScreenMixin`** — `BaseOwoContainerScreen` check removed.
- **`GuiRendererMixin`** — blur injections removed; kept `fixNonQuadIndexing`, with its
  namespace guard changed from `"owo"` to `CivModernUI.NAMESPACE`.
- **`UISounds`** — removed entirely; nothing in the pruned set played a UI sound.

## Things that are load-bearing

- **`GuiRendererMixin#fixNonQuadIndexing`.** Vanilla's GUI renderer assumes quad indexing.
  Without this, every `TRIANGLE_FAN`/`TRIANGLE_STRIP` pipeline — the radar's circle, ring and
  cardinal lines — draws wrong geometry, silently. It only fixes pipelines whose namespace is
  `civmodern`, which is why `CivModernPipelines` and `OwoUIPipelines` both register there.
- **`owo$` → `civmodern$`.** These members are added to vanilla classes under explicit,
  non-`@Unique` names. Leaving them as `owo$` means a duplicate-member crash for any player
  who also has the real owo-lib installed.
- **Interface injection.** `UIComponentStub` (onto `AbstractWidget`), `GreedyInputUIComponent`
  (onto `EditBox`) and `MatrixStackTransformer` (onto `GuiGraphics`) are declared in
  `src/main/resources/fabric.mod.json` under `custom."loom:injected_interfaces"`. Loom reads
  these from the project's own source set. Without them ~41 call sites stop compiling.
- **`src/main/resources/civmodern.accesswidener`.** Ported from `owo.accesswidener`, narrowed
  to this subset.
