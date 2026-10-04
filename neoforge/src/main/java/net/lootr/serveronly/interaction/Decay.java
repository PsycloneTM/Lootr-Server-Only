package net.lootr.serveronly.interaction;

import net.lootr.serveronly.api.LootListeners;
import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.data.DecayTracker;
import net.lootr.serveronly.data.PositionTracker;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.data.PlayerClears;
import net.lootr.serveronly.data.PlayerScopedContainer;
import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.lootr.serveronly.config.StructureTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class Decay {

    public static boolean covers(@Nullable Level level, @Nullable BlockPos pos, @Nullable ResourceKey<LootTable> table) {
        return level != null
                && LootrConfig.decayTicks() > 0
                && LootrConfig.isLootTableEnabled(table)
                && (LootrConfig.isDecayLootTable(table)
                    || LootrConfig.isDecayDimension(level.dimension())
                    || (level instanceof ServerLevel serverLevel && pos != null
                        && StructureTags.isIn(serverLevel, pos, StructureTags.DECAY)));
    }

    public static long ticksLeft(@Nullable Level level, @Nullable BlockPos pos, long firstGenerated,
                                 @Nullable ResourceKey<LootTable> table, long now) {
        if (firstGenerated < 0 || !covers(level, pos, table)) {
            return -1;
        }
        return Math.max(0, firstGenerated + LootrConfig.decayTicks() - now);
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
        if (LootrConfig.DISABLE_NOTIFICATIONS.get()) {
            return;
        }
        long left = ticksLeft(player.level(), pos, state.getFirstGeneratedGameTime(), table, now);
        if (left < 0) {
            return;
        }
        boolean justStarted = left >= LootrConfig.decayTicks();
        int delay = LootrConfig.notificationDelay();
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

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        try {
            PlayerClears.get(server);
            if (server.getTickCount() % LootrConfig.TICK_DELAY.get() == 0) {
                sweep(server);
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    private static boolean failureLogged = false;

    private static void logFailure(RuntimeException e) {
        if (!failureLogged) {
            failureLogged = true;
            LootrServerOnly.LOGGER.error("Decay sweep failed on one container; skipping it and carrying on. "
                    + "Repeats of this are not logged until a sweep completes cleanly.", e);
        }
    }

    private static void sweep(MinecraftServer server) {
        if (LootrConfig.decayTicks() > 0 && LootrConfig.PERFORM_DECAY_WHILE_TICKING.get()) {
            PositionTracker tracker = DecayTracker.get(server);
            tracker.syncSetting(LootrConfig.decayTicks());
            for (ServerLevel level : server.getAllLevels()) {
                if (!LootrConfig.isDimensionEnabled(level.dimension())) {
                    continue;
                }
                sweepBlocks(level, tracker);
                sweepCarts(level);
            }
        }
        Refresh.sweep(server);
        failureLogged = false;
    }

    private static final long DEFER_TICKS = 200;

    private static void sweepBlocks(ServerLevel level, PositionTracker tracker) {
        long now = level.getGameTime();
        var dimension = level.dimension();
        for (BlockPos pos : tracker.takeDue(dimension, now)) {
            try {
                if (!level.hasChunkAt(pos)) {
                    tracker.schedule(dimension, pos, now + DEFER_TICKS);
                    continue;
                }
                if (LootrConfig.checkWorldBorder() && !level.getWorldBorder().isWithinBounds(pos)) {
                    tracker.schedule(dimension, pos, now + DEFER_TICKS);
                    continue;
                }
                if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container)
                        || ContainerInteractionHandler.kindOf(container) == null
                        || !container.hasData(ModAttachments.LOOT_STATE)) {
                    continue;
                }
                long left = ticksLeft(level, pos, container.getData(ModAttachments.LOOT_STATE).getFirstGeneratedGameTime(),
                        container.getLootTable(), now);
                if (left < 0) {
                    continue;
                }
                if (left == 0) {
                    if (isViewed(level, container)) {
                        tracker.schedule(dimension, pos, now + 1);
                    } else {
                        decayBlock(level, container);
                    }
                } else {
                    tracker.schedule(dimension, pos, now + left);
                }
            } catch (RuntimeException e) {
                tracker.schedule(dimension, pos, now + DEFER_TICKS);
                logFailure(e);
            }
        }
    }

    private static void sweepCarts(ServerLevel level) {
        long now = level.getGameTime();
        List<? extends MinecartChest> carts = level.getEntities(EntityType.CHEST_MINECART,
                cart -> cart.hasData(ModAttachments.LOOT_STATE));
        for (MinecartChest cart : carts) {
            try {
                long left = ticksLeft(level, cart.blockPosition(), cart.getData(ModAttachments.LOOT_STATE).getFirstGeneratedGameTime(),
                        cart.getLootTable(), now);
                if (left == 0 && !isViewed(level, cart)) {
                    decayCart(level, cart);
                }

            } catch (RuntimeException e) {
                logFailure(e);
            }
        }
    }

    private static void decayBlock(ServerLevel level, RandomizableContainerBlockEntity container) {
        BlockPos pos = container.getBlockPos();
        LootListeners.decaying(level, container, pos, container.getLootTable());
        container.setLootTable(null);
        if (LootrConfig.REPLACE_WHEN_DECAYED.get()) {
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
        if (LootrConfig.REPLACE_WHEN_DECAYED.get()) {
            return;
        }
        cart.discard();
    }

    public static boolean isViewed(ServerLevel level, Object owner) {
        for (ServerPlayer player : level.players()) {
            AbstractContainerMenu menu = player.containerMenu;
            if (menu != null && !menu.slots.isEmpty()
                    && menu.slots.get(0).container instanceof PlayerScopedContainer view
                    && view.getOwner() == owner) {
                return true;
            }
        }
        return false;
    }

    public static List<ServerPlayer> viewers(ServerLevel level, Object owner) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            AbstractContainerMenu menu = player.containerMenu;
            if (menu != null && !menu.slots.isEmpty()
                    && menu.slots.get(0).container instanceof PlayerScopedContainer view
                    && view.getOwner() == owner) {
                out.add(player);
            }
        }
        return out;
    }

    private Decay() {}
}
