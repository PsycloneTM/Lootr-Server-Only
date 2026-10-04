package net.lootr.serveronly.data;

import net.lootr.serveronly.registry.ModAttachments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.jetbrains.annotations.Nullable;

public final class NeoLootStateBackend implements LootStateStore.Backend {
    private static IAttachmentHolder holder(Object holder) {
        if (holder instanceof IAttachmentHolder attachments) {
            return attachments;
        }
        throw new IllegalArgumentException("Not an attachment holder: " + holder);
    }

    @Override
    public boolean has(Object holder) {
        return holder instanceof IAttachmentHolder attachments && attachments.hasData(ModAttachments.LOOT_STATE);
    }

    @Override
    @Nullable
    public LootrLootState peek(Object holder, Level level) {
        return has(holder) ? holder(holder).getData(ModAttachments.LOOT_STATE) : null;
    }

    @Override
    public LootrLootState getOrCreate(Object holder, Level level) {
        return holder(holder).getData(ModAttachments.LOOT_STATE);
    }

    @Override
    public long firstGenerated(Object holder) {
        return has(holder) ? holder(holder).getData(ModAttachments.LOOT_STATE).getFirstGeneratedGameTime() : -1;
    }

    @Override
    public void save(Object holder, LootrLootState state, Level level) {
        if (holder instanceof BlockEntity blockEntity) {
            blockEntity.setChanged();
        }
    }
}
