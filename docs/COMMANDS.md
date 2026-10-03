# Commands

## Commands

Operators (permission level 2). Targets are explicit so the commands work from a
command block or the console:

- `/lootr info block <pos>` and `/lootr info entity <target>`: how many
  players/teams have looted it, and the refresh timer.
- `/lootr reset block <pos>` and `/lootr reset entity <target>`: forget
  everyone's loot so the next open by anyone rolls fresh loot.
- `/lootr clear <players>`: forget everything those players have looted, so they can
  loot every container, pot, suspicious block and item frame again. It is **lazy**
  (see [implementation notes](DEVELOPMENT.md#lootr-clear-lootr-openers-refresh-filters-disable---not-build-verified)). With `team_loot` on it
  clears the whole team's shared record.
- `/lootr openers block <pos>` and `/lootr openers entity <target>`: which players
  have a loot menu open on it right now.
- `/lootr frame mark <target>` and `/lootr frame unmark <target>`: make one item
  frame a loot frame, or stop it being one (see [Item frames](FEATURES.md#item-frames)).
  `mark` refuses fixed, invisible, empty and map frames. `unmark` does not clear
  who has already taken a copy; use `reset` for that.
- `/lootr chest|barrel|trapped_chest|shulker|pot|gravel|sand|cart [<table>]`: create that
  kind of loot container at the command's position (your feet, from a player). It is a plain
  vanilla block or chest minecart with a vanilla loot table, so it behaves exactly like a
  structure-generated one. Without a table one is picked at random from the tables that suit
  the kind and that your loot-table filters leave managed (`chests/*`; `pots/*` as well for
  pots; `archaeology/*` for suspicious sand and gravel). A table the filters exclude, or that
  does not exist, is refused; in a dimension Lootr is disabled for it is created with a
  warning that it will not be per-player. The position must be empty. Tab-completion lists the
  suitable tables.
- `/lootr force_chunk`, `/lootr force_radius <radius>` (1 to 16) and `/lootr force_all`: run the
  chunk-load discovery scan on demand, so already-looted containers join the decay and refresh
  trackers even with `start_decay_while_ticking` / `start_refresh_while_ticking` off. They need
  `decay_value` or `refresh_value` above 0 and only look at chunks that are already fully
  loaded; nothing is loaded or generated. `force_all` covers the chunks within view distance of
  each online player plus `/forceload`ed chunks, in every dimension Lootr is enabled for. The
  reply says how many containers were newly added to each tracker. Safe to run twice.
- `/lootr open_as <player> block <pos>|entity <target>` and
  `/lootr open_as_uuid <uuid> block <pos>|entity <target>`: open a **read-only copy** of what
  that player (or loot key) has left in a chest, trapped chest, barrel, shulker box or chest
  minecart. You can't take or move anything, so the player's real entry never changes. It never
  rolls loot: if they have not looted it, it says so, and nothing is credited to anyone
  (no advancement, `generate_loot` trigger or stat). With `team_loot` on, the entry is the
  team's; for an offline player's team that can't be resolved, so use it while they are online.
- `/lootr reload` (**Fabric only**): re-read `config/lootr_serveronly.json`
  without a restart. NeoForge picks up config file changes by itself. A malformed
  file is backed up to `.bak` and replaced with defaults, exactly as at startup.

The spawn, `force_*` and `open_as*` commands are not build-verified (see [Verification status](DEVELOPMENT.md#verification-status)).

Entities cover chest minecarts and item frames, e.g.
`/lootr reset entity @e[type=minecraft:chest_minecart,limit=1,sort=nearest]`.
An object only has state once somebody has looted it; before that both commands
say so.
