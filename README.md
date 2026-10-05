# Lootr (Server-Only)

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62b47a)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.219%2B-e68c2c)
![Fabric](https://img.shields.io/badge/Fabric-0.16.5%2B-dbd0b4)
![Side](https://img.shields.io/badge/side-server%20only-blue)
![License: MIT](https://img.shields.io/badge/license-MIT-green)

**Unique loot for every player, with nothing installed on the client.**

Every player who opens a loot chest gets their own freshly rolled loot, so nobody
races for chests and nobody loots an area dry. This is a reimplementation of
[Lootr](https://github.com/noobanidus/Lootr)'s core mechanic that runs on the server
only: **vanilla clients can join**, with no client mod, resource pack or matching
mod list.

It supports **NeoForge** and **Fabric** with the same feature set.

## Why server-only?

Lootr adds its own blocks and entities, and a vanilla client cannot connect to a
server whose registries it does not know. This project registers **no blocks,
items or block entity types at all**. It attaches per-player loot state to
vanilla chests, barrels and other objects, then opens a normal vanilla menu backed
by a container that belongs to the player who opened it. The client just sees a
chest.

## Features

| Object | What each player gets |
|---|---|
| Chest, trapped chest, barrel, shulker box | Their own loot in the normal vanilla menu |
| Chest minecart | Their own loot in a chest menu |
| Decorated pot | Their own loot straight into their inventory; the pot stays |
| Suspicious sand / gravel | Their own archaeology loot when they finish brushing; the block stays |
| Item frame | One copy of the framed item, once (frames must be marked, see [Item frames](../../wiki/Features-in-detail#item-frames)) |

Also included:

- **Team loot:** players on the same scoreboard team share one loot pool (optional).
- **Refresh and decay:** containers can reset after a timer, or vanish after being looted (both off by default).
- **Protection:** one player (or one creeper) can't destroy loot for everyone.
- **Filters:** per-dimension and per-loot-table/mod lists, plus a built-in list of tables known to misbehave.
- **Admin commands:** inspect, reset, clear, spawn, view and re-scan loot containers (`/lootr ...`), on the container you stand on or a named target.
- **Hopper and explosion handling:** hoppers can't drain loot minecarts, and `blast_resistant` follows upstream's resistance-16 rule.
- **Advancement trigger:** loot rolls fire vanilla's `minecraft:generate_loot`, so datapack advancements keyed on it work.
- **Advancements:** first-use advancements for each container type.
- **Add-on API:** loot filters, listeners and team resolvers for other server-side mods.
- **Config parity:** key names and defaults follow upstream Lootr, so an upstream config copies over largely as-is.

## Installation

**Server only.** Players need nothing.

1. Install Minecraft **1.21.1** with either **NeoForge 21.1.219+** or **Fabric Loader 0.16.5+** (Java 21).
2. Put the matching jar in the server's `mods/` folder:
   - `lootr_serveronly-neoforge-<version>.jar` for NeoForge
   - `lootr_serveronly-fabric-<version>.jar` for Fabric (not the `-dev` or `-sources` jars)
3. Start the server. A config file is created on first run.

Fabric API does not need to be installed: the few modules this mod uses are bundled
in its jar, and it deliberately avoids `fabric-registry-sync-v0`, the module that
would kick vanilla clients.

Jars come from [Releases](../../releases) when one is published, or build from source (below).

### Try it

```
/lootr chest
```

Open the chest it creates, then open it again from a second account. Each account
sees different loot. (`/lootr` commands need operator level 2.)

## Configuration

| Loader | File |
|---|---|
| NeoForge | `config/lootr_serveronly-common.toml` (reloaded automatically) |
| Fabric | `config/lootr_serveronly.json` (read at startup; `/lootr reload` to apply) |

The options most servers touch:

| Option | Default | Meaning |
|---|---|---|
| `team_loot` | `false` | Teammates share one loot pool |
| `dimension_whitelist` / `dimension_blacklist` | empty | Limit which dimensions are converted |
| `loot_table_blacklist` / `mod_id_blacklist` | empty | Leave specific tables or mods' tables fully vanilla |
| `refresh_value` + `refresh_all` | `24000` / `false` | Reset looted containers after this many ticks (nothing refreshes until `refresh_all` or a list is set) |
| `decay_value` + `decay_all` | `6000` / `false` | Remove looted containers after this many ticks (same rule) |
| `randomise_seed` | `true` | `false`: everyone gets identical loot from a container |
| `protect_containers` | `true` | Survival players can't break loot containers; explosions leave them standing |
| `disable` | `false` | Master off switch; everything reverts to vanilla |

Everything else, with all keys, defaults and the exact break rules, is in
[Configuration](../../wiki/Configuration).

## Commands

| Command | Purpose |
|---|---|
| `/lootr` | Show command usage |
| `/lootr info` / `info block <pos>` / `info entity <target>` | How many players/teams have looted it, and its timers |
| `/lootr reset` / `reset block <pos>` / `reset entity <target>` | Forget everyone's loot so the next open rolls fresh |
| `/lootr id`, `refresh`, `decay` (same three forms) | Show the container's Lootr id; force a refresh or a decay now |
| `/lootr clear <players>` | Let those players loot everything again |
| `/lootr openers` (same three forms) | Everyone who has opened it (kept across refresh/reset), plus how many are viewing now |
| `/lootr frame mark\|unmark <target>` | Make an item frame a loot frame, or stop it being one |
| `/lootr chest\|barrel\|trapped_chest\|shulker\|pot\|gravel\|sand\|cart [<table>]` | Create a loot container at your position |
| `/lootr open_as <player>` / `open_as_uuid <uuid>` (optionally followed by `block <pos>` or `entity <target>`) | View what a player has left in a container, read-only |
| `/lootr force_chunk\|force_radius\|force_all` | Run the chunk discovery scan for decay/refresh on demand; also re-queues tracked containers that were waiting for their chunk to load |
| `/lootr reload` | Fabric only: re-read the config file |

Commands act on the container you are standing on (the block at your feet or the one below, or a chest minecart within a block), like upstream. The `block <pos>` and `entity <target>` forms still work but are deprecated and print a warning; they may be removed.

Not implemented: `custom-chest`, `custom-area` and `custom-map`, which depend on upstream's custom inventories (they need a custom block, so no server-only equivalent). `/lootr refresh` and `/lootr decay` are marked deprecated in upstream's wiki. They are kept here, but they behave differently: upstream starts the container's refresh or decay timer with the full configured delay, while here they act immediately (or as soon as the last viewer closes the menu). They still require the refresh or decay setting to cover that container.

Full details in [Commands](../../wiki/Commands).

## Known limitations

- **Shared visuals.** Two players opening the same chest see each other's lid animation and hear the same sounds, as with a vanilla double chest. A server-only mod cannot split this per player.
- **Loot containers are detected by loot table.** Any vanilla container with an assigned, non-filtered loot table is treated as loot.
- **Item frames must be marked.** Structures generated after install are marked automatically; frames in existing worlds, or placed by structure blocks or `/place`, need `/lootr frame mark`.
- **Hoppers see nothing** in a loot container and can't insert into one.
- **English only.** A vanilla client has no language file for this mod, so text is literal.
- **Not ported:** client-side extras (particles, JEI/Jade integration, custom textures) and upstream's conversion-queue settings, which don't apply because nothing is converted.

## Project status

Both loaders build and boot a dedicated server, and a light playtest passed.
Features added since then (background refresh and decay sweeps with chunk-aware scheduling,
add-on API, spawn, `open_as` and `force_*` commands, standing-on command forms, the
hopper and explosion mixins, the `generate_loot` trigger, minecart break rules, and others)
are written and checked against the 1.21.1 sources but have had limited in-game testing. Bug
reports are welcome. See
[Verification status](../../wiki/Development-notes#verification-status) for the full list
and a quick test for each.

## Building from source

Requires JDK 21.

```
.\gradlew clean build              # both loaders (./gradlew on Linux/macOS)
.\gradlew :neoforge:runServer      # dev server, NeoForge
.\gradlew :fabric:runServer        # dev server, Fabric
```

Run one dev server at a time (both bind port 25565); the Fabric dev server stops
on first run until you accept `eula.txt` in its `run/` folder.

Jars are written to `neoforge/build/libs/` and `fabric/build/libs/`.

The project has three modules: `common` (loader-independent logic: commands, loot state, config settings,
interaction handlers), `neoforge` and `fabric` (registration, config and mixins per loader).

## Documentation

Full docs are in the [wiki](../../wiki):

| Document | Contents |
|---|---|
| [Features in detail](../../wiki/Features-in-detail) | How each object behaves, protection, break rules, item frames, advancements |
| [Configuration](../../wiki/Configuration) | Every config key with defaults |
| [Commands](../../wiki/Commands) | Every `/lootr` command |
| [Add-on API](../../wiki/Add-on-API) | Loot filters, listeners and the `LootrAPI` facade |
| [Development notes](../../wiki/Development-notes) | Project layout, mixins, verification status, implementation notes |

## Credits and license

Based on the mechanic of [Lootr](https://github.com/noobanidus/Lootr) by
noobanidus. This is an independent reimplementation and is not affiliated with or
endorsed by Lootr's authors.

Released under the [MIT License](LICENSE.txt).
