package net.lootr.serveronly.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.registry.ItemFrameMarker;
import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Operator commands (permission level 2):
 * <ul>
 *     <li>{@code /lootr info block <pos>} / {@code /lootr info entity <target>}
 *     - how many players (or teams) have looted it, and the refresh timer.</li>
 *     <li>{@code /lootr reset block <pos>} / {@code /lootr reset entity <target>}
 *     - forget everyone's loot so the next open by anyone rolls fresh loot.</li>
 *     <li>{@code /lootr frame mark|unmark <target>} - make one item frame a loot
 *     frame, or stop it being one.</li>
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
        event.getDispatcher().register(Commands.literal("lootr")
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
                                        .executes(ctx -> reset(ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "target"), "that entity")))))
                .then(Commands.literal("frame")
                        .then(Commands.literal("mark")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> markFrame(ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "target"), true))))
                        .then(Commands.literal("unmark")
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> markFrame(ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "target"), false))))));
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
        String refresh = refreshText(state, source.getLevel().getGameTime());
        source.sendSuccess(() -> Component.literal("Looted by " + looted + " player(s)/team(s). " + refresh), false);
        return looted;
    }

    private static int reset(CommandSourceStack source, @Nullable IAttachmentHolder holder, String what) {
        if (!hasState(source, holder, what)) {
            return 0;
        }
        boolean hadAnything = holder.getData(ModAttachments.LOOT_STATE).resetAll();
        if (holder instanceof BlockEntity blockEntity) {
            // Block entities only save when marked dirty; entities are saved
            // with their chunk regardless.
            blockEntity.setChanged();
        }
        source.sendSuccess(() -> Component.literal(hadAnything
                ? "Reset " + what + ": everyone can loot it again."
                : "Nothing to reset on " + what + "."), true);
        return hadAnything ? 1 : 0;
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

    private static String refreshText(LootrLootState state, long gameTime) {
        long refreshTicks = LootrConfig.REFRESH_TICKS.get();
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
