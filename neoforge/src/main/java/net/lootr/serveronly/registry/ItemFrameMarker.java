package net.lootr.serveronly.registry;

import net.lootr.serveronly.config.LootrConfig;
import net.lootr.serveronly.mixin.AccessorItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public final class ItemFrameMarker {

    public static final String TAG = "lootr_serveronly.loot_frame";

    public static boolean isMarked(ItemFrame frame) {
        return frame.getTags().contains(TAG);
    }

    public static void mark(ItemFrame frame) {
        frame.addTag(TAG);
    }

    public static void onStructureFrameSpawned(ItemFrame frame) {
        if (!LootrConfig.isDisabled() && LootrConfig.CONVERT_ITEM_FRAMES.get() && isEligibleStructureFrame(frame)) {
            mark(frame);
        }
    }

    public static void onEndCityElytraFrameSpawned(ItemFrame frame) {
        if (!LootrConfig.isDisabled() && LootrConfig.CONVERT_ELYTRAS_TO_ITEM_FRAMES.get() && frame.getItem().is(Items.ELYTRA)) {
            mark(frame);
        }
    }

    private static boolean isEligibleStructureFrame(ItemFrame frame) {
        return ineligibleReason(frame) == null;
    }

    @Nullable
    public static String ineligibleReason(ItemFrame frame) {
        if (((AccessorItemFrame) frame).lootr$isFixed()) {
            return "it is a fixed frame";
        }
        if (frame.isInvisible()) {
            return "it is invisible";
        }
        ItemStack framed = frame.getItem();
        if (framed.isEmpty()) {
            return "it is empty";
        }
        if (framed.is(Items.FILLED_MAP) || framed.is(Items.MAP)) {
            return "it holds a map";
        }
        return null;
    }

    public static void unmark(ItemFrame frame) {
        frame.removeTag(TAG);
    }

    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> ELYTRA_CHEST_TABLE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("lootr_serveronly", "chests/end_city_elytra"));

    public static boolean convertElytraToChest(net.minecraft.world.level.ServerLevelAccessor level, ItemFrame frame) {
        if (LootrConfig.isDisabled() || !LootrConfig.convertElytrasToChests() || LootrConfig.convertElytrasToItemFrames()
                || !frame.getItem().is(Items.ELYTRA)) {
            return false;
        }
        net.minecraft.core.BlockPos chestPos = frame.getPos().below();
        level.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, frame.getDirection()), 3);
        if (level.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity chest) {
            chest.setLootTable(ELYTRA_CHEST_TABLE, level.getRandom().nextLong());
        }
        return true;
    }

    private ItemFrameMarker() {}
}
