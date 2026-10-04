package net.lootr.serveronly.fabric.data;

import net.lootr.serveronly.data.LootStateStore;
import net.lootr.serveronly.data.LootrLootState;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class FabricLootStateBackend implements LootStateStore.Backend {
    @Nullable
    private static CompoundTag tagOf(Object holder) {
        if (holder instanceof BlockEntity blockEntity) {
            return blockEntity.getAttached(ModAttachments.LOOT_STATE);
        }
        if (holder instanceof Entity entity) {
            return entity.getAttached(ModAttachments.LOOT_STATE);
        }
        return null;
    }

    private static LootrLootState load(@Nullable CompoundTag tag, Level level) {
        LootrLootState state = new LootrLootState(27);
        state.load(tag == null ? new CompoundTag() : tag, level.registryAccess());
        return state;
    }

    @Override
    public boolean has(Object holder) {
        return tagOf(holder) != null;
    }

    @Override
    @Nullable
    public LootrLootState peek(Object holder, Level level) {
        CompoundTag tag = tagOf(holder);
        return tag == null || tag.isEmpty() ? null : load(tag, level);
    }

    @Override
    public LootrLootState getOrCreate(Object holder, Level level) {
        return load(tagOf(holder), level);
    }

    @Override
    public long firstGenerated(Object holder) {
        CompoundTag tag = tagOf(holder);
        return tag == null ? -1 : LootrLootState.peekFirstGeneratedGameTime(tag);
    }

    @Override
    public void save(Object holder, LootrLootState state, Level level) {
        CompoundTag tag = new CompoundTag();
        state.save(tag, level.registryAccess());
        if (holder instanceof BlockEntity blockEntity) {
            blockEntity.setAttached(ModAttachments.LOOT_STATE, tag);
            blockEntity.setChanged();
        } else if (holder instanceof Entity entity) {
            entity.setAttached(ModAttachments.LOOT_STATE, tag);
        } else {
            throw new IllegalArgumentException("Not an attachment holder: " + holder);
        }
    }
}
