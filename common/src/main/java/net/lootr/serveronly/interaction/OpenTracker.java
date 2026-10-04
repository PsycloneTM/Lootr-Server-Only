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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OpenTracker {
    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<Key, Set<UUID>> OPENERS = new HashMap<>();

    public static void opened(ServerLevel level, BlockPos pos, Player player) {
        if (player.isSpectator()) {
            return;
        }
        Set<UUID> set = OPENERS.computeIfAbsent(new Key(level.dimension(), pos.immutable()), k -> new HashSet<>());
        int before = set.size();
        prune(level.getServer(), set);
        set.add(player.getUUID());
        apply(level, pos, before, set.size());
    }

    public static void closed(ServerLevel level, BlockPos pos, Player player) {
        Key key = new Key(level.dimension(), pos.immutable());
        Set<UUID> set = OPENERS.get(key);
        if (set == null) {
            return;
        }
        int before = set.size();
        set.remove(player.getUUID());
        prune(level.getServer(), set);
        if (set.isEmpty()) {
            OPENERS.remove(key);
        }
        apply(level, pos, before, set.size());
    }

    public static int count(Level level, BlockPos pos) {
        Set<UUID> set = OPENERS.get(new Key(level.dimension(), pos));
        return set == null ? 0 : set.size();
    }

    public static void forget(ServerPlayer player) {
        MinecraftServer server = player.server;
        List<Key> touched = new ArrayList<>();
        for (Map.Entry<Key, Set<UUID>> e : OPENERS.entrySet()) {
            if (e.getValue().contains(player.getUUID())) {
                touched.add(e.getKey());
            }
        }
        for (Key key : touched) {
            ServerLevel level = server.getLevel(key.dimension());
            Set<UUID> set = OPENERS.get(key);
            if (level == null || set == null) {
                continue;
            }
            int before = set.size();
            set.remove(player.getUUID());
            if (set.isEmpty()) {
                OPENERS.remove(key);
            }
            apply(level, key.pos(), before, set.size());
        }
    }

    private static void prune(MinecraftServer server, Set<UUID> set) {
        set.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
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
