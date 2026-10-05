package net.lootr.serveronly.interaction;

import net.lootr.serveronly.schedule.LootSchedule;
import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.api.LootListeners;
import net.lootr.serveronly.common.LootrServerOnlyConstants;
import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.data.DecayTracker;
import net.lootr.serveronly.data.PositionTracker;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.data.PlayerClears;
import net.lootr.serveronly.data.PlayerScopedContainer;
import net.minecraft.core.BlockPos;
import net.lootr.serveronly.config.StructureTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Decay {
    public static boolean covers(@Nullable Level level, @Nullable BlockPos pos, @Nullable ResourceKey<LootTable> table) {
        return level != null
                && LootrSettings.decayTicks() > 0
                && LootrSettings.isLootTableEnabled(table)
                && (LootrSettings.isDecayLootTable(table)
                    || LootrSettings.isDecayDimension(level.dimension())
                    || (level instanceof ServerLevel serverLevel && pos != null
                        && StructureTags.isIn(serverLevel, pos, StructureTags.DECAY)));
    }

    public static long ticksLeft(@Nullable Level level, @Nullable BlockPos pos, long firstGenerated,
                                 @Nullable ResourceKey<LootTable> table, long now) {
        if (firstGenerated < 0 || !covers(level, pos, table)) {
            return -1;
        }
        return LootSchedule.decayTicksLeft(firstGenerated, LootrSettings.decayTicks(), now);
    }

    public static String format(long ticks) {
        long seconds = (Math.max(0, ticks) + 19) / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    public static String infoText(Object holder, long firstGenerated, long now) {
        ResourceKey<LootTable> table;
        Level level;
        BlockPos pos;
        if (holder instanceof RandomizableContainerBlockEntity container
                && ContainerInteractionHandler.kindOf(container) != null) {
            table = container.getLootTable();
            level = container.getLevel();
            pos = container.getBlockPos();
        } else if (holder instanceof MinecartChest cart) {
            table = cart.getLootTable();
            level = cart.level();
            pos = cart.blockPosition();
        } else {
            return "";
        }
        if (!covers(level, pos, table)) {
            return "Decay is off.";
        }
        if (firstGenerated < 0) {
            return "Decay timer has not started.";
        }
        long left = ticksLeft(level, pos, firstGenerated, table, now);
        return left == 0 ? "Decays any moment." : "Decays in " + format(left) + ".";
    }

    public static void track(ServerLevel level, RandomizableContainerBlockEntity container) {
        if (covers(level, container.getBlockPos(), container.getLootTable())) {
            DecayTracker.get(level.getServer()).add(level.dimension(), container.getBlockPos());
        }
    }

    public static void announce(ServerPlayer player, LootrLootState state, BlockPos pos,
                                @Nullable ResourceKey<LootTable> table, long now) {
        if (LootrSettings.disableNotifications()) {
            return;
        }
        long left = ticksLeft(player.level(), pos, state.getFirstGeneratedGameTime(), table, now);
        if (left < 0) {
            return;
        }
        boolean justStarted = left >= LootrSettings.decayTicks();
        int delay = LootrSettings.notificationDelay();
        if (!justStarted && delay >= 0 && left > delay) {
            return;
        }
        String text = left == 0 ? "This container is about to decay."
                : justStarted ? "The container begins to crumble at your touch! It will decay in " + format(left) + "."
                : "This container will decay in " + format(left) + ".";
        player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay(text), false);
    }

    public static boolean decayIfDue(ServerLevel level, RandomizableContainerBlockEntity container, LootrLootState state) {
        if (ticksLeft(level, container.getBlockPos(), state.getFirstGeneratedGameTime(), container.getLootTable(), level.getGameTime()) != 0
                || isViewed(level, container)) {
            return false;
        }
        decayBlock(level, container);
        return true;
    }

    public static boolean decayIfDue(ServerLevel level, MinecartChest cart, LootrLootState state) {
        if (ticksLeft(level, cart.blockPosition(), state.getFirstGeneratedGameTime(), cart.getLootTable(), level.getGameTime()) != 0
                || isViewed(level, cart)) {
            return false;
        }
        decayCart(level, cart);
        return true;
    }

    static boolean backgroundEnabled() {
        return LootrSettings.decayTicks() > 0 && LootrSettings.performDecayWhileTicking();
    }

    /** Decides what to do with a due container: decay it, wait for viewers, or look again later. */
    static long decideBlock(ServerLevel level, BlockPos pos, RandomizableContainerBlockEntity container, long now) {
        long left = ticksLeft(level, pos, LootStateStore.firstGenerated(container), container.getLootTable(), now);
        LootSchedule.Step step = LootSchedule.decayStep(left, now);
        if (step.kind() == LootSchedule.Kind.DROP) {
            return BlockSweep.DONE;
        }
        if (step.kind() == LootSchedule.Kind.RESCHEDULE) {
            return step.at();
        }
        if (isViewed(level, container)) {
            return now + LootSchedule.VIEWED_RETRY_TICKS;
        }
        decayBlock(level, container);
        return BlockSweep.DONE;
    }

    private static void decayBlock(ServerLevel level, RandomizableContainerBlockEntity container) {
        BlockPos pos = container.getBlockPos();
        LootListeners.decaying(level, container, pos, container.getLootTable());
        container.setLootTable(null);
        if (LootrSettings.replaceWhenDecayed()) {
            container.setChanged();
            level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    8, 0.25, 0.25, 0.25, 0.02);
            DecayTracker.get(level.getServer()).remove(level.dimension(), pos);
            return;
        }
        level.destroyBlock(pos, false);
        DecayTracker.get(level.getServer()).remove(level.dimension(), pos);
    }

    private static void decayCart(ServerLevel level, MinecartChest cart) {
        LootListeners.decaying(level, cart, cart.blockPosition(), cart.getLootTable());
        ContainerProtection.clearCartLootTable(cart);
        level.sendParticles(ParticleTypes.POOF, cart.getX(), cart.getY(0.5), cart.getZ(), 8, 0.25, 0.25, 0.25, 0.02);
        level.playSound(null, cart.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (LootrSettings.replaceWhenDecayed()) {
            return;
        }
        cart.discard();
    }

    public static boolean isViewed(ServerLevel level, Object owner) {
        return OpenTracker.isViewed(level, owner);
    }

    /** True if this player's open menu is a Lootr view of {@code owner}. */
    static boolean isShowing(ServerPlayer player, Object owner) {
        AbstractContainerMenu menu = player.containerMenu;
        return menu != null && !menu.slots.isEmpty()
                && menu.slots.get(0).container instanceof PlayerScopedContainer view
                && view.getOwner() == owner;
    }

    public static List<ServerPlayer> viewers(ServerLevel level, Object owner) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (isShowing(player, owner)) {
                out.add(player);
            }
        }
        return out;
    }

    private Decay() {}
}
