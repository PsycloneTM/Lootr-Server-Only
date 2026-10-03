package net.lootr.serveronly.command;

import net.lootr.serveronly.interaction.Decay;
import net.lootr.serveronly.interaction.Refresh;
import net.lootr.serveronly.interaction.ContainerInteractionHandler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.registry.ItemFrameMarker;
import net.lootr.serveronly.registry.ModAttachments;
import net.lootr.serveronly.interaction.ItemFrameVisualSync;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.data.PlayerClears;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Operator commands (permission level 2):
 * <ul>
 *     <li>{@code /lootr info block <pos>} / {@code /lootr info entity <target>}
 *     - how many players (or teams) have looted it, and the refresh timer.</li>
 *     <li>{@code /lootr reset block <pos>} / {@code /lootr reset entity <target>}
 *     - forget everyone's loot so the next open by anyone rolls fresh loot.</li>
 *     <li>{@code /lootr refresh|decay|id block <pos>|entity <target>} - force a refresh, force a decay, or report the
 *     stable Lootr state identity for a target.
 *     <li>{@code /lootr clear <players>} - forget everything those players have looted, so they
 *     can loot it all again (lazy; see {@code PlayerClears}).</li>
 *     <li>{@code /lootr openers block <pos>} / {@code entity <target>} - who has it open right now.</li>
 *     <li>{@code /lootr frame mark|unmark <target>} - make one item frame a loot
 *     frame, or stop it being one.</li>
 *     <li>Spawn ({@code chest}, {@code barrel}, ...), {@code force_*} and {@code open_as*}: see {@link AdminCommands}.</li>
 * </ul>
 * Blocks and entities are addressed explicitly rather than "the thing you are
 * looking at", so every command works from a command block or the console and
 * needs no ray-tracing. Entities cover chest minecarts and item frames.
 * <p>
 * Both commands work on the same per-object attachment the containers, pots,
 * brushable blocks, minecarts and frames all share, so one code path covers
 * every kind. Nothing here can turn a vanilla container into a Lootr one or
 * back: an object only has state once somebody has looted it.
 */
@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class LootrCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("lootr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info")
                        .then(Commands.literal("block")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> info(ctx.getSource(), blockAt(ctx), "that block"))))
                        .then(Commands.literal("entity")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> info(ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "target"), "that entity")))))
                .then(Commands.literal("reset")
                        .then(Commands.literal("block")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> reset(ctx.getSource(), blockAt(ctx), "that block"))))
                        .then(Commands.literal("entity")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> {
                                            Entity entity = EntityArgument.getEntity(ctx, "target");
                                            return reset(ctx.getSource(), entity, "that entity");
                                        }))))
                .then(Commands.literal("refresh")
                        .then(Commands.literal("block")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> refreshNow(ctx.getSource(), blockAt(ctx), "that block"))))
                        .then(Commands.literal("entity")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> {
                                            Entity entity = EntityArgument.getEntity(ctx, "target");
                                            return refreshNow(ctx.getSource(), entity instanceof IAttachmentHolder h ? h : null, "that entity");
                                        }))))
                .then(Commands.literal("decay")
                        .then(Commands.literal("block")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> decayNow(ctx.getSource(), blockAt(ctx), "that block"))))
                        .then(Commands.literal("entity")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> {
                                            Entity entity = EntityArgument.getEntity(ctx, "target");
                                            return decayNow(ctx.getSource(), entity instanceof IAttachmentHolder h ? h : null, "that entity");
                                        }))))
                .then(Commands.literal("id")
                        .then(Commands.literal("block")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> idOf(ctx.getSource(), blockAt(ctx), "that block"))))
                        .then(Commands.literal("entity")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> {
                                            Entity entity = EntityArgument.getEntity(ctx, "target");
                                            return idOf(ctx.getSource(), entity instanceof IAttachmentHolder h ? h : null, "that entity");
                                        }))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> clear(ctx.getSource(), EntityArgument.getPlayers(ctx, "players")))))
                .then(Commands.literal("cclear")
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> clear(ctx.getSource(), EntityArgument.getPlayers(ctx, "players")))))
                .then(Commands.literal("openers")
                        .then(Commands.literal("block")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> openers(ctx.getSource(), ctx.getSource().getLevel(),
                                                blockAt(ctx), "that block"))))
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
                                                EntityArgument.getEntity(ctx, "target"), false)))));
        // chest|barrel|... spawn commands, force_chunk|force_radius|force_all, open_as|open_as_uuid
        AdminCommands.addTo(root);
        event.getDispatcher().register(root);
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
        boolean team = LootrConfig.TEAM_LOOT.get();
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

    /**
     * Marks or unmarks one item frame as a loot frame (see {@link ItemFrameMarker}).
     * Frames in worlds generated before the mod was installed, or placed by a
     * structure block, are never marked automatically; this replaces the long
     * {@code /tag} command. Marking applies the same eligibility rules as the
     * automatic marker (no fixed, invisible, empty or map frames).
     */
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

    /** The block entity at the given (loaded) position, or null if the block has none. */
    @Nullable
    private static BlockEntity blockAt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getLevel().getBlockEntity(BlockPosArgument.getLoadedBlockPos(ctx, "pos"));
    }

    private static int info(CommandSourceStack source, @Nullable IAttachmentHolder holder, String what) {
        if (!hasState(source, holder, what)) {
            return 0;
        }
        LootrLootState state = holder.getData(ModAttachments.LOOT_STATE);
        int looted = state.lootedCount();
        long now = source.getLevel().getGameTime();
        String refresh = refreshText(state, now, refreshTicksOf(holder));
        String decay = Decay.infoText(holder, state.getFirstGeneratedGameTime(), now);
        source.sendSuccess(() -> Component.literal("Looted by " + looted + " player(s)/team(s). " + refresh
                + (decay.isEmpty() ? "" : " " + decay)), false);
        return looted;
    }

    private static int reset(CommandSourceStack source, Entity entity, String what) {
        if (!(entity instanceof IAttachmentHolder holder)) {
            source.sendFailure(Component.literal("That entity cannot hold Lootr state."));
            return 0;
        }
        if (!hasState(source, holder, what)) {
            return 0;
        }
        boolean hadAnything = holder.getData(ModAttachments.LOOT_STATE).resetAll();

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

    private static int reset(CommandSourceStack source, @Nullable IAttachmentHolder holder, String what) {
        if (!hasState(source, holder, what)) {
            return 0;
        }
        boolean hadAnything = holder.getData(ModAttachments.LOOT_STATE).resetAll();
        if (holder instanceof BlockEntity blockEntity) {
            blockEntity.setChanged();
        }
        source.sendSuccess(() -> Component.literal(hadAnything
                ? "Reset " + what + ": everyone can loot it again."
                : "Nothing to reset on " + what + "."), true);
        return hadAnything ? 1 : 0;
    }

    /** Forces the existing refresh timer due and then runs the normal refresh path. */
    private static int refreshNow(CommandSourceStack source, @Nullable IAttachmentHolder holder, String what) {
        if (!hasState(source, holder, what)) {
            return 0;
        }
        ServerLevel level = levelOf(source, holder);
        LootrLootState state = holder.getData(ModAttachments.LOOT_STATE);

        long refreshTicks = refreshTicksOf(holder);
        if (refreshTicks <= 0) {
            source.sendFailure(Component.literal("Refresh is not enabled for " + what + "."));
            return 0;
        }
        if (holder instanceof RandomizableContainerBlockEntity block && ContainerInteractionHandler.kindOf(block) != null) {
            state.setFirstGeneratedGameTime(level.getGameTime() - refreshTicks);
            boolean refreshed = Refresh.refreshIfDue(level, block, state, refreshTicks);
            if (refreshed) {
                block.setChanged();
                Refresh.notifyRefreshed(level, block);
            }
            source.sendSuccess(() -> Component.literal(refreshed
                    ? "Refreshed " + what + "."
                    : "Someone has " + what + " open; it will refresh as soon as it is closed."), true);
            return refreshed ? 1 : 0;
        }
        if (holder instanceof MinecartChest cart) {
            state.setFirstGeneratedGameTime(level.getGameTime() - refreshTicks);
            boolean refreshed = Refresh.refreshIfDue(level, cart, state, refreshTicks);
            if (refreshed) {
                Refresh.notifyRefreshed(level, cart);
            }
            source.sendSuccess(() -> Component.literal(refreshed
                    ? "Refreshed " + what + "."
                    : "Someone has " + what + " open; it will refresh as soon as it is closed."), true);
            return refreshed ? 1 : 0;
        }
        if (holder instanceof DecoratedPotBlockEntity pot || holder instanceof BrushableBlockEntity) {
            state.setFirstGeneratedGameTime(level.getGameTime() - refreshTicks);
            boolean refreshed = state.refreshIfDue(level.getGameTime(), refreshTicks);
            if (holder instanceof BlockEntity be) {
                be.setChanged();
            }
            if (refreshed) {
                Refresh.notifyRefreshed(level, holder);
            }
            source.sendSuccess(() -> Component.literal(refreshed
                    ? "Refreshed " + what + "."
                    : "Nothing changed on " + what + "."), true);
            return refreshed ? 1 : 0;
        }
        source.sendFailure(Component.literal("That object is not a supported Lootr refresh target."));
        return 0;
    }

    /** Forces the existing decay timer due and then runs the normal decay path. */
    private static int decayNow(CommandSourceStack source, @Nullable IAttachmentHolder holder, String what) {
        if (!hasState(source, holder, what)) {
            return 0;
        }
        ServerLevel level = levelOf(source, holder);
        LootrLootState state = holder.getData(ModAttachments.LOOT_STATE);
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
            chestLike.setChanged();
            Decay.track(level, chestLike);
            decayed = Decay.decayIfDue(level, chestLike, state);
        } else {
            decayed = Decay.decayIfDue(level, (MinecartChest) holder, state);
        }
        source.sendSuccess(() -> Component.literal(decayed
                ? "Decayed " + what + "."
                : "Someone has " + what + " open; it will decay as soon as it is closed."), true);
        return decayed ? 1 : 0;
    }

    /** Returns the stable state UUID assigned to this Lootr object. */
    private static int idOf(CommandSourceStack source, @Nullable IAttachmentHolder holder, String what) {
        if (!hasState(source, holder, what)) {
            return 0;
        }
        LootrLootState state = holder.getData(ModAttachments.LOOT_STATE);
        UUID id = state.getContainerId();
        if (holder instanceof BlockEntity blockEntity) {
            blockEntity.setChanged();
        }
        source.sendSuccess(() -> Component.literal("Lootr id of " + what + ": " + id), false);
        return 1;
    }

    /** Returns the server level containing a supported Lootr attachment holder. */
    private static ServerLevel levelOf(CommandSourceStack source, @Nullable IAttachmentHolder holder) {
        if (holder instanceof BlockEntity blockEntity && blockEntity.getLevel() instanceof ServerLevel level) {
            return level;
        }
        if (holder instanceof Entity entity && entity.level() instanceof ServerLevel level) {
            return level;
        }
        return source.getLevel();
    }

    /** Sends the failure message and returns false when there is no loot state to act on. */
    private static boolean hasState(CommandSourceStack source, @Nullable IAttachmentHolder holder, String what) {
        if (holder == null) {
            source.sendFailure(Component.literal("There is no block entity at that position."));
            return false;
        }
        if (!holder.hasData(ModAttachments.LOOT_STATE)) {
            source.sendFailure(Component.literal("No per-player loot state on " + what
                    + ": nobody has looted it yet, or it isn't a loot container."));
            return false;
        }
        return true;
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
            return LootrConfig.REFRESH_TICKS.get();
        }
        return level == null ? LootrConfig.REFRESH_TICKS.get() : LootrConfig.refreshTicksFor(level.dimension(), table);
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
