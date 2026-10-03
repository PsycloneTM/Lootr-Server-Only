# Add-on API

For other server-side mods. All of it is optional: nothing inside this mod calls it.

## Addon API: loot filters

Other server-side mods can change the loot a player is rolled. Implement `LootFilter`
(`net.lootr.serveronly.api` on NeoForge, `net.lootr.serveronly.fabric.api` on Fabric) and call
`LootFilters.register(filter)` from your mod's initializer, or list the class (public no-argument constructor) in
`META-INF/services/<package>.LootFilter`, which is looked up once, the first time filters are needed (the server-only
counterpart of upstream's `ILootrFilterProvider` service file). A filter gets the rolled stacks as a mutable list plus
a small `Context` (level, looting player, loot table, random) and may remove, replace or add stacks; return `true`
to stop lower-priority filters (lower `priority()` runs first, ties keep registration order).

It runs for every Lootr roll (chests, trapped chests, barrels, shulker boxes, chest minecarts, decorated pots,
suspicious sand/gravel) and for nothing else. With no filter registered, rolling is unchanged. A filter that
throws is skipped for that roll and logged once. For containers the filter sees stacks *after* vanilla placed them
across slots (upstream filters before), so removals and replacements keep vanilla's layout and added stacks go to
random free slots; anything that no longer fits is dropped. This is **not** binary-compatible with upstream's
`ILootrFilter` (which takes its own `LootFillerState`); it is the server-only equivalent. Not verified by a build.

## Addon API: loot listeners

`LootListener` (same packages as `LootFilter`) is the server-only counterpart of upstream's block-entity and entity
*processors*. Upstream's processors run while a vanilla container is **converted** into a Lootr one; this mod never
converts anything (the vanilla block entity or entity stays and carries an attachment), so there is no conversion step to
hook. The two moments an add-on cares about here are events instead. Both methods have empty defaults, so implement only
what you need, and register with `LootListeners.register(listener)` or a `META-INF/services/<package>.LootListener` file.

| Method | Fires |
|---|---|
| `onLooted(level, holder, pos, looter, table)` | Once per looter (player or team) per container, when their loot is first rolled: after the per-player record is saved, before the items reach them. Every path is covered (menus, break-to-drop-loot, pots, suspicious blocks, chest minecarts). Not on a repeat open. |
| `onDecaying(level, holder, pos, table)` | Just before decay removes or resets a container, while it still exists with its loot table. |

`holder` is the vanilla `BlockEntity` (chest, trapped chest, barrel, shulker box, decorated pot, suspicious block) or the
`MinecartChest` involved. Listeners run on the server thread inside the action that caused them, in ascending
`priority()`; one that throws is skipped for that event and logged once, and never stops the player getting loot. With
none registered the calls are a cheap no-op. **Not provided:** a refresh event, upstream's data/item-frame adapters
(they teach upstream about other mods' container classes, and there is no vanilla object to attach them to here), and
anything that replaces or vetoes the open itself. Not verified by a build.

## Addon API: `LootrAPI` facade

One class for add-ons to import: `LootrAPI` (same packages as `LootFilter`). It is **read-mostly and optional**:
nothing inside this mod calls it, it only wraps what the mod already decides, so the answers are the ones the mod
acts on and removing it cannot change gameplay. Call it from the server thread.

| Method | Answers |
|---|---|
| `isLootrContainer(BlockEntity)` / `(Entity)` | Is this a container this mod manages right now (table converted **and** dimension enabled)? Block entities: chests, trapped chests, barrels, shulker boxes, decorated pots, suspicious blocks. Entities: chest minecarts, marked item frames holding an item |
| `getLootKey(Player)` | The key their loot is stored under (own UUID, or the shared team UUID from the active team resolver) |
| `hasLooted(BlockEntity \| Entity, Player)` | Has this player/team already looted it? `false` if there is no loot state |
| `getLooterCount(BlockEntity \| Entity)` | How many players/teams have looted it (records wiped by `/lootr clear` are not counted) |
| `getTicksUntilDecay(...)` / `getTicksUntilRefresh(...)` | `OptionalLong`: ticks left, `0` = due now, empty = no timer applies (feature off or not covering it, nobody has looted it, or not a chest/barrel/shulker box/chest minecart) |
| `clearLootRecords(ServerPlayer)` | Same as `/lootr clear <player>` |
| `registerFilter` / `registerListener` / `registerTeamResolver` / `registerProblematicLootTableProcessor` | One-line shortcuts to `LootFilters`, `LootListeners`, `TeamResolvers` and `ProblematicLootTables`, so an add-on imports one class |

**Not upstream-binary-compatible.** Names follow upstream's `LootrAPI` where the idea maps over, but upstream's
signatures belong to its custom block and entity types, which a server-only mod does not have; a mod written against
upstream's API has to be adapted, not just recompiled. **Still not provided:** container/entity processors and data
or item-frame adapters (upstream's way of teaching Lootr about other mods' block entities - there is no vanilla
object to attach them to here), and a generic service registry. On Fabric the query methods load a throwaway copy of
the state from the saved tag (never written back), so they cost a little more than on NeoForge. Type-checked against
stubs only; not verified by a build.
