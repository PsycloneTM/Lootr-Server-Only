# Development notes

Internals, mixin list, verification status and per-feature implementation notes. Most of the "NOT build-verified" labels below predate later successful builds; treat them as "check in play", not "known broken".

## Status notes

A reimplementation of Lootr's "unique loot per player" mechanic for Minecraft
1.21.1 that needs **no mod on the client**: vanilla clients can join. It runs
on **NeoForge** (21.1.219) and **Fabric** (Loader 0.16.5, Loom 1.14.3) with the
same feature set on both.

Ported objects: **chest, trapped chest, barrel, shulker box, chest minecart,
decorated pot, suspicious sand/gravel (brushable block) and item frame**, plus
first-use advancements, a config layer, loot-table filters, `/lootr` admin
commands and protection so one player can't destroy loot for everyone.

Status: both loaders have compiled and booted a dedicated server (confirmed from
real runs) and a light playtest passed, but which features it covered was not
recorded. Everything added since then (see [Verification status](#verification-status))
is written and checked against vanilla sources only.
Nothing was ever compiled in the authoring sandbox (no JDK
or network there), so every change has been checked by reading vanilla sources,
not by a compiler, until you build it.

### Design rule: no registry entries

**Never register a new `Block`, `Item`, `BlockEntityType` or any other registry
entry.** A vanilla client can't render or even connect with unknown registry
content (upstream Lootr's custom block entities each need a client-side
`BlockEntityRenderer`). So instead of new blocks:

- per-player loot state is **attached to vanilla's own block entities and
  entities** (`minecraft:chest`, `minecraft:barrel`, a minecart, an item
  frame...) using NeoForge's Data Attachment API or Fabric's
  `fabric-data-attachment-api-v1`;
- a server-side listener cancels the vanilla interaction and opens a vanilla
  menu backed by a plain `Container` scoped to the player who opened it
  (`PlayerScopedContainer`). Vanilla's `ChestMenu`/`ShulkerBoxMenu` never know
  the difference.

Registering into an already-frozen registry is not just against the rule, it
crashes: a first attempt at custom advancement triggers died at startup on both
loaders with `Registry is already frozen`.

## Project layout

```
build.gradle, settings.gradle, gradle.properties, .github/workflows/build.yml, release.yml
README.md, LOOTR_SERVER_ONLY_DESIGN.md, docs/*.md

neoforge/
  build.gradle
  src/main/java/net/lootr/serveronly/
    LootrServerOnly.java              entry point; registers config + attachment
    advancement/OpenedAdvancements    awards the first-use advancements
    api/                              add-on API: LootrAPI facade, LootFilter(s), LootListener(s) (see "Addon API" below)
    command/LootrCommands             /lootr info|reset|refresh|decay|id|clear|cclear|openers|frame (+ reload on Fabric)
    command/AdminCommands             /lootr <type> [table], force_chunk|force_radius|force_all, open_as|open_as_uuid
    config/                           LootrConfig (ModConfigSpec), TeamResolver + TeamResolvers + ITeamResolver (pluggable),
                                      ProblematicLootTables + IProblematicLootTableProcessor,
                                      StructureTags (refresh/decay structure tags), MessageStyles
    data/                             LootrLootState (+ NeoForge attachment serializer), PlayerScopedContainer,
                                      PlayerClears (lazy /lootr clear), ReadOnlyLootView (/lootr open_as), DecayTracker, RefreshTracker,
                                      LootStateStripper (structure saving)
    interaction/                      ContainerInteractionHandler (4 block containers), OpenTracker (lids/sounds),
                                      MinecartInteractionHandler, PotInteractionHandler, BrushableLoot,
                                      ItemFrameInteractionHandler, ItemFrameVisualSync,
                                      ContainerProtection (break/blast/lock-adjacent protection, cart unpack guard),
                                      Decay, Refresh (background sweeps), ChunkDiscovery (start_*_while_ticking),
                                      LootRoller, UnresolvedTables
    mixin/                            see "Mixins" below
    registry/                         ModAttachments, ModLootTags, ItemFrameMarker
  src/main/resources/
    META-INF/neoforge.mods.toml, pack.mcmeta, lootr_serveronly.mixins.json
    data/lootr_serveronly/advancement/*.json (eight + root), loot_table/chests/ (example.json, end_city_elytra.json),
    tags/worldgen/structure/ (refresh.json, decay.json; ship empty)

fabric/                               same packages under net/lootr/serveronly/fabric/
  build.gradle                        Loom; three Fabric API modules (see below)
  src/main/resources/fabric.mod.json, lootr_serveronly.mixins.json,
    data/lootr_serveronly/ (same advancement, loot_table and tag files)
```

The loaders share no code module: the two trees are deliberate twins, with the
same class names. Differences are only where an API differs (events vs
callbacks, `ModConfigSpec` vs hand-rolled JSON, raw-`CompoundTag` attachments on
Fabric).

**Fabric API modules used:** `fabric-data-attachment-api-v1`,
`fabric-events-interaction-v0`, `fabric-command-api-v2`. Never
`fabric-registry-sync-v0`, the module that makes Fabric servers kick vanilla
clients over unknown registry entries. A real Fabric boot log shows it absent
from the "Loading N mods" list; re-check that list if you bump the Fabric API
version or add a module.

## Mixins

Used sparingly, and only where no loader event exists. The mixin config has
`defaultRequire: 1`, so a target that fails to apply **stops the server at
startup** instead of silently doing nothing. Mixin applies each one when its
target class first loads, so a target that loads late (structure classes) is
only proven once that code runs.

| Mixin | Loader | Purpose |
|---|---|---|
| `MixinRandomizableContainerBlockEntity` | both | Stop vanilla unpacking a loot table behind the mod's back |
| `MixinAbstractMinecartContainer` | both | Chest-minecart isolation: a loot cart looks empty to hoppers and other vanilla callers, so nothing unpacks its table into the shared inventory |
| `MixinEntitySelector` | both | Keeps a managed loot cart out of `EntitySelector.CONTAINER_ENTITY_SELECTOR`, so hoppers and hopper minecarts never pick it as a container at all (appends a predicate to the field at the end of `EntitySelector.<clinit>` via `@Shadow @Final @Mutable`; no lambda names, unlike upstream's hook, which targets `lambda$static$2` and fails to remap here) |
| `MixinHopperBlockEntity` | both | Backstop on `getEntityContainer`: a managed cart is treated as no container, and a `ClassCastException` caused by another mod's selector change is logged once instead of crashing the tick |
| `MixinAbstractContainerMenu` | both | `power_comparators`: fixed comparator output. Uses `require = 0`, so a wrong target name is skipped (comparators keep reading 0) rather than stopping startup |
| `MixinBrushableBlockEntity`, `AccessorBrushableBlockEntity` | both | Per-player brushing |
| `MixinDecoratedPotBlock` | both | Loot pots survive projectiles |
| `MixinStructureTemplate`, `MixinEndCityPiece`, `AccessorItemFrame` | both | Mark structure item frames |
| `MixinServerGamePacketListenerImpl` | both | `bypass_spawn_protection`: wraps the `ServerLevel.mayInteract` call in `handleUseItemOn` (the same target upstream wraps) |
| `MixinBrushableBlock` | both | `brushables_self_support`: wraps the one `FallingBlock.isFree` call in `BrushableBlock.tick` |
| `MixinItemFrame` | both | `item_frames_self_support`: `ItemFrame.survives` returns true for a marked frame |
| `MixinExplosion` | Fabric only | Fabric has no explosion event (NeoForge uses one) |
| `MixinEntity` | Fabric only | Fabric has no invulnerability callback (NeoForge uses one) |

## Verification status

**Confirmed from real runs by the author:**
- `.\gradlew clean build` succeeds for both loaders.
- A dedicated server boots to "Done" on both NeoForge and Fabric on the feature
  set that included item frames, pots, brushables, commands and loot-table
  filters (per the run logs from the previous session).
- `fabric-registry-sync-v0` is absent from the Fabric mod list.
- The custom-trigger "Registry is already frozen" crash is fixed (advancements
  use `minecraft:impossible`).
- A light in-game playtest worked; the features it covered were not recorded.

**Written and checked against vanilla sources, but not confirmed in play:**
- **Everything added in the latest passes, on both loaders, unbooted and uncompiled:** background refresh, the three
  `*_while_ticking` toggles and `ChunkDiscovery` (NeoForge `ChunkEvent.Load`, Fabric `MixinLevelChunk`); the viewing rule
  for refresh; the `blast_resistant` fix for frames and carts; `check_world_border`; `convert_elytras_to_chests`;
  `notification_delay`; `disable_message_styles`; pluggable team resolvers (`pinned_team_resolver`); `/lootr cclear`;
  `report_unresolved_tables`; **locked-container support**; **`MixinAbstractMinecartContainer`** (chest-minecart
  isolation); **chest-minecart break rules**; **chest-minecart open/close game events and piglin anger**; and **structure-template sanitization** (two new `@WrapOperation`s in
  `MixinStructureTemplate`). The implementation notes below each give a quick test. New mixin targets in these passes:
  `LevelChunk.registerTickContainerInLevel` (Fabric), six names on `AbstractMinecartContainer` (both), and
  `StructureTemplate.fillFromWorld` / `fillEntityList` (both); and, in a later pass, `ExplosionDamageCalculator.getBlockExplosionResistance`, the `EntitySelector.<clinit>` container-selector hook and `HopperBlockEntity.getEntityContainer` (both).
- **Audit pass 3, both loaders, compiles and boots to `Done` on both loaders (real runs), but its three mixin targets load lazily and are not yet exercised:** `minecraft:generate_loot` fired from `LootRoller.triggerGenerateLoot`
  (every roll, as upstream); `blast_resistant` reworked to upstream's resistance 16 (`MixinExplosionDamageCalculator`, the
  power-4 cutoff is gone); loot minecarts excluded from hopper candidacy (`MixinEntitySelector`, `MixinHopperBlockEntity`).
  The suspicious-sand-falls question was analysed and needs only a playtest.
- **Audit pass 4 (the last open commands), both loaders, never compiled or booted:** `/lootr <type> [table]` (spawn),
  `/lootr force_chunk|force_radius|force_all` and `/lootr open_as|open_as_uuid` (`AdminCommands`, `ReadOnlyLootView`; `ChunkDiscovery.scan`
  is now shared with the chunk-load hook, and `DecayTracker.add` / `RefreshTracker.add` return whether they added). No new mixins.
  Parsed with the JDK's own parser only (no Minecraft jars here), so type errors are possible. Quick test: `/lootr chest`, open it
  as two players, then `/lootr open_as <name> block ~ ~ ~` as an admin and try to take an item (nothing moves).
- **`LootListener` / `LootListeners` and `LootFilter` service discovery, both loaders, never compiled** (hooks in every roll
  path, in `Decay`, and a `ServiceLoader` lookup; no new mixins).
- **Add-on API and upstream-compatible naming, both loaders, never compiled** (the previous session type-checked it
  against stubs only): `LootFilter` / `LootFilters` (hooked into `LootRoller`, the single place every roll goes through),
  the `LootrAPI` facade, `ITeamResolver` with upstream's ids (`minecraft:vanilla_default`, priority -1000) and the
  `loot_modid_blacklist` alias. No new mixins, so these cannot stop a server from booting; they can only fail to compile. Boot both
  loaders before anything else: a wrong target stops the server at startup.
- **Problematic-table processors** (built-in set, priorities, two-pass, `ServiceLoader` discovery). Quick test:
  `/setblock ~ ~ ~ minecraft:chest{LootTable:"atum:chests/pharaoh"}` and open it. It should open as a plain vanilla
  chest (not per-player), because the table is built in as problematic. Then add `atum:chests/pharaoh` to
  `loot_table_forced_whitelist`, reload, and place a fresh one: it should now be handled by the mod (the table
  does not exist, so you should see the unresolved-table log line). `ServiceLoader` discovery itself needs a second
  mod to exercise.
- **Break, blast, spawn-protection and self-support options** (the eight keys from `enable_break` to
  `item_frames_self_support`) and **three new mixins** (`MixinServerGamePacketListenerImpl`, `MixinBrushableBlock`,
  `MixinItemFrame`). Every target and signature was checked against the 1.21.1 sources, and `MixinExtras` is already
  used by existing mixins, but a wrong mixin target stops the server at startup (`defaultRequire: 1`), so **boot
  both loaders first**. Quick tests: set `disable_break` true and try to break a loot chest as survival (refused), and
  as creative without then with sneaking (refused, then allowed); set `blast_resistant` true and detonate TNT a few blocks from a
  loot chest (survives) and then a charged creeper or an end crystal right next to it (can break it); stand inside spawn protection as
  a non-op and open a loot chest (works with `bypass_spawn_protection` on, refused with it off).
  Fake-player detection could not be exercised without another mod.
- **Loot rolling through `LootTable.fill`, and `randomise_seed`.** Quick test: open a
  fresh loot chest; items should be scattered across the slots, not packed into the
  first row. Then set `randomise_seed` false and use a structure chest (one with a
  real seed): two players, or the same player after `/lootr reset`, should see
  identical contents.
- **The two newest mixins, `MixinEntity` (Fabric) and `MixinDecoratedPotBlock`
  (both), and the pot/minecart additions to protection.** Added after the last
  real boot. Boot both loaders before anything else: a wrong target crashes the
  server at startup. Their targets were verified in the 1.21.1 sources
  (`isInvulnerableTo` is declared only in `Entity`; `onProjectileHit` once in
  `DecoratedPotBlock`).
- That structure frames are actually marked in a freshly generated structure or
  End City, and that `MixinStructureTemplate` applies in a real run (its target
  was changed from a compiler-indexed lambda to a named call after a real
  **Fabric** failure; see design doc §10). Also confirm the Fabric mixins on a
  *production* server: Loom 1.14.3 generates no refmap (annotation processor off by
  default) and remaps mixin targets inside the jar instead, which `runServer` (dev
  names) does not exercise. Drop the jar from `fabric/build/libs` (the remapped one,
  not `-dev`) onto a real Fabric server and check the log for mixin errors. If one
  appears, set `mixin { useLegacyMixinAp = true }` in `fabric/build.gradle` and restore
  the `"refmap"` entry in `lootr_serveronly.mixins.json`.
- Client-side prediction: a vanilla client predicts pot breaks and frame hits
  locally, and whether a cancelled server action is reverted cleanly needs a
  playtest. Sherd retention on a pot after a cancelled break is unchecked.
- Fabric `AttackEntityCallback` must return `SUCCESS`, not `FAIL` (`FAIL` stops
  the packet reaching the server so the take never runs). That is intentional
  and differs from the pot handler's `AttackBlockCallback`; don't "align" them.
- **Break rules** (`break_to_drop_loot`, `require_sneak_to_break`,
  `should_drop_player_loot`), added after the last real boot on both loaders.
  No new mixin or registry entry is involved, so a boot failure is unlikely, but
  the behavior is untested. Quick test: set `break_to_drop_loot` true, hold-break
  a loot chest in survival without sneaking (you should receive the loot and the
  chest stays); then set `protect_containers` false and `require_sneak_to_break`
  true and check that a plain break is refused and a sneaking break destroys it.
- `/lootr` commands, team loot, dimension lists, refresh timer and per-player
  brushing in actual play. `/lootr frame mark|unmark` and `/lootr reload` were
  added after the last real boot, so they are also unbooted.

## Lessons recorded

- Don't write to registries (frozen registry crash above).
- Don't name a compiler-indexed lambda in a mixin (`lambda$...$5`). Its index
  depends on decompile/recompile, and NeoForge's patched `StructureTemplate`
  renames the surrounding method too. Wrap a named method or call instead.
- Don't target a vanilla lambda by name even when upstream does. `MixinEntitySelector` originally copied upstream's `lambda$static$2` / `method_5914` and the Fabric remap step failed with "Cannot remap lambda$static$2 because it does not exists in any of the targets". It now appends to `CONTAINER_ENTITY_SELECTOR` in `<clinit>` instead. **Rewritten after the last boot: not yet built or booted.**
- A mixin cannot name an inherited interface default (for example `ContainerEntity.isEmpty`); neutralise what it
  calls instead (`MixinAbstractMinecartContainer` cancels the unpack's `setLootTable(null)` and `setItem`).
  Anything that legitimately needs the cancelled call (decay) must go through an explicit bypass.
- `sh` does not expand `{a,b}` braces; verification scripts for this repo need `bash`.
- Entities have no `setChanged()`; block entities do. On Fabric an attachment is
  a raw `CompoundTag`, so handlers load, mutate and save `LootrLootState` by
  hand with `level.registryAccess()`.

See `LOOTR_SERVER_ONLY_DESIGN.md` for the design rationale, per-object plan and
build order.

## Implementation notes

### Menu validity fix (team loot closed the chest instantly)

`PlayerScopedContainer.stillValid` compared the **loot key** to the player's own
UUID. The loot key is the team's id when team loot is on, so for a team member
it was always false and vanilla closed the menu the instant it opened. With team
loot off it was always true, so nothing ever checked range or that the chest
still existed. The container now takes a validity check from its caller:
vanilla's own `Container.stillValidBlockEntity` for chests, barrels and shulker
boxes, and a still-present-and-within-8-blocks check for chest minecarts. Applied
on both loaders.

Verified: `Container.stillValidBlockEntity(BlockEntity, Player)` exists in 1.21
(mappings.dev), every `PlayerScopedContainer` construction passes the new
argument (checked), and both copies of the class compile against stubs.
**Not verified:** a real run with team loot on. Test it: put two players on one
scoreboard team with `team_loot` enabled, open a loot chest, and confirm the menu
stays open; then walk away from an open chest and confirm it closes.

### Open fidelity (lids, sounds, vanilla refusals) - NOT build-verified

Loot chests used to open a menu with no lid animation, no sound, no open stat
and no piglin anger, and ignored "chest blocked" / sneak-placing rules.
`ContainerInteractionHandler` (both loaders) now:

- skips the interaction when the player is sneaking with an item (vanilla would
  place the block), denies spectators (they would resolve the loot table for
  everyone), and lets vanilla refuse a blocked chest or an obstructed shulker box;
- awards the open stat and angers nearby piglins, as vanilla's blocks do;
- reports opens to `OpenTracker`, which keeps its own per-block opener set and
  sends vanilla's block event (`blockEvent(pos, block, 1, count)`) plus the
  open/close sounds, barrel `open` state and game events. It does **not** call
  the real `startOpen`: a chest's opener counter re-counts every 5 ticks by
  checking that the menu is backed by the real block entity, so the lid would
  snap shut and the count would go negative. Logouts are handled (`forget`).
- `MixinChestBlockEntity` makes `ChestBlockEntity.getOpenCount` include those
  loot openers, so **trapped chests emit redstone** while open. (An earlier
  design-doc line said this worked for free; it did not.)

Test: open a loot chest/barrel/shulker (lid opens, sound plays, closes when you
leave), put a block on a chest (should refuse), sneak + hold a block and click
(should place it), open a loot trapped chest next to a redstone lamp, open near a
piglin. The new mixin targets `ChestBlockEntity.getOpenCount` by name: a wrong
name crashes at startup, so boot both loaders first.

**Still not ported:** nothing from upstream's container set; see the deliberate omissions above.

### Decay - NOT build-verified

A looted container now disappears after a configurable time, for everyone
(upstream's decay, minus the client-side particles). Both loaders.

- **Config** (NeoForge `decay` section; Fabric `lootr_serveronly.json` keys of the same
  names): `decay_value` (ticks, 0 = off), `decay_all` (default true), `decay_loot_tables`,
  `decay_modids`. A container decays if its table is converted by this mod (not
  blacklisted) AND covered by `decay_all` or one of the two lists.
- **Timer:** the same per-container first-looted time that refresh uses; due at
  `first looted + decay_value`. Refresh restarts it, so a short `refresh_value` keeps
  a container that is being opened alive; if refresh is not shorter, decay wins.
- **What decays:** chests, trapped chests, barrels, shulker boxes, chest minecarts.
  Pots, suspicious blocks and item frames never do.
- **How:** a once-a-second sweep. Blocks are found through `DecayTracker`, a saved
  set of positions added when a container is looted or opened. Unloaded chunks are
  skipped, never loaded; an expired container goes within a second of its chunk
  loading. Minecarts are found by listing loaded ones. NeoForge uses
  `ServerTickEvent.Post`; Fabric uses `MixinMinecraftServer` (to avoid the
  `fabric-lifecycle-events-v1` module).
- **Safety:** a container someone has open is never removed (it goes after they close
  it); the loot table is cleared before removal so vanilla spills nothing; opening a
  container already past its deadline makes it decay instead; `/lootr info` shows the
  time left; first opens print the time left in chat.

Test: set `decay_value` to 600 (30 s), loot a chest, wait. Also: stay in the menu past
30 s (should stay until you close it), restart the server mid-timer (should still
decay), loot a minecart, check `/lootr info`. Boot first: `MixinMinecraftServer` targets
`tickServer` by name, so a wrong name crashes startup.

### `/lootr clear`, `/lootr openers`, refresh filters, `disable` - NOT build-verified

Added after decay. Both loaders. None of it has been built or run; the new data, config and
decay classes were only type-checked against hand-written stubs of the Minecraft/loader APIs,
which cannot catch a wrong real API name.

- **`/lootr clear <players>` is lazy.** Containers cannot be listed from outside, so it does
  not walk them. Each loot key (a player's UUID, or their team's) has a *generation* number
  stored in `PlayerClears` (`data/lootr_serveronly_clears.dat`, overworld folder). Every
  per-player entry in a container remembers the generation it was written under (new `Gen`
  tag; missing = 0, so old worlds load unchanged) and counts as "not looted" once its key's
  generation has moved past it. Old entries are overwritten the next time that player loots
  there, so they linger on disk until then. `/lootr info` does not count cleared entries.
  Limits: a player with a loot menu open during the clear keeps that session's items, and
  because both the player's own key and their team key are cleared, teammates are affected
  when `team_loot` is on. Because `LootrLootState` has no server in hand, the loaded
  `PlayerClears` is cached in a static; the once-a-second tick refreshes it
  (`Decay.onServerTick`), so it is in place before anyone can interact.
- **`/lootr openers`** reads players' open menus (via the `PlayerScopedContainer` owner tag),
  so it works for minecarts too and cannot go stale.
- **Refresh filters** mirror decay's: `refresh_all` / `refresh_loot_tables` /
  `refresh_modids` / `refresh_dimensions`. All six refresh call sites (container, take-loot,
  minecart, pot, brushable) go through `LootrConfig.refreshTicksFor(dimension, table)`.
  Defaults reproduce the old behavior. `/lootr info` shows the filtered interval (suspicious
  blocks fall back to plain `refresh_value`).
- **`decay_dimensions`**, **`disable_notifications`**, **`disable`**: see the config table.
  `disable` makes `isDimensionEnabled` return false everywhere, so containers revert to vanilla.
  Containers already looted still hold an unrolled table, so the next opener gets its loot.

**Test:** `/lootr clear @s` then reopen a chest you had looted (fresh loot), a pot, and a
loot frame; confirm another player's loot is untouched. Restart and repeat (the generation
must persist). `/lootr openers` while two players are in one chest. Set `refresh_all` false
with a one-table list and confirm only that table refreshes. `disable true` then reload.
**Boot first**: `PlayerClears` uses the 2-argument `SavedData.Factory` on NeoForge and the
3-argument form on Fabric, copied from `DecayTracker`.

Also added: `replace_when_decayed`, `perform_decay_while_ticking`, `tick_delay` (the decay sweep
interval; `PlayerClears` is refreshed every tick regardless) and `loot_table_forced_whitelist`.
**Background refresh** was first left out, then added; see "Background refresh" below. (`power_comparators` was later implemented; see the Mixins table.)

**Structure tags (refresh and decay).** Besides `*_all`, the table list and the modid list, a container also
refreshes / decays when it sits inside a structure tagged `lootr_serveronly:refresh` / `lootr_serveronly:decay`
(`data/lootr_serveronly/tags/worldgen/structure/refresh.json` and `decay.json`, same idea as upstream's
`lootr:refresh` / `lootr:decay`). Both ship **empty**; override them with a datapack, e.g.
`{"values": ["minecraft:desert_pyramid", "#minecraft:village"]}`. "Inside" is the structure's bounding box grown by 8
blocks, as upstream, plus upstream's desert-pyramid pit box and (with `perform_piecewise_check`) each structure piece.
`refresh_dimensions` / `decay_dimensions` are one more way in, as upstream (no longer a restriction). Not verified by a build.

**Still not ported** (key names and meanings verified against upstream's `ConfigManager`, see "Upstream parity" below):
`trapped_custom` (not applicable: no custom inventory block exists). `blast_resistant` now uses upstream's resistance of 16. **Not applicable here:** `save_mode` (upstream's separate per-container data files; state is saved with
the block entity instead), `max_age` (defined upstream but read nowhere, only a config entry and a lang string), the
client visuals (`decay_particles`, `refresh_particles`, `unopened_particles`, `display_toasts`, `new_textures`,
`vanilla_textures`), and the structure-chest conversion queue (`convert_mineshafts`, `perform_piecewise_check`,
`skip_logging_no_loot_table_at_generation`).

### Upstream parity: what is verified and what is not

Defaults follow upstream and are now **verified against upstream's current `ConfigManager` source** (mdg-1.21.1
branch), not only its 2023 wiki: `decay_value` 6000 (5*60*20), `decay_all` false, `refresh_value` 24000 (20*60*20),
`refresh_all` false, `power_comparators` true, `tick_delay` 20, `replace_when_decayed` false,
`perform_decay_while_ticking` true, `randomise_seed` true, `disable_notifications` false. One deliberate difference:
`tick_delay` has a minimum of 1 here (upstream allows 0), which keeps the sweep's `% tick_delay` from ever dividing
by zero.
The newer key names (`modid_dimension_*`, `loot_table_forced_whitelist`, `blast_immune`, `enable_break`, ...) that an
earlier audit pasted into the conversation supplied have since been checked and exist in upstream's source. That
audit also listed several keys as missing that this mod already has (`tick_delay`, `perform_decay_while_ticking`,
`replace_when_decayed`, `loot_table_forced_whitelist`, `power_comparators`, `decay_dimensions`) and claimed
`refresh_all` / `decay_all` default to true here; both are false, matching upstream.

- **Breaking change:** `refresh_ticks` was renamed `refresh_value`. An existing config's old value is not
  migrated (NeoForge TOML and Fabric JSON both ignore the unknown old key), and the new defaults mean nothing
  refreshes or decays until `*_all` or a list is set. Re-enter your values after upgrading.
- **Name alias (done):** upstream's key is `loot_modid_blacklist`. Both it and the older `mod_id_blacklist` are now
  accepted and merged, so neither an old config nor an upstream one loses its value.
- **Corrections to the earlier audit:** upstream's `max_age` has no effect in the 1.21.1 source (read nowhere), so
  there is nothing to port; the older wiki's `maximum_age` was about the *conversion queue*, not an expiry for stored loot; `skip_unloaded`,
  `convert_wooden_chests`, `convert_trapped_chests`, `additional_chests`, `convert_mineshafts` and the queue
  settings are about upstream converting structure chests into its own blocks, which this mod never does
  (vanilla containers are used directly).
- **Not exactly equivalent:** `protect_containers` (this mod's default, stricter than upstream's) still exists alongside the upstream
  break keys; see [Break rules](FEATURES.md#break-rules).

### Decay review (failure guard)

Reviewing the decay implementation found no logic errors, but one robustness gap: nothing caught an exception in
the sweep, and an exception escaping a tick event stops the whole server. A single corrupt container could
therefore crash-loop it every sweep. `Decay.onServerTick` (both loaders) now catches `RuntimeException`, and each
container in `sweepBlocks` / `sweepCarts` is guarded on its own so one bad entry cannot stop the others decaying.
The failure is logged once per streak, not once per sweep, and re-arms after a clean sweep. NeoForge gained a
`LootrServerOnly.LOGGER` (SLF4J, as the Fabric side already used) for this.

Checked and fine: `tick_delay` cannot be 0 (minimum 1 on NeoForge, clamped on Fabric), so `% tick_delay` is safe;
a decayed container's loot table is cleared before removal so vanilla drops nothing; a container someone has open
is never removed; unloaded chunks are skipped rather than loaded. **Not verified by a build** (the same caveat as
everything in this zip): the guard was parse-checked only.

### Background refresh and the start toggles - NOT build-verified

Refresh used to happen only when a player opened a container. It now also runs in the background, with the same
scheduler design as decay. Both loaders.

- **Config:** `perform_refresh_while_ticking` (default `true`), `start_refresh_while_ticking` (default `true`),
  `start_decay_while_ticking` (default `false`); `perform_decay_while_ticking` and `tick_delay` already existed.
  Defaults match upstream. `tick_delay` now paces both sweeps.
- **How:** `Refresh` runs from the same tick entry as `Decay` (after it). Block containers are found through
  `RefreshTracker`, a saved set of positions, filled when a container is looted or opened. Unloaded chunks are
  skipped, never loaded. Chest minecarts are found by listing the loaded ones. A refreshed container is dropped from
  the tracker; the next loot starts and tracks a new timer. Decay and refresh still share one timer (see Decay).
- **Never while the menu is open (new, also for open-time refresh):** a due container that a player has open is left
  alone and refreshed after they close it, instead of being reset under them. This applies to the background sweep and
  to another player opening the same container, for chests, barrels, shulker boxes and chest minecarts.
- **`start_*_while_ticking`:** when a chunk loads, containers in it that already have a first-looted time but are not in
  the tracker are added (`ChunkDiscovery`; NeoForge `ChunkEvent.Load`, Fabric `MixinLevelChunk` on
  `LevelChunk.registerTickContainerInLevel`, because the Fabric chunk event is in a module this project avoids). It
  looks only at the chunk that loaded and does nothing unless the toggle and its feature are on. This is what picks up
  containers looted before decay or refresh was enabled; without it they are picked up when next opened.
- **Not swept:** pots and suspicious blocks (their only state is an unobservable "already looted" marker, so refreshing
  on next touch is equivalent) and item frames.
- **`blast_resistant` fix:** item frames and chest minecarts were not covered by it when `protect_containers` and
  `blast_immune` were both off. They now survive explosions from TNT and an uncharged creeper (the entity version of
  "power 4 or less"; a damage source does not carry the power, so anything else counts as a big blast).

Test: set `refresh_value` 600 with `refresh_all` true, loot a chest, wait 30 s (no need to open it: another account then
sees fresh loot; `/lootr info` shows the timer cleared). Keep the menu open past 30 s: it must stay filled until you
close it, then refresh. Restart mid-timer: it still refreshes. Loot a chest with refresh off, enable refresh, restart:
with `start_refresh_while_ticking` true the chest refreshes after its chunk loads. Boot first: `MixinLevelChunk` targets
`registerTickContainerInLevel` by name, so a wrong name crashes startup.

### Problematic and unresolved loot tables - NOT build-verified

- **`ProblematicLootTables`** (package `config`) is consulted by `LootrConfig.isLootTableEnabled`, after the forced
  whitelist, so a problematic table is left vanilla unless it is also force-whitelisted (the namespace blacklist
  `mod_id_blacklist` still applies, as upstream). It follows upstream's design:
  - **Built in, as upstream:** `twilightforest:structures/stronghold_boss` and `atum:chests/pharaoh`, which upstream
    blocks to fix its issues #79 and #74. This is a behavior change from earlier builds, which shipped the set
    empty. If you do want one converted, add it to `loot_table_forced_whitelist`.
  - **Processors** implement `IProblematicLootTableProcessor` (same three methods and defaults as upstream's:
    `gatherProblematicChests()`, `processProblematicChests(Set)`, `priority()`). They are found with
    `ServiceLoader` (a file `META-INF/services/net.lootr.serveronly.config.IProblematicLootTableProcessor`
    on NeoForge, `net.lootr.serveronly.fabric.config.IProblematicLootTableProcessor` on Fabric, naming the
    implementing class), or registered in code with `ProblematicLootTables.registerProcessor(...)`. The older
    `ProblematicLootTables.register(() -> List.of(...))` still works as a simple priority-0 adapter.
  - **Two passes, in priority order (highest first):** pass 1 unions every processor's gathered tables; pass 2
    hands that set through each processor's `processProblematicChests`, each result feeding the next, so a
    processor can add, remove or replace entries. The built-in set is a processor at priority -1000, as upstream.
  - **Your `problematic_loot_tables` list** is added after pass 2, so no processor can remove an entry you set.
  - An exception in any processor call is logged and that call is skipped; it can never break loot conversion.
  - `invalidate()` rebuilds the set (a config reload does this automatically); service discovery happens once.
  - **Not binary-compatible with upstream's interface.** It is a different package, and upstream's classes are not
    on a server-only server, so an integration written for upstream must be recompiled against this one (a
    one-line import change). I did not ship a copy of upstream's interface under its own package name: it would
    clash if upstream Lootr were ever on the same classpath. If you want that compatibility shim, say so.
- **`UnresolvedTables.check`** runs right after each of the four loot lookups (containers, minecarts, pots,
  suspicious blocks). Vanilla returns the shared `LootTable.EMPTY` for a missing table, so identity with it
  means "unresolved"; a table that exists but is deliberately empty is a different object and is not reported.
  Logged once per table per server run; with `report_unresolved_tables` the player who triggered the roll is also
  told in chat every time. It does not change what happens (the container is still empty), it only makes the
  cause visible.
- The type-check of these classes against hand-written API stubs passes on both loaders; that cannot catch a
  wrong real API name, so run a Gradle build and open a chest whose table you have deleted.

### Team resolvers - NOT build-verified

With `team_loot` on, a resolver maps each player to the UUID their loot is keyed under (players who should share
loot get the same one). This follows upstream's design, the same way the problematic-table processors do:

- **`ITeamResolver`** (package `config`) has upstream's methods and defaults: `resolveServerPlayer(Player)`,
  `resolveClientPlayer(Player)` (defaults to the server method and is never called here, there being no client),
  `resolverId()`, `init()` and `priority()`. A resolver must never return null.
- **Sources**, all treated alike: the built-in scoreboard resolver; resolvers found with `ServiceLoader` (a file
  `META-INF/services/net.lootr.serveronly.config.ITeamResolver` on NeoForge,
  `net.lootr.serveronly.fabric.config.ITeamResolver` on Fabric, naming the implementing class); resolvers added in
  code with `TeamResolvers.registerResolver(...)`; and the older `TeamResolvers.register(Resolver)` through an adapter.
- **Selection**, as upstream: the resolver whose id is `pinned_team_resolver`; otherwise the highest `priority()`
  (ties keep discovery order). A blank pin, or `lootr:default` (upstream's blank value), is silent; any other unknown id
  is logged once and ignored.
- **The built-in** has id `minecraft:vanilla_default` (upstream's id, so a pinned value copies across) and priority
  **-1000**, as upstream. The older id `lootr_serveronly:scoreboard` is still accepted in `pinned_team_resolver` as an
  alias. The UUID it derives (scoreboard team name, prefixed `lootr_serveronly:team:`) is unchanged, so existing team
  loot in a world keeps working.
- **Behavior change:** the built-in used to be priority 0, so an add-on at the default priority only *tied* with it
  and lost (the built-in registered first). Now any add-on at the default priority wins automatically, as upstream.
- **Safety:** a resolver whose `init()` throws, or whose `resolverId()` throws or returns null, is ignored. One whose
  `resolveServerPlayer` throws or returns null falls back to the player's own UUID for that call. Each is logged once.
  Two resolvers with the same id: the higher-priority one wins and the other is logged and ignored (upstream lets the
  later one silently replace the earlier).
- **Not binary-compatible with upstream's interface** (different package; upstream's classes are not on a server-only
  server), so an add-on written for upstream needs a one-line import change and a recompile.
- **Quick test:** `team_loot` true, two players on the same scoreboard team: they share one loot pool (unchanged).
  Pin `minecraft:vanilla_default`, then `lootr_serveronly:scoreboard`: same result both times. Pin `foo:bar`: one error
  line in the log, same result. `ServiceLoader` discovery needs a second mod that provides a resolver.

### Locked containers and chest-minecart isolation - NOT build-verified

Two gaps found in the upstream-parity audit, both loaders.

- **Locked containers.** A chest, barrel or shulker box with a vanilla lock (`/data merge block ... {lock:...}` or a lock
  component) used to open for anyone, because this mod's open handler replaced vanilla's menu path, which is where the lock
  is checked. The handler now calls the block entity's own `canOpen(player)` after its other vanilla-parity checks (sneak-place,
  spectator, blocked chest, obstructed shulker) and, if it refuses, cancels the interaction. Vanilla's own message
  ("... is locked") and lock sound are shown; no open, stat or piglin anger happens. Test: lock a loot chest, then right-click it
  with and without the named key item in your main hand.
- **Chest-minecart isolation.** `MixinAbstractMinecartContainer` is the entity twin of `MixinRandomizableContainerBlockEntity`.
  Before it, a hopper next to a loot chest minecart reached vanilla's `ContainerEntity` methods, which roll the loot table into the
  cart's one shared inventory and clear the table: every player would then see the same chest. For a loot `MinecartChest` (a table
  this mod converts, in an enabled dimension, on the server) the declared methods `getItem`, `removeItem`, `removeItemNoUpdate`,
  `setItem` and `clearContent` now behave like an empty container, and `setLootTable(null)` is cancelled. The last two matter
  because `isEmpty` is an inherited interface default and cannot be targeted by name: it unpacks through
  `unpackChestVehicleLootTable`, which clears the table and fills with `setItem`, and both steps are now no-ops. Decay is the one
  deliberate exception and uses `ContainerProtection.clearCartLootTable`, so `replace_when_decayed` still turns a cart into an ordinary one.
  Side effects: hoppers see nothing in a loot cart and cannot insert into it, and breaking one drops no shared loot (its real inventory
  is empty by design). Not covered: `/item` on a loot cart's slots. Cart break rules and open/close events were added afterwards; see the next two sections.
  Test: put a hopper under a loot chest minecart; it must pull nothing, and two accounts must still get different loot afterwards.
  Boot first: the mixin targets six names on `AbstractMinecartContainer`; a wrong one stops the server at startup.

### Structure-template sanitization and chest-minecart break rules - NOT build-verified

Two more gaps from the same audit, both loaders.

- **Structure-template sanitization.** The per-player loot state is a data attachment, and attachments are written into the
  object's own NBT. Saving a structure with a structure block (`StructureTemplate.fillFromWorld`) therefore copied a looted
  chest's "player X already looted this" into every place the structure was loaded, and did the same for chest minecarts and
  item frames saved with entities. `MixinStructureTemplate` now wraps the two NBT saves (`BlockEntity.saveWithId` in
  `fillFromWorld`, `Entity.save` in `fillEntityList`) and `LootStateStripper` removes only this mod's attachment
  (`lootr_serveronly:lootr_loot_state`) from the result, under both `neoforge:attachments` and `fabric:attachments`. The loot
  table and seed stay, so the placed copy is a loot container again; other mods' attachments are untouched.
  Test: loot a chest that has a loot table, save the area with a structure block, load it elsewhere: a fresh account must get
  loot, and the original's looted players must not be marked looted in the copy.
- **Chest-minecart break rules.** Carts used to be only protected (invulnerable unless creative). They now follow the same
  rules as blocks, on a player's hit (`AttackEntityEvent` / Fabric `AttackEntityCallback`, which returns `SUCCESS`, not `FAIL`,
  like the item-frame handler): `enable_break` (or a fake player with `enable_fake_player_break`) allows the break and lifts the
  invulnerability for that player; `break_to_drop_loot` gives a non-sneaking real player their own loot and cancels the hit;
  `disable_break` refuses survival always and creative unless sneaking; otherwise `protect_containers` refuses, and failing that
  `require_sneak_to_break` refuses a non-sneaking hit; if the hit goes ahead, `should_drop_player_loot` first drops the hitter's
  loot at the cart. `takeLoot` and `collectLoot` for carts mirror the block versions. Defaults are unchanged: survival cannot
  break a cart, creative can. Explosions and other non-player damage are still governed by `protect_containers`,
  `blast_immune` and `blast_resistant` only.
  Test: set `break_to_drop_loot` true and hit a loot cart without sneaking (you get your loot, the cart stays; a second account
  gets different loot); set `enable_break` true and break one as survival; set `disable_break` true and hit as creative (refused,
  then allowed while sneaking).

### Chest-minecart open/close events - NOT build-verified

The last high-priority item from the audit. Both loaders.

- **What vanilla does for a chest minecart** (read from `MinecartChest`): after a successful open it sends the
  `CONTAINER_OPEN` game event and angers nearby piglins; when the menu closes, `stopOpen` sends `CONTAINER_CLOSE` at the cart's
  position. A cart has no lid, sound, open counter or redstone, so unlike chests and barrels nothing else is involved. The loot
  cart's open path replaced vanilla's, so none of that happened (sculk sensors and wardens never heard it, piglins stayed calm).
- **Now:** `MinecartInteractionHandler` passes a close callback to `PlayerScopedContainer` (so `CONTAINER_CLOSE` is sent when
  the menu closes, and also on a disconnect with the menu open, as in vanilla) and, if `openMenu` really opened a menu, sends
  `CONTAINER_OPEN` and calls `PiglinAi.angerNearbyPiglins`. This deliberately does not use `OpenTracker`: that class exists to
  drive a block's lid, door, sound and redstone from a per-block opener set, none of which a cart has.
- Test: put a sculk sensor next to a loot chest minecart and open it (it should trigger on open and again on close); open one near
  a piglin (it should turn hostile unless you wear gold).
