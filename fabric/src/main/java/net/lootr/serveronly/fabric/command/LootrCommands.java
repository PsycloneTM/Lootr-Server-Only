package net.lootr.serveronly.fabric.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.fabric.registry.ItemFrameMarker;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fabric twin of the NeoForge {@code LootrCommands}; see that class for the
 * command list and behavior ({@code /lootr info|reset block|entity} and
 * {@code /lootr frame mark|unmark}, permission level 2). Fabric-only extra:
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
    private record Target(Supplier<CompoundTag> read, Consumer<CompoundTag> write) {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("lootr")
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
                                                .executes(ctx -> reset(ctx.getSource(),
                                                        of(EntityArgument.getEntity(ctx, "target")), "that entity")))))
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
                                .executes(ctx -> reload(ctx.getSource())))));
    }

    private static int reload(CommandSourceStack source) {
        LootrConfig.load();
        source.sendSuccess(() -> Component.literal("Reloaded lootr_serveronly.json."), true);
        return 1;
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
                });
    }

    private static Target of(Entity entity) {
        // Entities are saved with their chunk automatically; no setChanged().
        return new Target(
                () -> entity.getAttachedOrElse(ModAttachments.LOOT_STATE, new CompoundTag()),
                tag -> entity.setAttached(ModAttachments.LOOT_STATE, tag));
    }

    private static int info(CommandSourceStack source, @Nullable Target target, String what) {
        LootrLootState state = loadState(source, target, what);
        if (state == null) {
            return 0;
        }
        int looted = state.lootedCount();
        String refresh = refreshText(state, source.getLevel().getGameTime());
        source.sendSuccess(() -> Component.literal("Looted by " + looted + " player(s)/team(s). " + refresh), false);
        return looted;
    }

    private static int reset(CommandSourceStack source, @Nullable Target target, String what) {
        LootrLootState state = loadState(source, target, what);
        if (state == null) {
            return 0;
        }
        boolean hadAnything = state.resetAll();
        HolderLookup.Provider provider = source.getLevel().registryAccess();
        CompoundTag saved = new CompoundTag();
        state.save(saved, provider);
        target.write().accept(saved);
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

    private static String refreshText(LootrLootState state, long gameTime) {
        long refreshTicks = LootrConfig.refreshTicks();
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
