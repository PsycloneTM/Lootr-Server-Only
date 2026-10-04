package net.lootr.serveronly.fabric.event;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.lootr.serveronly.interaction.ContainerInteractionHandler;
import net.lootr.serveronly.interaction.ContainerProtection;
import net.lootr.serveronly.interaction.InteractionOutcome;
import net.lootr.serveronly.interaction.ItemFrameInteractionHandler;
import net.lootr.serveronly.interaction.MinecartInteractionHandler;
import net.lootr.serveronly.interaction.OpenTracker;
import net.lootr.serveronly.interaction.PotInteractionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

public final class FabricEvents {
    public static void register() {
        ContainerProtection.installFakePlayerCheck(player -> player instanceof FakePlayer);

        UseBlockCallback.EVENT.register(FabricEvents::onUseBlockContainer);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OpenTracker.forget(handler.getPlayer()));

        UseEntityCallback.EVENT.register(FabricEvents::onUseEntityMinecart);
        AttackEntityCallback.EVENT.register(FabricEvents::onAttackEntityMinecart);

        UseBlockCallback.EVENT.register(FabricEvents::onUseBlockPot);
        AttackBlockCallback.EVENT.register(FabricEvents::onAttackBlockPot);

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level)) {
                return true;
            }
            boolean[] denied = {false};
            ContainerProtection.handleBreak(player, level, pos, () -> denied[0] = true);
            return !denied[0];
        });

        AttackEntityCallback.EVENT.register(FabricEvents::onAttackEntityItemFrame);
        UseEntityCallback.EVENT.register(FabricEvents::onUseEntityItemFrame);
    }

    private static InteractionResult result(InteractionOutcome outcome) {
        return switch (outcome) {
            case PASS -> InteractionResult.PASS;
            case CONSUME -> InteractionResult.CONSUME;
            case SUCCESS -> InteractionResult.SUCCESS;
        };
    }

    private static InteractionResult onUseBlockContainer(Player player, Level world, InteractionHand hand,
                                                         BlockHitResult hitResult) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        return result(ContainerInteractionHandler.use(serverPlayer, (ServerLevel) world, hitResult.getBlockPos()));
    }

    private static InteractionResult onUseEntityMinecart(Player player, Level world, InteractionHand hand,
                                                         Entity entity, @Nullable EntityHitResult hit) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(entity instanceof MinecartChest cart)) {
            return InteractionResult.PASS;
        }
        return result(MinecartInteractionHandler.use(serverPlayer, cart, (ServerLevel) world));
    }

    private static InteractionResult onAttackEntityMinecart(Player player, Level world, InteractionHand hand,
                                                            Entity entity, @Nullable EntityHitResult hit) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof MinecartChest cart)) {
            return InteractionResult.PASS;
        }
        return MinecartInteractionHandler.attack(serverPlayer, cart, (ServerLevel) world)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static InteractionResult onUseBlockPot(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        return result(PotInteractionHandler.use(serverPlayer, (ServerLevel) world, hit.getBlockPos(), hand));
    }

    private static InteractionResult onAttackBlockPot(Player player, Level world, InteractionHand hand,
                                                      BlockPos pos, Direction direction) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        return PotInteractionHandler.attack(serverPlayer, (ServerLevel) world, pos)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static InteractionResult onAttackEntityItemFrame(Player player, Level world, InteractionHand hand,
                                                             Entity entity, @Nullable EntityHitResult hit) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof ItemFrame frame)) {
            return InteractionResult.PASS;
        }

        return ItemFrameInteractionHandler.attack(serverPlayer, frame, (ServerLevel) world, hand == InteractionHand.MAIN_HAND)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static InteractionResult onUseEntityItemFrame(Player player, Level world, InteractionHand hand,
                                                          Entity entity, @Nullable EntityHitResult hit) {
        if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof ItemFrame frame)) {
            return InteractionResult.PASS;
        }
        return ItemFrameInteractionHandler.use(serverPlayer, frame, (ServerLevel) world, hand == InteractionHand.MAIN_HAND)
                ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    private FabricEvents() {}
}
