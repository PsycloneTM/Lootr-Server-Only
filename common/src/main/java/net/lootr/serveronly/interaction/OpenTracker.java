package net.lootr.serveronly.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.UUID;

public final class OpenTracker {
    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final ViewerRegistry<Key> BLOCKS = new ViewerRegistry<>();
    private static final ViewerRegistry<UUID> CARTS = new ViewerRegistry<>();

    private static Predicate<UUID> online(MinecraftServer server) {
        return id -> server.getPlayerList().getPlayer(id) != null;
    }

    public static void opened(ServerLevel level, BlockPos pos, Player player) {
        if (player.isSpectator()) {
            return;
        }
        ViewerRegistry.Change change = BLOCKS.add(new Key(level.dimension(), pos.immutable()), player.getUUID(),
                online(level.getServer()));
        apply(level, pos, change.before(), change.after());
    }

    public static void closed(ServerLevel level, BlockPos pos, Player player) {
        ViewerRegistry.Change change = BLOCKS.remove(new Key(level.dimension(), pos.immutable()), player.getUUID(),
                online(level.getServer()));
        if (change != null) {
            apply(level, pos, change.before(), change.after());
        }
    }

    public static int count(Level level, BlockPos pos) {
        return BLOCKS.count(new Key(level.dimension(), pos));
    }

    public static void openedCart(ServerLevel level, Entity cart, Player player) {
        CARTS.add(cart.getUUID(), player.getUUID(), online(level.getServer()));
    }

    public static void closedCart(ServerLevel level, Entity cart, Player player) {
        CARTS.remove(cart.getUUID(), player.getUUID(), online(level.getServer()));
    }

    public static boolean isViewed(ServerLevel level, Object owner) {
        Set<UUID> ids;
        if (owner instanceof BlockEntity blockEntity) {
            ids = BLOCKS.viewersOf(new Key(level.dimension(), blockEntity.getBlockPos()));
        } else if (owner instanceof Entity entity) {
            ids = CARTS.viewersOf(entity.getUUID());
        } else {
            return false;
        }
        for (UUID id : ids) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && Decay.isShowing(player, owner)) {
                return true;
            }
        }
        return false;
    }

    public static void forget(ServerPlayer player) {
        MinecraftServer server = player.server;
        for (Map.Entry<Key, ViewerRegistry.Change> e : BLOCKS.forget(player.getUUID()).entrySet()) {
            ServerLevel level = server.getLevel(e.getKey().dimension());
            if (level != null) {
                apply(level, e.getKey().pos(), e.getValue().before(), e.getValue().after());
            }
        }
        CARTS.forget(player.getUUID());
    }

    private static void apply(ServerLevel level, BlockPos pos, int before, int after) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity) {
            applyChest(level, pos, state, be instanceof TrappedChestBlockEntity, before, after);
        } else if (be instanceof BarrelBlockEntity) {
            applyBarrel(level, pos, state, before, after);
        } else if (be instanceof ShulkerBoxBlockEntity) {
            applyShulker(level, pos, state, before, after);
        }
    }

    private static void applyChest(ServerLevel level, BlockPos pos, BlockState state, boolean trapped, int before, int after) {
        Block block = state.getBlock();
        if (before != after) {
            level.blockEvent(pos, block, 1, after);
            if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
                if (level.getBlockState(other).is(block)) {
                    level.blockEvent(other, block, 1, after);
                }
            }
            if (trapped) {
                level.updateNeighborsAt(pos, block);
                level.updateNeighborsAt(pos.below(), block);
            }
        }
        if (before == 0 && after > 0) {
            chestSound(level, pos, state, SoundEvents.CHEST_OPEN);
            level.gameEvent(null, GameEvent.CONTAINER_OPEN, pos);
        } else if (before > 0 && after == 0) {
            chestSound(level, pos, state, SoundEvents.CHEST_CLOSE);
            level.gameEvent(null, GameEvent.CONTAINER_CLOSE, pos);
        }
    }

    private static void chestSound(ServerLevel level, BlockPos pos, BlockState state, SoundEvent sound) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        if (state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            Direction toOther = ChestBlock.getConnectedDirection(state);
            x += toOther.getStepX() * 0.5;
            z += toOther.getStepZ() * 0.5;
        }
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    private static void applyBarrel(ServerLevel level, BlockPos pos, BlockState state, int before, int after) {
        if (!state.hasProperty(BarrelBlock.OPEN) || !state.hasProperty(BarrelBlock.FACING)) {
            return;
        }
        if (before == 0 && after > 0) {
            barrelSound(level, pos, state, SoundEvents.BARREL_OPEN);
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), 3);
            level.gameEvent(null, GameEvent.CONTAINER_OPEN, pos);
        } else if (before > 0 && after == 0) {
            barrelSound(level, pos, state, SoundEvents.BARREL_CLOSE);
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), 3);
            level.gameEvent(null, GameEvent.CONTAINER_CLOSE, pos);
        }
    }

    private static void barrelSound(ServerLevel level, BlockPos pos, BlockState state, SoundEvent sound) {
        Direction facing = state.getValue(BarrelBlock.FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() / 2.0;
        double y = pos.getY() + 0.5 + facing.getStepY() / 2.0;
        double z = pos.getZ() + 0.5 + facing.getStepZ() / 2.0;
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    private static void applyShulker(ServerLevel level, BlockPos pos, BlockState state, int before, int after) {
        if (before != after) {
            level.blockEvent(pos, state.getBlock(), 1, after);
        }
        if (before == 0 && after > 0) {
            level.gameEvent(null, GameEvent.CONTAINER_OPEN, pos);
            level.playSound(null, pos, SoundEvents.SHULKER_BOX_OPEN, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
        } else if (before > 0 && after == 0) {
            level.gameEvent(null, GameEvent.CONTAINER_CLOSE, pos);
            level.playSound(null, pos, SoundEvents.SHULKER_BOX_CLOSE, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
        }
    }

    private OpenTracker() {}
}
