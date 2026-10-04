package net.lootr.serveronly.fabric.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.interaction.Decay;
import net.lootr.serveronly.fabric.interaction.ContainerInteractionHandler;
import net.lootr.serveronly.fabric.interaction.Refresh;
import net.lootr.serveronly.fabric.interaction.ItemFrameVisualSync;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.registry.ItemFrameMarker;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.PlayerClears;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.lootr.serveronly.mixin.AccessorBrushableBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fabric twin of the NeoForge {@code LootrCommands}; see that class for the
 * command list and behavior ({@code /lootr info|reset block|entity} and
 * {@code /lootr frame mark|unmark}, permission level 2; the spawn, {@code force_*} and {@code open_as*} commands are
 * in {@link AdminCommands}). Fabric-only extra:
 * {@code /lootr reload} re-reads the JSON config.
 * <p>
 * Differences: registration goes through Fabric API's
 * {@link CommandRegistrationCallback} (module {@code fabric-command-api-v2}),
 * and the attachment is a raw {@link CompoundTag}, so state is loaded into a
 * {@link LootrLootState}, mutated, and written back by hand, exactly as the
 * interaction handlers do. An object with no loot state has an empty tag (the
 * attachment's initializer), since anything Lootr has ever saved contains at
 * least a container id.
 */
public final class LootrCommands {

    /** Read/write access to one block entity's or entity's attachment. */
    private record Target(Supplier<CompoundTag> read, Consumer<CompoundTag> write, Object holder) {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("lootr")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("info")
                                .then(Commands.literal("block")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> info(ctx.getSource(), blockAt(ctx), "that block"))))
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(ctx -> info(ctx.getSource(),
                                                        of(EntityArgument.getEntity(ctx, "target")), "that entity")))))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("block")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> reset(ctx.getSource(), blockAt(ctx), "that block"))))
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(ctx -> {
                                                    Entity entity = EntityArgument.getEntity(ctx, "target");
                                                    return reset(ctx.getSource(), of(entity), "that entity", entity);
                                                }))))
                        .then(Commands.literal("refresh")
                                .then(Commands.literal("block")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> refreshNow(ctx.getSource(), blockAt(ctx), "that block"))))
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(ctx -> {
                                                    Entity entity = EntityArgument.getEntity(ctx, "target");
                                                    return refreshNow(ctx.getSource(), of(entity), "that entity");
                                                }))))
                        .then(Commands.literal("decay")
                                .then(Commands.literal("block")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> decayNow(ctx.getSource(), blockAt(ctx), "that block"))))
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(ctx -> {
                                                    Entity entity = EntityArgument.getEntity(ctx, "target");
                                                    return decayNow(ctx.getSource(), of(entity), "that entity");
                                                }))))
                        .then(Commands.literal("id")
                                .then(Commands.literal("block")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> idOf(ctx.getSource(), blockAt(ctx), "that block"))))
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(ctx -> idOf(ctx.getSource(), of(EntityArgument.getEntity(ctx, "target")), "that entity")))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> clear(ctx.getSource(), EntityArgument.getPlayers(ctx, "players")))))
                        .then(Commands.literal("cclear")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> clear(ctx.getSource(), EntityArgument.getPlayers(ctx, "players")))))
                        .then(Commands.literal("openers")
                                .then(Commands.literal("block")
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> {
                                                    Target target = blockAt(ctx);
                                                    return openers(ctx.getSource(), ctx.getSource().getLevel(),
                                                            target == null ? null : target.holder(), "that block");
                                                })))
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(ctx -> {
                                                    Entity entity = EntityArgument.getEntity(ctx, "target");
                                                    return openers(ctx.getSource(), (ServerLevel) entity.level(), entity, "that entity");
                                                }))))
                        .then(Commands.literal("frame")
                                .then(Commands.literal("mark")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(ctx -> markFrame(ctx.getSource(),
                                                        EntityArgument.getEntity(ctx, "target"), true))))
                                .then(Commands.literal("unmark")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(ctx -> markFrame(ctx.getSource(),
                                                        EntityArgument.getEntity(ctx, "target"), false)))))
                        // Fabric has no config-reload event, so config is read at startup.
                        // This re-reads the file without a restart.
                        .then(Commands.literal("reload")
                                .executes(ctx -> reload(ctx.getSource())));
                // chest|barrel|... spawn commands, force_chunk|force_radius|force_all, open_as|open_as_uuid
                AdminCommands.addTo(root);
                dispatcher.register(root);
        });
    }

    /** Forces the shared refresh timer due, then executes the same refresh operation used by normal opens/ticks. */
    private static int refreshNow(CommandSourceStack source, @Nullable Target target, String what) {
        LootrLootState state = loadState(source, target, what);
        if (state == null) return 0;
        Object holder = target.holder();
        ServerLevel level = source.getLevel();
        long ticks = refreshTicksOf(holder);
        if (ticks <= 0) {
            source.sendFailure(Component.literal("Refresh is not enabled for " + what + "."));
            return 0;
        }
        boolean refreshed;
        if (holder instanceof RandomizableContainerBlockEntity block && ContainerInteractionHandler.kindOf(block) != null) {
            state.setFirstGeneratedGameTime(level.getGameTime() - ticks);
            refreshed = Refresh.refreshIfDue(level, block, state, ticks);
        } else if (holder instanceof MinecartChest cart) {
            state.setFirstGeneratedGameTime(level.getGameTime() - ticks);
            refreshed = Refresh.refreshIfDue(level, cart, state, ticks);
        } else if (holder instanceof DecoratedPotBlockEntity || holder instanceof BrushableBlockEntity) {
            state.setFirstGeneratedGameTime(level.getGameTime() - ticks);
            refreshed = state.refreshIfDue(level.getGameTime(), ticks);
        } else {
            source.sendFailure(Component.literal("That object is not a supported Lootr refresh target."));
            return 0;
        }
        CompoundTag saved = new CompoundTag();
        state.save(saved, level.registryAccess());
        target.write().accept(saved);
        if (refreshed) {
            Refresh.notifyRefreshed(level, holder);
        }
        source.sendSuccess(() -> Component.literal(refreshed
                ? "Refreshed " + what + "."
                : "Someone has " + what + " open; it will refresh as soon as it is closed."), true);
        return refreshed ? 1 : 0;
    }

    /** Forces the shared decay timer due, then executes the normal decay operation. */
    private static int decayNow(CommandSourceStack source, @Nullable Target target, String what) {
        LootrLootState state = loadState(source, target, what);
        if (state == null) return 0;
        Object holder = target.holder();
        ServerLevel level = source.getLevel();
        BlockPos pos;
        ResourceKey<LootTable> table;
        if (holder instanceof RandomizableContainerBlockEntity block && ContainerInteractionHandler.kindOf(block) != null) {
            pos = block.getBlockPos();
            table = block.getLootTable();
        } else if (holder instanceof MinecartChest cart) {
            pos = cart.blockPosition();
            table = cart.getLootTable();
        } else {
            source.sendFailure(Component.literal("Only chests, trapped chests, barrels, shulker boxes and chest minecarts decay."));
            return 0;
        }
        if (!Decay.covers(level, pos, table) || LootrConfig.decayTicks() <= 0) {
            source.sendFailure(Component.literal("Decay is not enabled for " + what
                    + " (see decay_value, decay_all and the decay lists and tags)."));
            return 0;
        }
        state.setFirstGeneratedGameTime(level.getGameTime() - LootrConfig.decayTicks());
        boolean decayed;
        if (holder instanceof RandomizableContainerBlockEntity chestLike) {
            Decay.track(level, chestLike);
            decayed = Decay.decayIfDue(level, chestLike, state);
        } else {
            decayed = Decay.decayIfDue(level, (MinecartChest) holder, state);
        }
        CompoundTag saved = new CompoundTag();
        state.save(saved, level.registryAccess());
        target.write().accept(saved);
        source.sendSuccess(() -> Component.literal(decayed
                ? "Decayed " + what + "."
                : "Someone has " + what + " open; it will decay as soon as it is closed."), true);
        return decayed ? 1 : 0;
    }

    /** Returns the stable state UUID assigned to this Lootr object. */
    private static int idOf(CommandSourceStack source, @Nullable Target target, String what) {
        LootrLootState state = loadState(source, target, what);
        if (state == null) return 0;
        UUID id = state.getContainerId();
        CompoundTag saved = new CompoundTag();
        state.save(saved, source.getLevel().registryAccess());
        target.write().accept(saved);
        source.sendSuccess(() -> Component.literal("Lootr id of " + what + ": " + id), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        LootrConfig.load();
        source.sendSuccess(() -> Component.literal("Reloaded lootr_serveronly.json."), true);
        return 1;
    }

    /**
     * {@code /lootr clear <players>}: forget everything these players have looted, so they can loot every
     * container, pot, suspicious block and item frame again. Lazy: see {@link PlayerClears} for why it is
     * one increment per loot key rather than a walk over every container in the world. Both the player's
     * own key and their team's key are cleared, because which one is in use depends on {@code team_loot}
     * (so with team loot on, a teammate's records are cleared too).
     */
    private static int clear(CommandSourceStack source, Collection<ServerPlayer> players) {
        PlayerClears clears = PlayerClears.get(source.getServer());
        Set<UUID> keys = new LinkedHashSet<>();
        for (ServerPlayer player : players) {
            keys.add(TeamResolver.resolve(player));
            keys.add(player.getUUID());
        }
        for (UUID key : keys) {
            clears.clear(key);
        }
        for (ServerPlayer player : players) {
            refreshFrames(player);
        }
        String who = players.size() == 1 ? players.iterator().next().getName().getString() : players.size() + " players";
        boolean team = LootrConfig.teamLoot();
        source.sendSuccess(() -> Component.literal("Cleared the loot records of " + who
                + ": they can loot every container, pot, suspicious block and item frame again."
                + (team ? " Team loot is on, so this also clears the records they share with their team." : "")), true);
        return players.size();
    }

    /**
     * A cleared player's client still shows loot frames they had taken as empty. Re-send the real item for
     * the marked frames they can currently see, so they look lootable again without relogging.
     */
    private static void refreshFrames(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, player.getBoundingBox().inflate(160.0),
                ItemFrameMarker::isMarked)) {
            ItemFrameVisualSync.sendVisibleItem(player, frame);
        }
    }

    /**
     * {@code /lootr openers block|entity}: the players who have a loot menu open on this container right
     * now. Reads the open menus themselves (see {@code Decay.viewers}), so it works for minecarts too and
     * is exact: nothing to go stale if a player logs out or their menu is closed by another mod.
     */
    private static int openers(CommandSourceStack source, ServerLevel level, @Nullable Object owner, String what) {
        if (owner == null) {
            source.sendFailure(Component.literal("There is no block entity at that position."));
            return 0;
        }
        List<ServerPlayer> viewers = Decay.viewers(level, owner);
        if (viewers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nobody has " + what + " open."), false);
            return 0;
        }
        List<String> names = new ArrayList<>();
        for (ServerPlayer viewer : viewers) {
            names.add(viewer.getName().getString());
        }
        source.sendSuccess(() -> Component.literal(viewers.size() + " player(s) have " + what + " open: "
                + String.join(", ", names) + "."), false);
        return viewers.size();
    }

    /** See the NeoForge twin; identical behavior. */
    private static int markFrame(CommandSourceStack source, Entity target, boolean mark) {
        if (!(target instanceof ItemFrame frame)) {
            source.sendFailure(Component.literal("That entity is not an item frame."));
            return 0;
        }
        boolean marked = ItemFrameMarker.isMarked(frame);
        if (!mark) {
            if (!marked) {
                source.sendFailure(Component.literal("That item frame is not a loot frame."));
                return 0;
            }
            ItemFrameMarker.unmark(frame);
            source.sendSuccess(() -> Component.literal("That item frame is no longer a loot frame."), true);
            return 1;
        }
        if (marked) {
            source.sendFailure(Component.literal("That item frame is already a loot frame."));
            return 0;
        }
        String reason = ItemFrameMarker.ineligibleReason(frame);
        if (reason != null) {
            source.sendFailure(Component.literal("Can't make that a loot frame: " + reason + "."));
            return 0;
        }
        ItemFrameMarker.mark(frame);
        source.sendSuccess(() -> Component.literal("That item frame is now a loot frame."), true);
        return 1;
    }

    /** The block entity at the given (loaded) position as a Target, or null if the block has none. */
    @Nullable
    private static Target blockAt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockEntity be = ctx.getSource().getLevel().getBlockEntity(BlockPosArgument.getLoadedBlockPos(ctx, "pos"));
        return be == null ? null : of(be);
    }

    private static Target of(BlockEntity be) {
        return new Target(
                () -> be.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag()),
                tag -> {
                    be.setAttached(ModAttachments.LOOT_STATE, tag);
                    // Block entities only save when marked dirty.
                    be.setChanged();
                },
                be);
    }

    private static Target of(Entity entity) {
        // Entities are saved with their chunk automatically; no setChanged().
        return new Target(
                () -> entity.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag()),
                tag -> entity.setAttached(ModAttachments.LOOT_STATE, tag),
                entity);
    }

    private static int info(CommandSourceStack source, @Nullable Target target, String what) {
        LootrLootState state = loadState(source, target, what);
        if (state == null) {
            return 0;
        }
        int looted = state.lootedCount();
        long now = source.getLevel().getGameTime();
        String refresh = refreshText(state, now, refreshTicksOf(target.holder()));
        String decay = Decay.infoText(target.holder(), state.getFirstGeneratedGameTime(), now);
        source.sendSuccess(() -> Component.literal("Looted by " + looted + " player(s)/team(s). " + refresh
                + (decay.isEmpty() ? "" : " " + decay)), false);
        return looted;
    }

    private static int reset(CommandSourceStack source, @Nullable Target target, String what) {
        return reset(source, target, what, null);
    }

    private static int reset(CommandSourceStack source, @Nullable Target target, String what, @Nullable Entity entity) {
        LootrLootState state = loadState(source, target, what);
        if (state == null) {
            return 0;
        }
        boolean hadAnything = state.resetAll();
        HolderLookup.Provider provider = source.getLevel().registryAccess();
        CompoundTag saved = new CompoundTag();
        state.save(saved, provider);
        target.write().accept(saved);

        if (entity instanceof ItemFrame frame) {
            // ChunkMap's watcher query is not exposed with the same signature in
            // Minecraft 1.21.1 mappings. Sending the normal entity-data update to
            // all currently connected players is safe: clients ignore metadata for
            // entities they are not tracking, and players who start tracking later
            // receive the reset state normally from the entity spawn packet.
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                ItemFrameVisualSync.sendVisibleItem(player, frame);
            }
        }

        source.sendSuccess(() -> Component.literal(hadAnything
                ? "Reset " + what + ": everyone can loot it again."
                : "Nothing to reset on " + what + "."), true);
        return hadAnything ? 1 : 0;
    }

    /** Loads the state, or sends the failure message and returns null when there is none to act on. */
    @Nullable
    private static LootrLootState loadState(CommandSourceStack source, @Nullable Target target, String what) {
        if (target == null) {
            source.sendFailure(Component.literal("There is no block entity at that position."));
            return null;
        }
        CompoundTag tag = target.read().get();
        if (tag.isEmpty()) {
            source.sendFailure(Component.literal("No per-player loot state on " + what
                    + ": nobody has looted it yet, or it isn't a loot container."));
            return null;
        }
        LootrLootState state = new LootrLootState(27);
        state.load(tag, source.getLevel().registryAccess());
        return state;
    }

    /**
     * The refresh interval in force for this object: filtered by its loot table and dimension where it
     * has a loot table (containers, pots, chest minecarts), otherwise the plain {@code refresh_value}.
     */
    private static long refreshTicksOf(Object holder) {
        ResourceKey<LootTable> table;
        Level level;
        if (holder instanceof RandomizableContainerBlockEntity container) {
            table = container.getLootTable();
            level = container.getLevel();
        } else if (holder instanceof DecoratedPotBlockEntity pot) {
            table = pot.getLootTable();
            level = pot.getLevel();
        } else if (holder instanceof MinecartChest cart) {
            table = cart.getLootTable();
            level = cart.level();
        } else if (holder instanceof BrushableBlockEntity brushable) {
            table = ((AccessorBrushableBlockEntity) brushable).lootr$getLootTable();
            level = brushable.getLevel();
        } else {
            return LootrConfig.refreshTicks();
        }
        return level == null ? LootrConfig.refreshTicks() : LootrConfig.refreshTicksFor(level.dimension(), table);
    }

    private static String refreshText(LootrLootState state, long gameTime, long refreshTicks) {
        if (refreshTicks <= 0) {
            return "Refresh is off.";
        }
        long first = state.getFirstGeneratedGameTime();
        if (first < 0) {
            return "Refresh timer has not started.";
        }
        long ticksLeft = Math.max(0, first + refreshTicks - gameTime);
        return "Refreshes in " + (ticksLeft / 20) + "s.";
    }

    private LootrCommands() {}
}
