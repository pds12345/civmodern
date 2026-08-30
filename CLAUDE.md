# CivModern

Client-side-only Fabric mod for Minecraft 1.21.11, used on Civ-style servers (CivMC etc.).
Features: combat radar, a self-built world map + minimap (Xaero-like, stored locally),
waypoints, a node/territory overlay fed by a custom server plugin channel, PvP/utility
macros (ice-road, auto-attack, hold-key), and compacted-item stack-count colouring.

This is a fork: `origin` = pds12345/civmodern (our fork), `upstream` = okx-code/civmodern.
Recent fork work centers on the node territory overlay. `.github/README.md` is upstream's
and outdated (mentions Forge and 1.20.6 — Forge support is gone; this is Fabric-only).

The user is fluent in JS/TS/Python but not Java — explain Java- or Minecraft-specific
idioms when they matter, and don't assume familiarity with the modding ecosystem.

## Toolchain and dependencies

- Gradle with a **version catalog**: all versions live in `gradle/libs.versions.toml`,
  not `gradle.properties` (that file only has mod metadata: version, name, authors).
- `fabric-loom` 1.15-SNAPSHOT, Mojang official mappings, Java 21 (`options.release = 21`).
- MC 1.21.11, Fabric Loader 0.18.4, Fabric API 0.141.3+1.21.11.
- **owo-lib** (`io.wispforest`): UI component library used by the map screen's modals and
  widgets (`map/screen/`). `owo-sentinel` ships jar-in-jar and prompts the player to
  install owo-lib if missing. Note the mod-menu dep resolves via the Modrinth maven —
  its "version" (`JWQVh32x`) is a Modrinth version ID, not semver.
- **sqlite-jdbc** and **zstd-jni** (with one native classifier per platform) are plain
  Java libraries shipped jar-in-jar via Loom's `include` — required or the mod crashes
  outside dev. Zstd is consumed through commons-compress's `ZstdCompressorInput/OutputStream`.
- `./gradlew build` also runs `copyJar`, which drops the remapped jar into `dist/`
  (a committed jar lives there; `cleanJar` clears old ones first).

## Architecture

Two top-level packages under `sh.okx.civmodern`, a leftover of the old multi-loader layout:

- `fabric/` — the loader shim. `FabricCivModernBootstrap` is the `client` entrypoint: it
  registers the `civnodes:v1` payload type and bridges fabric-api events onto the mod's
  internal event bus. `FabricCivModernMod` just wires keybinding registration.
- `common/` — everything else, loader-agnostic. `AbstractCivModernMod` is the central
  singleton (`getInstance()`): owns config, keybindings, the event bus, and all subsystems.

**Event bus**: internal events use **Guava's `EventBus`** (`@Subscribe` methods, register
objects on `mod.eventBus`). Fabric callbacks are converted to event records in `common/events/`
(ClientTickEvent, ChunkLoadEvent, JoinEvent, WorldRenderLastEvent, …) by the bootstrap. New
listeners subscribe to these rather than registering fabric callbacks directly.

**Config**: hand-rolled `Properties` file at `config/civmodern.properties` (defaults seeded
from `src/main/resources/civmodern.properties`), wrapped by `CivMapConfig`. No YACL/Cloth —
config UI is custom screens in `common/gui/screen/` (opened with `R`, plus a Mod Menu
entrypoint under `fabric/integrations/modmenu/`).

**Map** (`common/map/`, the largest subsystem):
- Per-server storage: SQLite DB at `<gamedir>/civmap/<type>/<server>/<dimension>/<seed>/map.sqlite`
  (`MapFolder` — tables for regions, waypoints, nodes, block/biome id lookups). Region
  pixel data is zstd-compressed blobs.
- `MapCache` + `data/RegionLoader`/`RegionRenderer` build region textures from loaded chunks;
  `rendering/` holds the GPU side (`CivModernPipelines` custom render pipelines registered at
  init, `BlitRenderer` registered as a fabric SpecialGuiElement — this is the modern 1.21.x
  RenderPipeline API, not legacy GL calls).
- `MapScreen` extends vanilla `Screen` but embeds owo-ui components for modals/context menus.
- `Minimap`, `waypoints/` (incl. chat-parsed waypoints via `parser/`), and `converters/` for
  one-time JourneyMap/VoxelMap imports.

**Nodes** (`common/map/nodes/`): territory overlay backed by the `civnodes:v1` custom plugin
channel. `NodeProtocol` is the wire format (deliberately free of Minecraft types; tolerant
decoding for forward compat), `NodeApiClient` is the session state machine (handshake,
query pacing, per-connection limits — see its javadoc), `NodeCache` persists into the same
sqlite. Debug: client command `/civmodern_nodedump` mirrors the server's `/nodeapidump`.

**Mixins**: all client-side, listed in `src/main/resources/civmodern.common.mixins.json`.
Mostly accessors and hooks where no fabric event exists (chat, inventory, item stacks,
render setup). Prefer the event bus / fabric-api events when one covers the need.

**Keybindings** (all in the `civmodern` category, rebindable): `R` config, `M` map,
keypad-`/` minimap zoom cycle, `Backspace` ice-road macro, `0` auto-attack, `-`/`=`
hold left/right click. A saved binding in `options.txt` overrides the code default.

**Lang**: `assets/civmodern/lang/en_us.json` is run through Gradle `processResources`
expansion (`${mod_name}` etc.) — a literal `${` in a lang string breaks the build.

## 1.21.11 gotchas

- Mojang renamed `ResourceLocation` → `net.minecraft.resources.Identifier`, and
  `ResourceKey#location()` → `#identifier()`.
- Keybinding categories are `KeyMapping.Category.register(Identifier)`; the lang key is
  `key.category.<namespace>.<path>` — singular "category", not the old `key.categories.`.
- To verify any mapped name, javap the named jar under
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/…`, or check
  the fabric-api javadoc at `https://maven.fabricmc.net/docs/fabric-api-<version>/`.

## Build, test, install

```bash
./gradlew build          # jar lands in build/libs/ and is copied to dist/
./gradlew runClient      # dev client; game dir is ./run/ (own saves/config, offline account)
```

Dev client test loop: create a singleplayer world (creative + cheats), set up the scenario
with commands, then exercise the mod. Quit normally (not force-kill) so shutdown hooks run.

The dev client cannot join real online-mode servers. For real-server testing, instruct
the user to copy the built jar into their `.minecraft` or Modrinth directory. The
user's own game may be running — never kill Java processes broadly; dev-client processes are
identifiable by `<project-dir>/run` in argv.

Commit at working milestones. Commit messages: headline only — no body, no co-authorship
trailers, no Claude session links. First Gradle run after a cache wipe downloads for
minutes — that's normal.
