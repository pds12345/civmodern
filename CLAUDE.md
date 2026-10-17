# CivModern

Client-side-only Fabric mod for Minecraft 1.21.11, used on Civ-style servers (CivNodes etc.).
Features: combat radar, a self-built world map + minimap (Xaero-like, stored locally),
waypoints, a node/territory overlay fed by a custom server plugin channel, a visualiser and
manager for the player's own JukeAlert snitches, PvP/utility macros (ice-road, auto-attack,
hold-key), and compacted-item stack-count colouring.

This is a fork: `origin` = pds12345/civmodern (our fork), `upstream` = okx-code/civmodern.
Recent fork work centers on the node territory overlay and the snitch visualiser.
`.github/README.md` is upstream's and outdated (mentions Forge and 1.20.6 — Forge support is
gone; this is Fabric-only).

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
  (`MapFolder` — tables for regions, waypoints, nodes, snitches + `snitch_hidden`, block/biome
  id lookups; schema changes are try-`ALTER TABLE` migrations in its constructor). Region
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

**Snitches** (`common/map/snitches/`): the player's own JukeAlert snitches, read from the
server's `/jalist` GUI (title "Your snitches", 45 per page, arrows in slots 45/53).
- `/jalist` is the source of truth and is only trusted whole: `SnitchListCapture` replaces the
  stored set (one transaction) only after a *complete* pass — a page with no previous arrow and
  one with no next arrow in the same opening. Partial passes are discarded.
- Pages are read from server packets (`ClientPacketListenerMixin`), not by polling the screen,
  and the arrows are judged only once no click is awaiting the server
  (`MultiPlayerGameModeMixin`): clicking the next arrow shows it lifted onto the cursor at
  once, so until the server answers every page looks like the last.
- Active → dormant → culled is derived from `dormant_at`/`cull_at` whenever asked, never stored
  or ticked. Dormancy periods per type are in `SnitchType` (confirmed by CivNodes admins: note
  block 4 weeks active + 4 dormant, jukebox 6 + 6). Culled snitches are listed but not drawn.
- `Snitches` holds cached read-only snapshots and a 512-block cell index of visible snitches,
  rebuilt only on change; the map and minimap query the cells in view. Hidden flags live in
  `snitch_hidden`, apart from the table a refresh rewrites, so they survive it.
- `JalistAutoPager` is "Refresh all" in `SnitchManagerScreen`: sends `/jalist` and clicks
  next every `CLICK_INTERVAL_MS` (250) once each page settles.
- References for the GUI format and lifecycle: local copies of JukeAlert
  (`~/projects/JukeAlert`, see `DormantCullingAppender`) and SnitchMod (`~/projects/SnitchMod`).

**Debug commands** (client-side): `/civmodern_containerdump` records the next container GUI,
page by page, to `<gamedir>/civmodern/container-dump.txt` — how a server GUI's exact items and
lore are learned before writing a parser. `/civmodern_snitches` counts stored snitches;
`/civmodern_snitches clear` forgets them.

**Mixins**: all client-side, listed in `src/main/resources/civmodern.common.mixins.json`.
Mostly accessors and hooks where no fabric event exists (chat, inventory, item stacks,
render setup). Prefer the event bus / fabric-api events when one covers the need.

**Keybindings** (all in the `civmodern` category, rebindable): `R` config, `M` map,
`Z` minimap zoom cycle, `B` new waypoint, `K` toggle waypoints, `Y` minimap node overlay,
`J` minimap snitch layer, `Backspace` ice-road macro, `0` auto-attack, `-`/`=` hold
left/right click. A saved binding in `options.txt` overrides the code default.

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

**Testmod** (`src/testmod/`): a second, dev-only mod (`civmodern-testmod`) that `runClient`
loads beside CivModern; the release jar is built from `main` alone, so it never ships (`build`
still compiles it). It registers a fake JukeAlert `/jalist` on the singleplayer server,
mirroring the real GUI layout, for testing the snitch capture and auto-pager.
`/civmodern_fakejalist` alone prints usage: `a`/`b`/`c`/`huge` pick a data set (100 / 60 /
450 / 20,000 snitches, incl. ones seconds from dormancy and culling; `huge` is for profiling)
and restart its countdowns, `long` adds snitches with long names and/or groups, and
`content`/`slots` choose whether pages arrive as one packet or slot by slot. Dev-only helpers
go here rather than behind runtime checks in `main`.

The dev client cannot join real online-mode servers. For real-server testing, instruct
the user to copy the built jar into their `.minecraft` or Modrinth directory. The
user's own game may be running — never kill Java processes broadly; dev-client processes are
identifiable by `<project-dir>/run` in argv.

Commit at working milestones. Commit messages: headline only — no body, no co-authorship
trailers, no Claude session links. First Gradle run after a cache wipe downloads for
minutes — that's normal.
