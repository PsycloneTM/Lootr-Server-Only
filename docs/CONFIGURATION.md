# Configuration

## Configuration

NeoForge: `config/lootr_serveronly-common.toml` (sections shown, picked up on
config reload). Fabric: `config/lootr_serveronly.json` with the same keys
(read at startup; restart or run `/lootr reload` to apply; a malformed file is copied to `.bak` before
defaults are written back).

| Key (NeoForge section) | Default | Meaning |
|---|---|---|
| `disable` (`general`) | `false` | Master switch: nothing is converted; every container, pot, minecart and frame is vanilla and nothing is protected. Already-taken loot is kept for when it is turned off again |
| `team_loot` (`team`) | `false` | Players on the same scoreboard team share one loot pool; teamless players are a team of one |
| `dimension_whitelist` (`dimensions`) | empty | If non-empty, only these dimensions are converted |
| `dimension_blacklist` (`dimensions`) | empty | Never converted; wins over the whitelist |
| `modid_dimension_whitelist` / `modid_dimension_blacklist` (`dimensions`) | empty | Dimension **namespaces**, e.g. `minecraft` covers overworld, nether and end, `somemod` covers every `somemod:...` dimension. Checked before the two lists above; a listed blacklist namespace always wins, a non-empty whitelist excludes every other namespace (same rule as upstream) |
| `loot_table_blacklist` (`loot_filters`) | empty | Exact loot table ids left fully vanilla |
| `mod_id_blacklist` (`loot_filters`) | empty | Whole namespaces left fully vanilla |
| `loot_modid_blacklist` (`loot_filters`) | empty | Same list under upstream Lootr's own name, so an upstream config copies over as-is. Both lists are merged; the old name keeps working |
| `refresh_value` (`refresh`) | `24000` | Ticks after a container is first looted before it resets for everyone (renamed from `refresh_ticks`; 0 = off). Needs `refresh_all` or a list, so with defaults nothing refreshes. Checked when someone opens it, not in the background |
| `refresh_all` (`refresh`) | `false` | `true`: refresh applies to every converted container/pot/suspicious block. `false`: only tables covered by the two lists below |
| `refresh_loot_tables` / `refresh_modids` (`refresh`) | empty | Exact table ids / namespaces that refresh when `refresh_all` is `false` |
| `refresh_dimensions` (`refresh`) | empty | Containers in these dimensions refresh, in addition to `refresh_all` / the lists / tagged structures (upstream semantics) |
| `decay_value` / `decay_all` / `decay_loot_tables` / `decay_modids` (`decay`) | `6000` / `false` / empty / empty | See [Decay](DEVELOPMENT.md#decay---not-build-verified). Like refresh, nothing decays by default |
| `decay_dimensions` (`decay`) | empty | Containers in these dimensions decay, in addition to `decay_all` / the lists / tagged structures (upstream semantics) |
| `notification_delay` (`notifications`) | `600` | The decay timer is announced on open only once this many ticks or fewer remain; the "decay has started" message is always sent; `-1` = always announce |
| `disable_message_styles` (`notifications`) | `false` | Plain text instead of coloured/bold break, decay and invalid-table messages |
| `pinned_team_resolver` (`team`) | empty | Id of the team resolver to use with `team_loot`. Built-in: `minecraft:vanilla_default` (upstream's id; the older `lootr_serveronly:scoreboard` is still accepted). Empty = the highest-priority resolver wins. See [Team resolvers](DEVELOPMENT.md#team-resolvers---not-build-verified) |
| `convert_elytras_to_chests` (`item_frames`) | `false` | End City Elytra frame becomes a chest with a guaranteed Elytra (loots per player). Frames take priority: off while `convert_elytras_to_item_frames` is on, as upstream |
| `check_world_border` (`performance`) | `false` | Background sweeps and chunk discovery skip containers outside the world border |
| `perform_piecewise_check` (`performance`) | `true` | Structure-tag refresh/decay also tests each structure piece, plus the desert-pyramid pit box (as upstream) |
| `replace_when_decayed` (`decay`) | `false` | A decayed container stays as an ordinary empty vanilla container (loot table cleared) instead of vanishing; minecarts likewise |
| `perform_decay_while_ticking` (`decay`) | `true` | `false`: no background sweep; containers only decay when someone next opens them past the deadline |
| `start_decay_while_ticking` (`decay`) | `false` | `true`: containers already looted but not yet watched for decay are picked up when their chunk loads (otherwise a container starts being watched when someone next opens it) |
| `tick_delay` (`decay`) | `20` | Ticks between background decay **and refresh** sweeps (1-12000) |
| `perform_refresh_while_ticking` (`refresh`) | `true` | `false`: no background refresh; containers only refresh when someone next opens them |
| `start_refresh_while_ticking` (`refresh`) | `true` | `true`: containers already looted but not yet watched for refresh are picked up when their chunk loads |
| `problematic_loot_tables` (`loot_filters`) | empty | Your own list of tables that misbehave when converted; treated like the blacklist, but `loot_table_forced_whitelist` overrides them. **Two are built in, as in upstream**: `twilightforest:structures/stronghold_boss` and `atum:chests/pharaoh` (force-whitelist one to convert it anyway). Other server mods add more; see [Problematic and unresolved loot tables](DEVELOPMENT.md#problematic-and-unresolved-loot-tables---not-build-verified) |
| `report_unresolved_tables` (`loot_filters`) | `false` | Tables a container names but the server cannot find are always logged once; `true` also tells the player who opened it |
| `loot_table_forced_whitelist` (`loot_filters`) | empty | Table ids always converted, overriding `loot_table_blacklist` and `mod_id_blacklist` (not the dimension lists) |
| `power_comparators` (`redstone`) | `true` | A comparator on a loot container outputs 1 (false: 0). See [Mixins](DEVELOPMENT.md#mixins) |
| `disable_notifications` (`notifications`) | `false` | No chat message about a container's decay timer |
| `randomise_seed` (`loot`) | `true` | `true`: every roll is random, so each player and each roll after a reset gets different loot. `false`: rolls use the container's own loot seed, so everyone gets the same loot, and the same again after a reset. See [Loot rolling](FEATURES.md#loot-rolling) |
| `convert_item_frames` (`item_frames`) | `true` | Mark frames that structures spawn |
| `convert_elytras_to_item_frames` (`item_frames`) | `true` | Mark the End City ship's Elytra frame |
| `protect_containers` (`protection`) | `true` | See [Protection](FEATURES.md#protection) |
| `break_to_drop_loot` (`protection`) | `false` | A non-sneaking survival break collects your loot instead of breaking the block (see [Break rules](FEATURES.md#break-rules)) |
| `require_sneak_to_break` (`protection`) | `false` | Only when `protect_containers` is off: survival players must sneak to break |
| `should_drop_player_loot` (`protection`) | `false` | When a container really is destroyed by a player, their own loot spills at the block |
| `enable_break` (`protection`) | `false` | Anyone may break loot containers; overrides every other break setting (see [Break rules](FEATURES.md#break-rules)) |
| `disable_break` (`protection`) | `false` | Upstream's strict mode: survival never breaks, creative only while sneaking |
| `enable_fake_player_break` (`protection`) | `false` | Fake players (automation from other mods) may break loot containers |
| `blast_resistant` (`protection`) | `false` | Loot containers get a blast resistance of 16 (upstream's rule), so only a strong, close explosion destroys one. See [Explosions, spawn protection and self-support](FEATURES.md#explosions-spawn-protection-and-self-support) |
| `blast_immune` (`protection`) | `false` | No explosion destroys a loot container; loot minecarts and marked item frames ignore explosion damage |
| `bypass_spawn_protection` (`protection`) | `true` | Players can **use** loot containers inside the server's spawn protection (never place or break) |
| `brushables_self_support` (`protection`) | `false` | Loot suspicious sand/gravel does not fall when the block under it is removed |
| `item_frames_self_support` (`protection`) | `false` | Marked loot item frames are not popped off when their support block is removed |

Also: `/lootr cclear <players>` is an alias of `/lootr clear`. `loot_table_forced_whitelist` now exempts a table from the table blacklist and the problematic set only; `mod_id_blacklist` still applies (upstream precedence).

A blacklisted loot table is left completely vanilla: no per-player loot, no
break protection, and vanilla resolves it as usual. Every place that decides "is
this a loot container" goes through `ModLootTags.isTableEnabled`, so the filter
applies uniformly to containers, minecarts, pots and brushable blocks.
