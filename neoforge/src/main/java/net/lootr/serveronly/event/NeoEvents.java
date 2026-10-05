package net.lootr.serveronly.event;

import net.lootr.serveronly.LootrServerOnly;
import net.lootr.serveronly.interaction.ChunkDiscovery;
import net.lootr.serveronly.interaction.ContainerInteractionHandler;
import net.lootr.serveronly.interaction.ContainerProtection;
import net.lootr.serveronly.interaction.LootScheduler;
import net.lootr.serveronly.interaction.InteractionOutcome;
import net.lootr.serveronly.interaction.ItemFrameInteractionHandler;
import net.lootr.serveronly.interaction.MinecartInteractionHandler;
import net.lootr.serveronly.interaction.OpenTracker;
import net.lootr.serveronly.interaction.PotInteractionHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = LootrServerOnly.MOD_ID)
public final class NeoEvents {
    public static void init() {
        ContainerProtection.installFakePlayerCheck(player -> player instanceof FakePlayer);
    }

    @SubscribeEvent
    public static void onRightClickBlockContainer(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        InteractionOutcome outcome = ContainerInteractionHandler.use(player, (ServerLevel) event.getLevel(), event.getPos());
        if (outcome != InteractionOutcome.PASS) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            OpenTracker.forget(player);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractMinecart(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof MinecartChest cart)) {
            return;
        }
        if (MinecartInteractionHandler.use(player, cart, (ServerLevel) event.getLevel()) != InteractionOutcome.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    @SubscribeEvent
    public static void onAttackEntityMinecart(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof MinecartChest cart)) {
            return;
        }
        if (MinecartInteractionHandler.attack(player, cart, (ServerLevel) player.level())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlockPot(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PotInteractionHandler.use(player, (ServerLevel) event.getLevel(), event.getPos(), event.getHand())
                != InteractionOutcome.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlockPot(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (PotInteractionHandler.attack(player, (ServerLevel) event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntityItemFrame(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof ItemFrame frame)) {
            return;
        }
        if (ItemFrameInteractionHandler.attack(player, frame, (ServerLevel) player.level(), true)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractItemFrame(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof ItemFrame frame)) {
            return;
        }
        if (ItemFrameInteractionHandler.use(player, frame, (ServerLevel) event.getLevel(),
                event.getHand() == InteractionHand.MAIN_HAND)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    @SubscribeEvent
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (!event.getSource().isCreativePlayer()
                && ContainerProtection.isManagedEntity(event.getEntity(), event.getSource())) {
            event.setInvulnerable(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ContainerProtection.handleBreak(event.getPlayer(), level, event.getPos(), () -> event.setCanceled(true));
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && ContainerProtection.blastSpares()) {
            event.getAffectedBlocks().removeIf(pos -> ContainerProtection.isManagedAt(level, pos));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        LootScheduler.onServerTick(event.getServer());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            ChunkDiscovery.onChunkLoaded(level, chunk);
        }
    }

    private NeoEvents() {}
}
