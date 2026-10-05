package net.lootr.serveronly.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.lootr.serveronly.common.LootrServerOnlyConstants;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LootrHooks {
    public interface Provider {
        boolean blocksUnpack(RandomizableContainerBlockEntity container);

        boolean blocksCartUnpack(AbstractMinecartContainer cart);

        boolean isClearingCartTable();

        Optional<Float> blastResistantResistance(Optional<Float> base, BlockGetter reader, BlockPos pos, BlockState state);

        boolean isManagedAt(Level level, BlockPos pos);

        boolean isManagedFrame(ItemFrame frame);

        boolean brushableIsManaged(BrushableBlockEntity be, @Nullable ResourceKey<LootTable> lootTable);

        boolean brushableAlreadyLooted(BrushableBlockEntity be, @Nullable ResourceKey<LootTable> lootTable, Player player);

        void brushableComplete(BrushableBlockEntity be, @Nullable ResourceKey<LootTable> lootTable, Player player);
    }

    private static volatile Provider provider;
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    public static void install(Provider installed) {
        provider = installed;
    }

    @Nullable
    private static Provider provider() {
        Provider current = provider;
        if (current == null && WARNED.compareAndSet(false, true)) {
            LootrServerOnlyConstants.LOGGER.error("LootrHooks used before ContainerProtection.installHooks() ran; "
                    + "loot protection is inactive until it does. This is a wiring bug in the mod.");
        }
        return current;
    }

    public static boolean blocksUnpack(RandomizableContainerBlockEntity container) {
        Provider p = provider();
        return p != null && p.blocksUnpack(container);
    }

    public static boolean blocksCartUnpack(AbstractMinecartContainer cart) {
        Provider p = provider();
        return p != null && p.blocksCartUnpack(cart);
    }

    public static boolean isClearingCartTable() {
        Provider p = provider();
        return p != null && p.isClearingCartTable();
    }

    public static boolean isNotManagedCart(Entity entity) {
        return !(entity instanceof AbstractMinecartContainer cart && blocksCartUnpack(cart));
    }

    public static Optional<Float> blastResistantResistance(Optional<Float> base, BlockGetter reader, BlockPos pos,
                                                           BlockState state) {
        Provider p = provider();
        return p == null ? base : p.blastResistantResistance(base, reader, pos, state);
    }

    public static boolean isManagedAt(Level level, BlockPos pos) {
        Provider p = provider();
        return p != null && p.isManagedAt(level, pos);
    }

    public static boolean isManagedFrame(ItemFrame frame) {
        Provider p = provider();
        return p != null && p.isManagedFrame(frame);
    }

    public static boolean brushableIsManaged(BrushableBlockEntity be, @Nullable ResourceKey<LootTable> lootTable) {
        Provider p = provider();
        return p != null && p.brushableIsManaged(be, lootTable);
    }

    public static boolean brushableAlreadyLooted(BrushableBlockEntity be, @Nullable ResourceKey<LootTable> lootTable,
                                                 Player player) {
        Provider p = provider();
        return p != null && p.brushableAlreadyLooted(be, lootTable, player);
    }

    public static void brushableComplete(BrushableBlockEntity be, @Nullable ResourceKey<LootTable> lootTable,
                                         Player player) {
        Provider p = provider();
        if (p != null) {
            p.brushableComplete(be, lootTable, player);
        }
    }

    private LootrHooks() {}
}
