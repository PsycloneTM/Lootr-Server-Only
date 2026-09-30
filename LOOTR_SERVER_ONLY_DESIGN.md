# Lootr → Server-Only: Design & Migration Plan

**Target:** vanilla, unmodified Minecraft clients can join a server running
this mod and interact with Lootr-style unique-per-player loot containers
with zero client-side installation.

**Status:** every container type is ported on **both NeoForge and Fabric**:
chest, trapped chest, barrel, shulker box, chest minecart, decorated pot, item
frame and brushable block, plus first-use advancements (eight), a config layer
(team loot, dimension lists, loot-table/mod-id filters, refresh timer,
item-frame conversion, `protect_containers`), `/lootr info|reset|frame` admin
commands (plus `/lootr reload` on Fabric), and protection (`ContainerProtection`, `MixinEntity`,
`MixinDecoratedPotBlock`, ...). Brushables, item frames and protection use
mixins. No client-side install is needed.

**Build/boot status:** both loaders compile and boot a dedicated server on the
feature set up to and including commands and loot-table filters (confirmed from
real runs). The newest changes - `MixinEntity` (Fabric), `MixinDecoratedPotBlock`
(both) and the pot/minecart additions to protection - were written after the
last real boot and are **not yet booted**; boot first, since a wrong mixin
target stops the server at startup. §9 is the full verification matrix.

---

## 1. Why this is hard: the actual constraint

Upstream Lootr is a real NeoForge/Fabric mod (`common`/`fabric`/`neoforge`
source sets, ~200 files in `common` alone) — not a Bukkit/Spigot/Paper
plugin. Every container type it adds (chest, barrel, shulker box, trapped
chest, brushable block, decorated pot, minecart, item frame) is implemented
as a **new registered `Block`/`Item`/`BlockEntityType`**, with its own
blockstate JSON, model JSON, and — critically — its own client-side
`BlockEntityRendererProvider` registration (confirmed directly in upstream's
`neoforge/src/main/java/.../setup/ClientSetup.java`, which calls
`event.registerBlockEntityRenderer(...)` once per custom block entity type).

That last point is the crux of the whole project:

> **Registering any new `BlockEntityType` that should render as a
> chest/barrel/shulker requires a client-side Java call.** Vanilla's chest
> lid-open animation is driven by `ChestRenderer`, which is bound per
> `BlockEntityType` via that registration call — it is not applied
> automatically from block shape or a model JSON. A model/blockstate JSON
> alone is not sufficient to get chest-like rendering for a *new* block
> entity type.

Given the requirement of "zero client mod, full stop," this rules out
**every** upstream approach that adds a new block/item/block-entity type for
rendering purposes. The entire redesign follows from working around this one
constraint.

## 2. The core trick: attach to vanilla's own block entities

Instead of registering new blocks, **attach Lootr's per-player loot data
directly onto vanilla's existing block entities** (`minecraft:chest`,
`minecraft:barrel`, `minecraft:shulker_box`, etc.) using **NeoForge's Data
Attachment API** (`net.neoforged.neoforge.attachment.AttachmentType`). This
is a supported, first-class NeoForge mechanism for exactly this purpose:
persistent, serializing, typed data hung off any `BlockEntity`/`Entity`/etc.
without subclassing it.

Then, intercept the **interaction**, not the **block**: a server-side event
listener (`PlayerInteractEvent.RightClickBlock`) checks whether the target
block entity carries Lootr data, cancels vanilla's own menu-open for that
interaction, and opens vanilla's own `ChestMenu`/`ShulkerBoxMenu`/etc.
constructed against a **plain vanilla `Container` implementation** scoped to
the opening player, instead of the block entity's real, shared inventory.

Because `ChestMenu` only ever calls methods on the `Container` interface —
it has no idea whether that `Container` is "the real inventory" or a
per-player view — a stock client rendering that menu cannot tell the
difference. This is validated against upstream Lootr's own design: upstream
*already* uses exactly this trick internally (`LootrInventory implements
Container, MenuProvider`, handed to vanilla's own `ChestMenu.threeRows(...)`)
— we're only removing the "which literal block is this" layer, not
inventing a new mechanism from scratch.

**What we get for free, still:** vanilla's lid-open animation, the shared
open/close sound, the "N players have this open" opener counter, hopper
interaction, redstone comparator output based on fill level (via a mixin —
see §5), locking — all of it, because we never stopped using vanilla's
actual `ChestBlockEntity` instance.

**What we give up, unavoidably:** upstream's "visual openers vs. logical
openers" split. In upstream Lootr, if two players each see their own loot
from the same physical chest, the client mod tracks this separately so each
player only sees *their own* lid open and hears *their own* open sound, not
sounds/animations triggered by other players' independent sessions. That
distinction is fundamentally a rendering-layer concept — reproducing it
requires telling two different clients two different visual truths about
the same block entity, which requires client code, full stop. **This is the
single biggest, unavoidable behavioral regression of going server-only** and
should be signed off on explicitly before investing further, not discovered
after the fact. (Mitigation ideas are in §7.)

## 3. Per-container-type plan

| Container | Vanilla BE to attach to | Interaction hook | Status |
|---|---|---|---|
| Chest | `ChestBlockEntity` | `PlayerInteractEvent.RightClickBlock`, cancel + reopen with `ChestMenu` | **Ported** (`ContainerInteractionHandler`) |
| Trapped chest | `TrappedChestBlockEntity` (extends `ChestBlockEntity`) | Same as chest | **Ported for free** — matches the `instanceof ChestBlockEntity` branch already; vanilla's own redstone-on-open signal is untouched since it lives on the block/BE class, not the `Container` we substitute |
| Barrel | `BarrelBlockEntity` | Same event, opens via `ChestMenu.threeRows` (barrels use the same menu class as chests in vanilla, not a distinct `BarrelMenu`) | **Ported** (`ContainerInteractionHandler`) |
| Shulker box | `ShulkerBoxBlockEntity` | Same event, `ShulkerBoxMenu(int, Inventory, Container)` — constructor confirmed against MC 1.21 mappings | **Ported** (`ContainerInteractionHandler`); note shulker boxes can be mined and kept as items with contents — the attachment must survive `saveToItem`/`loadFromItem`, which NeoForge attachments support out of the box via the same BE NBT round-trip |
| Decorated pot | `DecoratedPotBlockEntity` | Not menu-based: a pot resolves its loot table when broken and is destroyed for everyone. `PotInteractionHandler` substitutes the whole interaction - right-click and left-click on a loot pot are cancelled and the player is rolled their own loot straight into their inventory (overflow drops at their feet) with the vanilla wobble/shatter sound; the pot is never removed. "Already looted" is an all-empty per-player entry in the same attachment. Creative players can still break pots. Protection: `ContainerProtection` recognises pots (a pot is a plain `BlockEntity`, not a `RandomizableContainerBlockEntity`) so explosions skip them, and `MixinDecoratedPotBlock` cancels vanilla's shatter-on-projectile-hit. | **Ported on both loaders; boots cleanly.** Open, needs a playtest: client-side break prediction and sherd retention after a cancelled break. The projectile/explosion protection is newer and not yet booted. |
| Chest minecart | `MinecartChest` (an `Entity`, not a `BlockEntity`) | `PlayerInteractEvent.EntityInteract` (NeoForge) / `UseEntityCallback` (Fabric); attachments support `Entity` targets, so the same mechanism applies against an entity instead of a block entity. Opens `ChestMenu.threeRows`. Immune to arrows, explosions, fire and mobs via the shared `ContainerProtection.isManagedEntity` check. | **Ported on both loaders** (`MinecartInteractionHandler`); boots cleanly. Damage protection is newer and not yet booted. |
| Item frame ("Lootr" variant) | `ItemFrame` entity | `AttackEntityEvent` (hit = take) + `EntityInteract` (swallowed); Fabric: `AttackEntityCallback` / `UseEntityCallback` | **Ported on both loaders; boots cleanly.** Not a `Container`: the framed item IS the loot, and a hit gives each player/team one copy, once. The frame is never changed or removed and never refreshes (upstream `canRefresh() == false`). **Requires an explicit marker** (entity tag `lootr_serveronly.loot_frame`) because a vanilla frame has no loot table; structure frames are marked automatically by `MixinStructureTemplate` / `MixinEndCityPiece` (world generation only; existing worlds need `/tag`). Marked frames are invulnerable to non-creative damage on both loaders (NeoForge `EntityInvulnerabilityCheckEvent`, Fabric `MixinEntity`; see §10). Automatic marking is not yet confirmed in a real generated structure. |
| Brushable block (suspicious sand/gravel) | `BrushableBlockEntity` | **No open/menu interaction exists** - loot is extracted by repeated brushing, not a GUI. Implemented with mixins on vanilla's own class (`MixinBrushableBlockEntity` + `AccessorBrushableBlockEntity`, logic in `BrushableLoot`): players who already looted the block make no progress; vanilla's loot unpack is cancelled so the shared item is never rolled and the loot table survives; `brushingCompleted` is replaced so the block never turns into sand/gravel and the loot goes to the brusher only. Dust animation and completion effects stay shared. | **Ported on both loaders; boots cleanly.** Behaviour in play is not confirmed. |

**Practical migration order (all steps now done; kept for the reasoning):** chest → trapped chest → barrel → shulker box
(all near-identical, "menu substitution" pattern) first, since that's most
of the mod's player-facing surface area and validates the core mechanism
across vanilla's slightly different menu classes. Brushable blocks next
(different mechanism, but self-contained). Decorated pots, minecarts, and
item frames last, as they're comparatively niche and each need bespoke
design work.

## 4. Loot generation core (mostly a direct port, low risk)

Upstream's actual "generate random loot for this player, once" logic already
has **no client dependency** — it's server-side data generation feeding a
plain `Container`. Specifically:

- `SimpleLootrInstance` (`common/.../api/data/SimpleLootrInstance.java`) —
  holds the "has this player opened this yet" / per-player-contents state.
  Ported as `LootrLootState` in the scaffold, with every client-sync field
  (`clientOpeners`, `clientOpened`, `clientRefreshing`, `clientDecaying`,
  the `visualOpenersSupplier`) deliberately dropped, since there is no
  client to sync any of it to.
- `LootrInventory` (`common/.../data/LootrInventory.java`) — a plain
  `Container` + `MenuProvider`. Ported as `PlayerScopedContainer` in the
  scaffold, simplified since `MenuBuilder` indirection is unnecessary once
  we're only ever handing this to vanilla's own menu factory methods
  (`ChestMenu.threeRows`, etc.) directly from the interaction handler.
- `LootrAPI.handleProviderOpen`/`handleProviderSneak` (712-line
  `common/.../api/LootrAPI.java`) — the real port work here is *extracting*
  the loot-table-roll-and-cache logic from this file (it's currently
  entangled with team-loot lookups, advancement triggers, and
  provider-abstraction layers meant to support the many container types
  uniformly) into a smaller, chest-first version, then re-generalizing once
  more container types are added. Don't port this file wholesale on day one
  — it encodes decisions (team loot, refresh timers, decay) described in §6
  that are worth deciding fresh rather than inheriting silently.

## 5. Mixins: expect to keep most of them, retarget almost all of them

Upstream has ~25 mixin classes across categories: accessors, brushing,
chest-blocking (elytra-in-container prevention), falling-block handling,
loot-table filtering, hopper interaction, locking, particle-spawning-culling,
POI (village mechanics) integration, redstone comparator output, mob-spawn
suppression near containers, structure-template saving, client-level sync,
scoreboard/team lookups, and tickers.

The overwhelming majority of these exist because upstream's containers are
**different block classes** from vanilla's, so vanilla's own hopper/redstone/
POI/spawn logic doesn't automatically apply to them and has to be
mixin-patched in. **In the server-only design this category of mixin
mostly disappears**, because we deliberately kept using vanilla's real
block/block-entity classes — vanilla hopper interaction, comparator
output, and POI already work against `minecraft:chest` etc. natively.

What's actually still needed, retargeted at vanilla classes instead of
Lootr's own:
- **Loot-table filtering** (`mixin/filter/MixinLootTable.java`) — if the
  goal is "reroll loot per player" for *any* vanilla container that has a
  loot table assigned (structure-generated loot chests), the hook point is
  the same regardless of which mod defines the container.
- **Elytra-in-container blocking, brushing, ticker mixins** — depend on the
  brushable-block mechanism decision in §3; revisit once that's designed.
- **Team lookups / scoreboard mixin** — only needed if team-shared loot
  (§6) is in scope.
- Accessor mixins are mostly plumbing to reach private vanilla fields/
  methods; keep whichever ones the ported logic still needs, drop the rest.

**What was actually implemented** (far fewer than upstream, all reactive):
`MixinRandomizableContainerBlockEntity` (stop vanilla unpacking a loot table for
hoppers/comparators/break drops), `MixinBrushableBlockEntity` +
`AccessorBrushableBlockEntity` (per-player brushing), `MixinStructureTemplate` +
`MixinEndCityPiece` + `AccessorItemFrame` (mark structure item frames),
`MixinDecoratedPotBlock` (pots survive projectiles), and two Fabric-only ones
where NeoForge has an event: `MixinExplosion` and `MixinEntity`. The mixin config
uses `defaultRequire: 1`: a target that fails to apply stops the server at
startup. Upstream's loot-table filtering, hopper-locking, POI, spawn-suppression
and client-sync mixins were not needed because vanilla's own block classes are
kept.

**Recommendation:** don't attempt to pre-port every mixin speculatively.
Port the chest happy-path first (§3), run it, and add mixins reactively as
specific vanilla behaviors (hopper extraction while a player has the menu
open, redstone comparator reading of per-player contents, etc.) are found to
need patching — most will turn out to already work because the underlying
`BlockEntity` is genuinely vanilla.

## 6. Product decisions to make explicitly, not inherit implicitly

Upstream Lootr has a large, mature config surface (`ConfigManagerBase` and
friends) covering things like: team-shared loot vs. strictly-per-player,
loot refresh/decay timers, per-dimension and per-block enable/disable,
advancement triggers on first open, and JEI/Jade integration. None of this
is free in a server-only rewrite, and some of it interacts with the "shared
visual state" limitation in §2 in ways worth deciding up front:

- **Team loot** *(resolved: `team_loot`, scoreboard teams only)*: if players on the same team should share one generated
  loot pool, `PlayerScopedContainer` needs to key off team ID instead of
  player UUID for those containers — straightforward, but decide before
  building `LootrLootState`'s keying assumptions further.
- **Refresh/decay timers** *(resolved: `refresh_ticks` checked lazily at open-time; decay not ported)*: upstream re-rolls loot after a configurable time
  or on some trigger. This is orthogonal to the client question and ports
  cleanly — just a scheduled check against `LootrLootState`'s stored
  timestamps.
- **Advancements** *(resolved: eight, via `minecraft:impossible`, see §8.4)*: upstream fires a custom trigger
  (`IContainerTrigger`/`common/.../advancement`) on first-open. Custom
  advancement triggers are pure server-side data + a Java `CriterionTrigger`
  class — no client dependency, ports directly.
- **JEI/Jade integration** *(resolved: dropped)*: both are **client-side** mods that show tooltip/
  overlay info. By definition, integrating with them means shipping client
  code for players who have JEI/Jade installed — which contradicts "zero
  client mod" as a hard requirement. Recommendation: drop this integration
  entirely for the server-only version, or treat it explicitly as an
  *optional* separate client-side companion mod that degrades gracefully
  when absent (most players won't have it; the ones who do opt in
  knowingly). Don't let this scope-creep the core server-only mod.

## 7. Mitigating the "shared visual state" gap (§2), optional/future

Not required for a first working version, but worth having a plan for
since it's the most visible behavioral difference from upstream:

- **Cosmetic acceptance:** simplest option — just accept that two players
  looting "the same" chest simultaneously see a shared lid/sound, same as
  vanilla double-chests today. Most single-player-ish "unique loot per
  player" use cases (structure loot, dungeon chests) rarely have two
  players opening the exact same block within the same few seconds, so this
  may simply not matter in practice. Validate against your actual use case
  before building anything fancier.
- **Partial mitigation via server-authoritative sound/particle control:**
  NeoForge/vanilla allow a server to target a sound or particle effect at a
  specific player only (`ServerPlayer.connection.send(...)` with a
  client-bound sound packet, or level events sent to a single player via
  `ServerLevel.sendParticles(ServerPlayer, ...)`). This doesn't fix the lid
  animation (that's tied to `ChestBlockEntity`'s shared `openCount`, visible
  to everyone rendering the block), but could make the *sound* experience
  feel more personal. This is a genuine engineering rabbit hole — scope it
  as a stretch goal, not a blocker for v1.

## 8. Suggested build order (rough sizing, not a committed schedule)

1. ~~**Chest end-to-end**~~ — **Done and build-verified** (`gradlew clean
   build` succeeds on NeoForge with the real MDK).
2. ~~**Barrel, shulker box, trapped chest**~~ - **Done**; all four containers
   share `ContainerInteractionHandler`.
2b. ~~**Port the same four containers to Fabric.**~~ - **Done.** Fabric API's
   `fabric-data-attachment-api-v1` supplies the attachments (checked not to pull
   in `fabric-registry-sync-v0`, which would kick vanilla clients). Because a
   Fabric attachment is a raw `CompoundTag`, handlers load, mutate and save
   `LootrLootState` by hand.
3. ~~**Config layer**~~ — **Done; boots on both loaders.** Team loot
   (scoreboard team → stable UUID, no pluggable resolver), dimension
   whitelist/blacklist (blacklist wins; empty whitelist = all), and a refresh
   timer (`refresh_ticks`, 0 = off). NeoForge uses `ModConfigSpec`
   (`config/lootr_serveronly-common.toml`); Fabric uses a hand-rolled JSON
   file (`config/lootr_serveronly.json`, read at startup; `/lootr reload` re-reads it)
   to avoid a Cloth Config dependency. Refresh is checked lazily at
   open-time via one per-container `firstGeneratedGameTime`, not a ticking
   scheduler. Loot-table/mod-id blacklists and `/lootr info|reset` were added later (see README). Deliberately NOT ported: decay,
   refresh particles, pluggable team resolvers.
4. ~~**Advancements**~~ — **Done; boots on both loaders.** Eight advancements
   (`chest_opened`, `trapped_chest_opened`, `barrel_opened`, `shulker_opened`,
   `minecart_opened`, `pot_opened`, `item_frame_opened`, `brushable_opened`) plus
   a root use vanilla's `minecraft:impossible` trigger and are
   awarded directly by `OpenedAdvancements.award(...)` once per player per
   container. A first attempt registered custom `CriterionTrigger`s like
   upstream does; that crashed both loaders at startup with "Registry is
   already frozen" (the trigger-type registry is frozen by the time this
   mod initialises), so it was dropped in favour of this approach, which
   also keeps the mod free of any registry writes. Titles and
   descriptions are plain `{"text": ...}` components rather than translation
   keys, so a vanilla client shows readable text with no lang file (English
   only; a server resource pack could add localisation later if wanted).
5. ~~**Brushable blocks**~~ — **Done on both loaders; boots cleanly.**
   `MixinBrushableBlockEntity` + `BrushableLoot`. The block keeps its loot
   table and never turns into sand/gravel; each player/team that finishes
   brushing once gets their own loot straight into their inventory, and
   players who already looted it make no progress. Dust animation and
   completion effects stay shared. A wrong mixin name fails at startup
   (`defaultRequire = 1`), so check the server boots first.
6. ~~**Decorated pots, minecarts, item frames**~~ - **Done on both loaders;
   boot cleanly.** Item frames additionally rely on automatic structure marking
   (two mixins) - see §10.
6b. ~~**Container protection**~~ - **Done; blocks/explosions boot cleanly.**
   `ContainerProtection`: survival players cannot break a loot container
   (chest/barrel/shulker/suspicious block/pot) and explosions skip them (NeoForge
   `ExplosionEvent.Detonate`; Fabric `MixinExplosion`). `MixinRandomizableContainerBlockEntity`
   makes a managed container look empty to `isEmpty/getItem/removeItem/
   removeItemNoUpdate/setItem`, because vanilla's versions unpack the loot table
   and would otherwise let a hopper, comparator or break-drop turn it into one
   shared chest. Side effect: hoppers see nothing in a loot chest and
   comparators read 0. Config: `protect_containers` (default true; creative can
   still break).
6d. **Admin quality-of-life commands** - **Written, not yet booted.**
   `/lootr frame mark|unmark <target>` (both loaders) replaces the long `/tag`
   command for frames in pre-existing worlds and applies the shared eligibility
   rules. `/lootr reload` (Fabric only) re-reads the JSON config, since Fabric has
   no config-reload event; NeoForge reloads its TOML by itself.
6c. **Entity and pot damage protection** - **Written, not yet booted.**
   `ContainerProtection.isManagedEntity` (marked non-empty frame, or chest
   minecart with an enabled loot table) feeds NeoForge's
   `EntityInvulnerabilityCheckEvent` and Fabric's `MixinEntity` (hook at the head
   of `Entity.isInvulnerableTo`); `isManaged` now also recognises decorated pots
   and `MixinDecoratedPotBlock` cancels the projectile shatter. Creative players'
   hits are let through via `DamageSource.isCreativePlayer`. Gated on
   `protect_containers`.
7. Reactive mixin work throughout, driven by actually-observed vanilla
   behavior gaps rather than pre-emptive porting (§5).

## 9. What was actually verified vs. designed-but-unverified

The authoring sandbox has no JDK and no network, so **nothing here was ever
compiled by the author of the code**; every claim below is either from a real
run on the owner's machine, or from reading vanilla / loader sources.

**Confirmed by real runs (owner's machine):**
- `.\gradlew clean build` succeeds for both loaders.
- A dedicated server boots to "Done" on NeoForge and on Fabric on the feature
  set through commands and loot-table filters.
- `fabric-registry-sync-v0` is absent from the Fabric mod list.
- The custom-`CriterionTrigger` crash (`Registry is already frozen`) is fixed by
  using `minecraft:impossible`.
- A light playtest passed; which features it covered was not recorded.

**Checked against real 1.21.1 / loader sources, not yet run:**
- `Entity.isInvulnerableTo(DamageSource)` is declared only in `Entity`, and
  `ItemFrame.hurt`, `VehicleEntity.hurt`, `BlockAttachedEntity.hurt` all call it
  first (basis for `MixinEntity` and the NeoForge event).
- `DecoratedPotBlock.onProjectileHit(Level, BlockState, BlockHitResult, Projectile)`
  exists once (basis for `MixinDecoratedPotBlock`).
- `DamageSource.isCreativePlayer()`, `MinecartChest.getLootTable()` and the
  vehicle/entity APIs used by the minecart handlers.
- Every `net.minecraft` class the protection code imports resolves in the mapped
  1.21.1 jar.

**Unconfirmed - needs a playtest or a boot:**
- `MixinEntity` and `MixinDecoratedPotBlock` apply (boot both loaders first).
- `/lootr frame mark|unmark` and `/lootr reload` register and behave (added after
  the last real boot; `Entity.removeTag` was checked in the 1.21.1 sources).
- Structure item frames are actually marked in a freshly generated structure /
  End City, and `MixinStructureTemplate` applies in a real run.
- Client-side prediction for pot breaks and frame hits is reverted cleanly;
  sherd retention after a cancelled pot break.
- In-play behaviour of commands, team loot, dimension lists, the refresh timer,
  per-player brushing, and the pot/minecart/frame damage protection.

## 10. Item frames: design decisions and open questions

**Mechanic (matches upstream).** Upstream's Lootr frame is a normal frame with
one hidden item; the player action is *hitting* it, which drops that player's
own copy. Right-click only rotates; inserting is blocked. Its placeholder loot
table (`lootr:entity/item_frame_empty`) is empty and its type reports
`canRefresh()`/`canDecay()` as false. The server-only port keeps all of that.

**The core problem: nothing marks a frame as "Lootr".** Every other container
is recognised by the loot table vanilla set on it. A frame has none. The two
obvious shortcuts are both wrong:

| Shortcut | Why it fails |
|---|---|
| Every frame holding an item is a Lootr frame | Any player could farm duplicates from any other player's display frames, one hit each. |
| Every frame that newly joins the level | `EntityJoinLevelEvent` cannot distinguish a structure spawning a frame from a player placing one. |

So the port is **opt-in by marker**: a vanilla entity tag
(`lootr_serveronly.loot_frame`), which saves with the entity, needs no registry
content, and can be set with `/lootr frame mark`, `/tag` or a datapack function.

**Decision: structure frames are marked automatically by two mixins**
(implemented; not yet confirmed in a real run). Upstream mixes into
`StructureTemplate` (structure item frames) and
`EndCityPieces.EndCityPiece.handleDataMarker` (the End City elytra) and
*replaces* the frame with its own entity type, which a vanilla client cannot
render. This port hooks the same two places but only **adds the tag** to the
vanilla frame, so it stays server-only. Options considered:

1. *Leave it manual.* Zero risk, but the feature is inert for anyone who just
   installs the mod.
2. **A mixin at those two sites that tags instead of swapping (chosen).**
3. *A datapack function or command block* per generated structure. No mixin,
   but needs per-structure work and misses future structures.

Upstream's exclusions are kept (fixed, invisible, empty, and map frames).
Frames in already-generated worlds, and anything placed by a structure block,
`/place` or a schematic printer, are deliberately not marked - only world
generation is - so those need the manual tag.

**Lesson recorded: do not name a compiler-indexed lambda.** The first version
copied upstream's `lambda$addEntitiesToWorld$5`. It failed to apply on a real
**Fabric** run (the NeoForge half of that run started cleanly, so the old
NeoForge target was never shown to fail or to work). A lambda's name is
compiler-assigned and differs per environment: in Loom's dev runtime lambdas
keep intermediary names (`method_41041` appears in the stack trace), and on a
real Fabric server upstream needs the intermediary name `method_17917`. The
current version wraps the direct `createEntityIgnoreException` call in the named
method instead, which exists in both the NeoForge-patched code
(`addEntitiesToWorld`) and vanilla (`placeEntities`, used by Fabric).

**Damage protection (resolved in code; not yet booted).** Upstream's frame
is invulnerable to every damage source. NeoForge matches this with
`EntityInvulnerabilityCheckEvent`; Fabric has no equivalent callback, so
`MixinEntity` injects at the head of `Entity.isInvulnerableTo`. Checked against
the 1.21.1 sources: that method is declared only in `Entity`, and
`ItemFrame.hurt`, `VehicleEntity.hurt` and `BlockAttachedEntity.hurt` all call it
before doing anything, so one hook covers every non-creative damage source. The
shared predicate is `ContainerProtection.isManagedEntity` (marked non-empty frame,
or `MinecartChest` with an enabled loot table), so chest minecarts are protected
too. Decorated pots were a separate gap: `isManaged` only recognised
`RandomizableContainerBlockEntity` and brushables, and a pot is a plain
`BlockEntity`, so explosions destroyed loot pots. Pots are now recognised, and
`MixinDecoratedPotBlock` cancels vanilla's projectile-shatter
(`DecoratedPotBlock.onProjectileHit`). Residual gap: block removal that
doesn't go through the break or explosion paths (e.g. mob griefing that removes
blocks directly, `/setblock`, other mods).

**Maps.** Upstream cannot support framed maps (its own code logs an error and
refuses). Automatic marking skips map frames, and so does `/lootr frame mark`
(both use `ItemFrameMarker.ineligibleReason`, the single home of the
fixed/invisible/empty/map rules). A frame tagged with the raw `/tag` command
bypasses those checks, so avoid tagging map frames that way.
