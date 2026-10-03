# Features in detail

How each protected object behaves, what protection covers, and the break rules.

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

## Loot rolling

Every chest, barrel, shulker box and chest minecart is rolled through vanilla's
own `LootTable.fill` (into a scratch inventory that is then copied into the
player's entry), so items land in random slots across the whole inventory and
stacks split to fill empty slots, like a vanilla chest. (Earlier versions rolled
a plain item list and put it into slots 0, 1, 2..., which packed everything into
the top-left corner.) One class, `LootRoller`, does this on both loaders.

**Seed.** With `randomise_seed` on (default, and upstream's default) every roll
is random: each player gets different loot, and so does each roll after
`/lootr reset` or the refresh timer. With it off, the roll is seeded with the
container's own `LootTableSeed`, so every player gets identical loot, and the
same loot again after a reset. A seed of 0 is vanilla's "no seed", so a loot
container placed without one (for example `/setblock` with a `LootTable` tag)
stays random even with the option off; structure chests normally carry a real
seed. The option also applies to pots, which hand items straight to the player
and so have no slot placement. **Suspicious sand/gravel is always random**: its
accessor mixin has no seed getter, and adding one means touching a mixin.

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
  a loot chest.** Comparators are handled separately: `MixinAbstractContainerMenu` gives them a fixed
  output of 1 (`power_comparators`, default true; 0 when false) so structure traps wired to a comparator on a
  chest do not fire as soon as the structure loads.

Creative players can still break or remove any of these. Not covered: block
removal that doesn't go through the break or explosion paths (for example mob
griefing that removes blocks directly, `/setblock`, or other mods).

### Break rules

What happens when a player breaks a loot chest, barrel, shulker box (or trapped
chest), or any other managed block. The order follows upstream Lootr's, with this
mod's own options folded in:

1. **`enable_break`**, or a fake player with `enable_fake_player_break`: the break is
   allowed, whatever the other settings say. (A "fake player" is automation from
   another mod: the loader's `FakePlayer` class, or a server player with no network
   connection.)
2. **Creative players** are never restricted unless `disable_break` is on; they go
   straight to step 5. This is this mod's long-standing behavior and the one place it
   stays more permissive than upstream.
3. **`break_to_drop_loot`** on, player **not** sneaking, chest/barrel/shulker, real
   player: the player's loot is rolled if needed and put into their inventory (overflow
   drops at their feet), the container's advancement is awarded, and the break is
   cancelled. The container stays and counts as looted for that player (or team). It
   goes into the inventory, not onto the ground as upstream does, because a dropped
   item is visible to and pickable by every other player nearby. Breaking takes its
   normal time; the loot is handed over when the break completes.
4. Then the protection rules decide:
   - **`disable_break`** on: a survival player is always refused ("Loot containers can't
     be broken"); a creative player is refused unless sneaking.
   - Otherwise **`protect_containers`** on (the default): refused, sneaking or not.
   - Otherwise **`require_sneak_to_break`** on: a non-sneaking break is refused with a
     hint, and a sneaking break destroys the container for everyone (upstream's default
     mode).
5. Otherwise the break proceeds. If **`should_drop_player_loot`** is on, the breaker's
   own loot (rolled first if needed) spills at the block before the container is
   destroyed (not for fake players).

With every option at its default the behavior is unchanged: survival players cannot
break loot containers, creative players can. Suspicious blocks and pots ignore
`break_to_drop_loot` and `should_drop_player_loot` (pots already give loot when hit; see
[What each object does](#what-each-object-does)). In both the collect and spill paths the
loot is removed from the player's entry before it is handed out, so a break that another
mod cancels afterwards cannot duplicate it.

**Difference from upstream:** upstream's default (`disable_break` off) lets a *sneaking
survival player destroy a loot chest for everyone*. Here the default is the stricter
`protect_containers = true`, so that never happens unless you turn it off.

### Explosions, spawn protection and self-support

- **Explosions.** `protect_containers` (the default) and `blast_immune` leave every loot
  container standing in any explosion. `blast_resistant` matches upstream: a managed loot
  container counts as having a blast resistance of 16 inside vanilla's own explosion ray
  calculation (`MixinExplosionDamageCalculator`), so each ray loses strength against it
  exactly as it would against an upstream Lootr block, and it also shields what is behind
  it. Whether one is destroyed therefore depends on the explosion's power and the distance
  to it, as upstream. Resistance 16 absorbs about 4.9 of a ray's strength, and a ray starts at
  up to 1.3 x the explosion's power and weakens with distance: an uncharged creeper (power 3)
  can never destroy one, TNT (power 4) only when practically touching it, and a charged
  creeper, end crystal (power 6), bed or anchor (power 5) can from a few blocks away, less
  likely the further out. (An earlier version used a flat "power 4 or less" cutoff.) Loot minecarts and marked item frames ignore explosion
  *damage* under `protect_containers` (all damage) or `blast_immune` (explosions only).
  Upstream has no entity equivalent of `blast_resistant`; this mod's own version for
  item frames and minecarts still goes by the cause (TNT or an uncharged creeper survive).
- **`bypass_spawn_protection`** (default `true`, upstream's default). Vanilla stops
  non-operators from interacting with blocks inside the spawn-protection radius, which
  would otherwise stop them opening a loot chest near spawn. With this on, the right-click
  is let through for **managed loot blocks only**, only when it is a *use* (a sneaking
  player holding something is placing a block against it, which stays blocked), and the
  world border is still respected. Breaking is a different path and is unaffected.
- **`brushables_self_support`** / **`item_frames_self_support`** (default `false`). Keep a
  loot suspicious block from falling, and a marked loot item frame from popping off the
  wall, when the block supporting it is removed.

The three mixins behind these are listed in [Mixins](DEVELOPMENT.md#mixins). Chest minecarts follow the same break rules.

## Item frames

A frame is not a container: it holds one item. Matching upstream Lootr, the
framed item *is* the loot: **hitting** a marked frame gives that player (or
team) one copy of the item, once. The frame and its item never change, so every
other player still finds it intact. Right-click is swallowed so nobody can
rotate the item or swap one in. Creative players are left alone so an admin can
remove a frame. Frames never refresh (upstream's frame type has
`canRefresh() == false`), so `refresh_value` is ignored for them.

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

**`minecraft:generate_loot`.** Vanilla's own loot-generation trigger is fired for every roll this mod performs
(`LootRoller.triggerGenerateLoot`, called right after the table lookup for containers, chest minecarts, decorated pots
and suspicious sand/gravel), so a datapack advancement using it works with Lootr containers. As upstream does, it fires
on **every** roll for a player, including the re-roll after a refresh or reset, not only the first, and even if the table
cannot be resolved. Test: add an advancement with `"trigger": "minecraft:generate_loot"` and a `loot_table` condition,
then loot a matching container and the advancement is granted.

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
