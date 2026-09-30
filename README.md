# Lootr (Server-Only)

## What this is

A reimplementation of Lootr's "unique loot per player" mechanic for Minecraft
1.21.1 that needs **no mod on the client**: vanilla clients can join. It runs
on **NeoForge** (21.1.219) and **Fabric** (Loader 0.16.5, Loom 1.14.3) with the
same feature set on both.

Ported objects: **chest, trapped chest, barrel, shulker box, chest minecart,
decorated pot, suspicious sand/gravel (brushable block) and item frame**, plus
first-use advancements, a config layer, loot-table filters, `/lootr` admin
commands and protection so one player can't destroy loot for everyone.

Status: both loaders compile and boot a dedicated server (confirmed from real
runs) and a light playtest passed, but which features it covered was not
recorded. See [Verification status](#verification-status) for exactly what is
and isn't confirmed. Nothing was ever compiled in the authoring sandbox (no JDK
or network there), so every change has been checked by reading vanilla sources,
not by a compiler, until you build it.

### The one rule that makes zero-client-mod possible

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

## What each object does

| Object | Mechanism | Notes |
|---|---|---|
| Chest, trapped chest, barrel, shulker box | Right-click cancelled; a per-player container opens in the normal vanilla menu | Trapped chest has its own branch only to pick its advancement; menu handling is shared with chest |
| Chest minecart | `EntityInteract` (NeoForge) / `UseEntityCallback` (Fabric); `ChestMenu.threeRows` | State lives on an entity attachment |
| Decorated pot | Hit or right-click hands that player their own loot straight into their inventory; the pot is never removed | Overflow drops at the player's feet. Creative players can still break it |
| Suspicious sand/gravel | `MixinBrushableBlockEntity`: the block keeps its loot table and never turns into sand; each player/team that finishes brushing gets their own loot | Dust animation and completion effects stay shared |
| Item frame | **Hitting** a marked frame gives that player/team one copy of the framed item, once | Needs a marker tag; see [Item frames](#item-frames) |

Team loot, the dimension lists, loot-table filters, the refresh timer and
protection apply to all of them (item frames ignore the refresh timer and the
loot-table filters: they have no loot table and never refresh).

## Build and run

```
.\gradlew clean build                  # builds both loaders
.\gradlew :neoforge:runServer          # or :fabric:runServer
```

A plain `runServer` runs the loaders one after another and both bind port 25565,
so run one at a time. The Fabric dev server stops on first run until you accept
`eula.txt` in its `run/` folder.

## Project layout

```
build.gradle, settings.gradle, gradle.properties, .github/workflows/build.yml

neoforge/
  build.gradle
  src/main/java/net/lootr/serveronly/
    LootrServerOnly.java              entry point; registers config + attachment
    advancement/OpenedAdvancements    awards the first-use advancements
    command/LootrCommands             /lootr info|reset|frame (+ reload on Fabric)
    config/LootrConfig, TeamResolver  ModConfigSpec config; team -> stable UUID
    data/                             LootrLootState, its attachment serializer,
                                      PlayerScopedContainer
    interaction/                      ContainerInteractionHandler (4 block containers),
                                      MinecartInteractionHandler, PotInteractionHandler,
                                      ItemFrameInteractionHandler, BrushableLoot,
                                      ContainerProtection
    mixin/                            see "Mixins" below
    registry/                         ModAttachments, ModLootTags, ItemFrameMarker
  src/main/resources/
    META-INF/neoforge.mods.toml, pack.mcmeta, lootr_serveronly.mixins.json
    data/lootr_serveronly/advancement/*.json, loot_table/chests/example.json

fabric/                               same packages under net/lootr/serveronly/fabric/
  build.gradle                        Loom; three Fabric API modules (see below)
  src/main/resources/fabric.mod.json, lootr_serveronly.mixins.json,
    data/lootr_serveronly/advancement/*.json
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

## Configuration

NeoForge: `config/lootr_serveronly-common.toml` (sections shown, picked up on
config reload). Fabric: `config/lootr_serveronly.json` with the same keys
(read at startup; restart or run `/lootr reload` to apply; a malformed file is copied to `.bak` before
defaults are written back).

| Key (NeoForge section) | Default | Meaning |
|---|---|---|
| `team_loot` (`team`) | `false` | Players on the same scoreboard team share one loot pool; teamless players are a team of one |
| `dimension_whitelist` (`dimensions`) | empty | If non-empty, only these dimensions are converted |
| `dimension_blacklist` (`dimensions`) | empty | Never converted; wins over the whitelist |
| `loot_table_blacklist` (`loot_filters`) | empty | Exact loot table ids left fully vanilla |
| `mod_id_blacklist` (`loot_filters`) | empty | Whole namespaces left fully vanilla |
| `refresh_ticks` (`refresh`) | `0` (off) | Ticks after a container is first looted before it resets for everyone. Checked when someone opens it, not in the background |
| `convert_item_frames` (`item_frames`) | `true` | Mark frames that structures spawn |
| `convert_elytras_to_item_frames` (`item_frames`) | `true` | Mark the End City ship's Elytra frame |
| `protect_containers` (`protection`) | `true` | See [Protection](#protection) |
| `break_to_drop_loot` (`protection`) | `false` | A non-sneaking survival break collects your loot instead of breaking the block (see [Break rules](#break-rules)) |
| `require_sneak_to_break` (`protection`) | `false` | Only when `protect_containers` is off: survival players must sneak to break |
| `should_drop_player_loot` (`protection`) | `false` | When a container really is destroyed by a player, their own loot spills at the block |

A blacklisted loot table is left completely vanilla: no per-player loot, no
break protection, and vanilla resolves it as usual. Every place that decides "is
this a loot container" goes through `ModLootTags.isTableEnabled`, so the filter
applies uniformly to containers, minecarts, pots and brushable blocks.

## Commands

Operators (permission level 2). Targets are explicit so the commands work from a
command block or the console:

- `/lootr info block <pos>` and `/lootr info entity <target>`: how many
  players/teams have looted it, and the refresh timer.
- `/lootr reset block <pos>` and `/lootr reset entity <target>`: forget
  everyone's loot so the next open by anyone rolls fresh loot.
- `/lootr frame mark <target>` and `/lootr frame unmark <target>`: make one item
  frame a loot frame, or stop it being one (see [Item frames](#item-frames)).
  `mark` refuses fixed, invisible, empty and map frames. `unmark` does not clear
  who has already taken a copy; use `reset` for that.
- `/lootr reload` (**Fabric only**): re-read `config/lootr_serveronly.json`
  without a restart. NeoForge picks up config file changes by itself. A malformed
  file is backed up to `.bak` and replaced with defaults, exactly as at startup.

Entities cover chest minecarts and item frames, e.g.
`/lootr reset entity @e[type=minecraft:chest_minecart,limit=1,sort=nearest]`.
An object only has state once somebody has looted it; before that both commands
say so.

## Protection

Without it, one player breaking a loot chest, or one creeper, destroys the loot
for everyone. With `protect_containers` on (default):

- **Blocks.** Survival and adventure players cannot break a loot chest, barrel,
  shulker box, suspicious block or decorated pot, and explosions leave them
  standing (NeoForge `ExplosionEvent.Detonate`; Fabric `MixinExplosion`).
- **Pots and projectiles.** `MixinDecoratedPotBlock` skips vanilla's
  shatter-on-projectile-hit for loot pots.
- **Entities.** A marked, non-empty item frame and a loot chest minecart are
  immune to arrows, explosions, fire and mobs (NeoForge
  `EntityInvulnerabilityCheckEvent`; Fabric `MixinEntity` on
  `Entity.isInvulnerableTo`, the same method that event fires from). The shared
  check is `ContainerProtection.isManagedEntity`.
- **Hoppers, comparators, break drops.** `MixinRandomizableContainerBlockEntity`
  makes a managed container look empty to vanilla's `isEmpty/getItem/removeItem/
  removeItemNoUpdate/setItem`, because vanilla's versions unpack the loot table
  and would turn it into one shared chest. **Side effect: hoppers see nothing in
  a loot chest and comparators read 0.**

Creative players can still break or remove any of these. Not covered: block
removal that doesn't go through the break or explosion paths (for example mob
griefing that removes blocks directly, `/setblock`, or other mods).

### Break rules

What happens when a player breaks a loot chest, barrel, shulker box (or trapped
chest). Checked in this order for a **non-creative** player; creative players
skip straight to the last step, like everywhere else in this mod:

1. `break_to_drop_loot` on, player **not** sneaking: the player's loot is rolled
   if needed and put into their inventory (overflow drops at their feet), the
   container's advancement is awarded, and the break is cancelled. The
   container stays and counts as looted for that player (or team). It goes into
   the inventory, not onto the ground as upstream does, because a dropped item is
   visible to and pickable by every other player nearby. Breaking takes its
   normal time; the loot is handed over when the break completes.
2. `protect_containers` on: the break is cancelled ("Loot containers can't be
   broken"). This includes sneaking.
3. `require_sneak_to_break` on, player not sneaking: cancelled with a hint.
   Only reached when `protect_containers` is off; this is upstream's default mode.
4. Otherwise the break proceeds. If `should_drop_player_loot` is on, the
   breaker's own loot (rolled first if needed) spills at the block before the
   container is destroyed.

With every option at its default the behavior is unchanged: survival players
cannot break loot containers, creative players can. Suspicious blocks and pots
ignore `break_to_drop_loot` and `should_drop_player_loot` (pots already give
loot when hit; see [What each object does](#what-each-object-does)). In both
the collect and spill paths the loot is removed from the player's entry
before it is handed out, so a break that another mod cancels afterwards cannot
duplicate it.

Upstream options not ported: `enable_break` (same as `protect_containers` off and
`require_sneak_to_break` off), `enable_fake_player_break`, separate
`blast_resistant` / `blast_immune` (protection treats every explosion as
immune), `brushables_self_support`, `item_frames_self_support`, and upstream's
break handling for chest minecarts.

## Item frames

A frame is not a container: it holds one item. Matching upstream Lootr, the
framed item *is* the loot: **hitting** a marked frame gives that player (or
team) one copy of the item, once. The frame and its item never change, so every
other player still finds it intact. Right-click is swallowed so nobody can
rotate the item or swap one in. Creative players are left alone so an admin can
remove a frame. Frames never refresh (upstream's frame type has
`canRefresh() == false`), so `refresh_ticks` is ignored for them.

**Frames must be marked.** Every other container is recognised by the loot
table vanilla put on it; a vanilla frame has none and nothing marks it as
structure-generated. A frame is a Lootr frame only if it carries the entity tag
`lootr_serveronly.loot_frame`. Two shortcuts are deliberately *not* used
because both are dangerous: "every frame holding an item" would let any player
duplicate items out of any base, and "every newly spawned frame" cannot tell a
structure from a player placing one.

**Automatic marking.** Two mixins tag the vanilla frame at the moment a
*structure* spawns it, the one point where structure and player can be told
apart: `MixinStructureTemplate` (frames stored in structure templates, world
generation only) and `MixinEndCityPiece` (the End City ship's Elytra frame,
which vanilla builds in code). They tag the frame vanilla already created
instead of swapping in a custom entity, which is what keeps this server-only.
Fixed, invisible, empty and map frames are skipped. Limits: only structures
generated **after** the mod is installed are marked, so frames in an existing
world, and anything placed by a structure block, `/place` or a schematic
printer, must be marked by hand with the command:

    /lootr frame mark @e[type=item_frame,limit=1,sort=nearest]

`/lootr frame unmark <target>` reverses it. The command applies the same
eligibility rules as the automatic marker (no fixed, invisible, empty or map
frames) and says why it refused. The raw tag still works
(`/tag <frame> add lootr_serveronly.loot_frame`) but skips those checks, so avoid
tagging map frames that way (upstream can't support framed maps either).

## Advancements

Eight first-use advancements plus a root: `chest_opened`,
`trapped_chest_opened`, `barrel_opened`, `shulker_opened`, `minecart_opened`,
`pot_opened`, `item_frame_opened`, `brushable_opened`. Each uses vanilla's
`minecraft:impossible` trigger and is awarded directly by
`OpenedAdvancements.award(...)` the first time a player (or team member) loots
that object, so no custom `CriterionTrigger` and no registry write is needed.

Titles and descriptions are plain `{"text": ...}` components and the action-bar
hints are literal strings. The project ships no lang file and uses no
translation keys, because a vanilla client has no lang file for a mod it does
not have and would show a raw key. Trade-off: English only, and not localisable
by a resource pack.

## Mixins

Used sparingly, and only where no loader event exists. The mixin config has
`defaultRequire: 1`, so a target that fails to apply **stops the server at
startup** instead of silently doing nothing. Mixin applies each one when its
target class first loads, so a target that loads late (structure classes) is
only proven once that code runs.

| Mixin | Loader | Purpose |
|---|---|---|
| `MixinRandomizableContainerBlockEntity` | both | Stop vanilla unpacking a loot table behind the mod's back |
| `MixinBrushableBlockEntity`, `AccessorBrushableBlockEntity` | both | Per-player brushing |
| `MixinDecoratedPotBlock` | both | Loot pots survive projectiles |
| `MixinStructureTemplate`, `MixinEndCityPiece`, `AccessorItemFrame` | both | Mark structure item frames |
| `MixinExplosion` | Fabric only | Fabric has no explosion event (NeoForge uses one) |
| `MixinEntity` | Fabric only | Fabric has no invulnerability callback (NeoForge uses one) |

## Known limitations

- **Shared visual state.** Two players opening the same physical
  chest/barrel/shulker box still see each other's lid animation and hear a
  shared open/close sound, like two people opening a vanilla double chest.
  Because the mod never touches rendering, it can't reproduce upstream's
  client-side "visual openers vs logical openers" split. See design doc §7.
- **"Loot container" is detected by loot table**, not identity: any vanilla
  container with an assigned, non-blacklisted loot table is treated as a Lootr
  container. Upstream's per-block enable toggles are not reimplemented;
  dimension lists and loot-table/namespace filters are.
- **Frames in existing worlds** aren't marked automatically (see above).
- **Hoppers and comparators** see nothing in a loot container (see Protection).
- **Text is English-only** (see Advancements).
- **Not ported, deliberately:** decay, refresh particles, JEI/Jade integration
  (client-side by nature), pluggable team resolvers (team loot is scoreboard
  teams only).

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
- **The two newest mixins, `MixinEntity` (Fabric) and `MixinDecoratedPotBlock`
  (both), and the pot/minecart additions to protection.** Added after the last
  real boot. Boot both loaders before anything else: a wrong target crashes the
  server at startup. Their targets were verified in the 1.21.1 sources
  (`isInvulnerableTo` is declared only in `Entity`; `onProjectileHit` once in
  `DecoratedPotBlock`).
- That structure frames are actually marked in a freshly generated structure or
  End City, and that `MixinStructureTemplate` applies in a real run (its target
  was changed from a compiler-indexed lambda to a named call after a real
  **Fabric** failure; see design doc §10). Also confirm the Fabric refmap: build
  `:fabric:build` and check the jar contains `lootr_serveronly.refmap.json` (the
  name was mismatched with Loom's default until it was pinned in
  `fabric/build.gradle`; harmless in a dev run, fatal on a real Fabric server).
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
- Entities have no `setChanged()`; block entities do. On Fabric an attachment is
  a raw `CompoundTag`, so handlers load, mutate and save `LootrLootState` by
  hand with `level.registryAccess()`.

See `LOOTR_SERVER_ONLY_DESIGN.md` for the design rationale, per-object plan and
build order.

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

**Still not ported:** decay (and any commands that would depend on it). Break rules are now
implemented; see [Break rules](#break-rules).
