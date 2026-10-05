package net.lootr.serveronly.interaction;

import net.lootr.serveronly.registry.ModLootTags;
import net.lootr.serveronly.config.LootrSettings;
import net.lootr.serveronly.registry.ItemFrameMarker;
import net.lootr.serveronly.mixin.AccessorBrushableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public final class ContainerProtection {
    public static boolean isManaged(@Nullable BlockEntity be) {
        if (be instanceof RandomizableContainerBlockEntity container) {
            return ModLootTags.isLootrEnabled(container);
        }
        if (be instanceof BrushableBlockEntity brushable) {
            return ModLootTags.isTableEnabled(((AccessorBrushableBlockEntity) brushable).lootr$getLootTable());
        }
        if (be instanceof DecoratedPotBlockEntity pot) {
            return ModLootTags.isTableEnabled(pot.getLootTable());
        }
        return false;
    }

    private static boolean isSmallBlast(DamageSource source) {
        Entity cause = source.getDirectEntity();
        return cause instanceof PrimedTnt || (cause instanceof Creeper creeper && !creeper.isPowered());
    }

    public static boolean breakExplicitlyAllowed(Player player) {
        return LootrSettings.enableBreak() || (isFakePlayer(player) && LootrSettings.enableFakePlayerBreak());
    }

    public static boolean isManagedEntity(Entity entity, DamageSource source) {
        if (!(entity instanceof ItemFrame) && !(entity instanceof MinecartChest)) {
            return false;
        }
        if (entity instanceof MinecartChest && source.getEntity() instanceof Player breaker
                && breakExplicitlyAllowed(breaker)) {
            return false;
        }
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        if (!LootrSettings.protectContainers()
                && !(explosion && (LootrSettings.blastImmune() || (LootrSettings.blastResistant() && isSmallBlast(source))))) {
            return false;
        }
        if (!(entity.level() instanceof ServerLevel level)
                || !LootrSettings.isDimensionEnabled(level.dimension())) {
            return false;
        }
        if (entity instanceof ItemFrame frame) {
            return ItemFrameMarker.isMarked(frame) && !frame.getItem().isEmpty();
        }
        return ModLootTags.isTableEnabled(((MinecartChest) entity).getLootTable());
    }

    public static boolean isManagedFrame(ItemFrame frame) {
        return frame.level() instanceof ServerLevel level
                && LootrSettings.isDimensionEnabled(level.dimension())
                && ItemFrameMarker.isMarked(frame)
                && !frame.getItem().isEmpty();
    }

    public static boolean isManagedAt(Level level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel
                && LootrSettings.isDimensionEnabled(serverLevel.dimension())
                && isManaged(level.getBlockEntity(pos));
    }

    public static boolean blocksUnpack(RandomizableContainerBlockEntity container) {
        return ModLootTags.isLootrEnabled(container)
                && container.getLevel() instanceof ServerLevel level
                && LootrSettings.isDimensionEnabled(level.dimension());
    }

    private static boolean clearingCartTable = false;

    public static boolean isClearingCartTable() {
        return clearingCartTable;
    }

    public static void clearCartLootTable(AbstractMinecartContainer cart) {
        clearingCartTable = true;
        try {
            cart.setLootTable(null);
        } finally {
            clearingCartTable = false;
        }
    }

    public static boolean blocksCartUnpack(AbstractMinecartContainer cart) {
        return cart instanceof MinecartChest
                && cart.getLootTable() != null
                && cart.level() instanceof ServerLevel level
                && LootrSettings.isDimensionEnabled(level.dimension())
                && ModLootTags.isTableEnabled(cart.getLootTable());
    }

    public static void installHooks() {
        net.lootr.serveronly.interaction.LootrHooks.install(new net.lootr.serveronly.interaction.LootrHooks.Provider() {
            @Override public boolean blocksUnpack(RandomizableContainerBlockEntity container) {
                return ContainerProtection.blocksUnpack(container);
            }
            @Override public boolean blocksCartUnpack(AbstractMinecartContainer cart) {
                return ContainerProtection.blocksCartUnpack(cart);
            }
            @Override public boolean isClearingCartTable() {
                return ContainerProtection.isClearingCartTable();
            }
            @Override public java.util.Optional<Float> blastResistantResistance(java.util.Optional<Float> base,
                    net.minecraft.world.level.BlockGetter reader, BlockPos pos,
                    net.minecraft.world.level.block.state.BlockState state) {
                return ContainerProtection.blastResistantResistance(base, reader, pos, state);
            }
            @Override public boolean isManagedAt(Level level, BlockPos pos) {
                return ContainerProtection.isManagedAt(level, pos);
            }
            @Override public boolean isManagedFrame(ItemFrame frame) {
                return ContainerProtection.isManagedFrame(frame);
            }
            @Override public boolean brushableIsManaged(BrushableBlockEntity be,
                    @Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> lootTable) {
                return BrushableLoot.isManaged(be, lootTable);
            }
            @Override public boolean brushableAlreadyLooted(BrushableBlockEntity be,
                    @Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> lootTable,
                    Player player) {
                return BrushableLoot.alreadyLooted(be, lootTable, player);
            }
            @Override public void brushableComplete(BrushableBlockEntity be,
                    @Nullable net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> lootTable,
                    Player player) {
                BrushableLoot.complete(be, lootTable, player);
            }
        });
    }

    public static final float BLAST_RESISTANT_RESISTANCE = 16.0F;

    public static boolean blastSpares() {
        return LootrSettings.protectContainers()
                || LootrSettings.blastImmune();
    }

    public static java.util.Optional<Float> blastResistantResistance(java.util.Optional<Float> base,
                                                                      net.minecraft.world.level.BlockGetter reader,
                                                                      BlockPos pos,
                                                                      net.minecraft.world.level.block.state.BlockState state) {
        if (!LootrSettings.blastResistant() || base.isEmpty() || base.get() >= BLAST_RESISTANT_RESISTANCE
                || !state.hasBlockEntity() || !(reader instanceof Level level)
                || !isManagedAt(level, pos)) {
            return base;
        }
        return java.util.Optional.of(BLAST_RESISTANT_RESISTANCE);
    }

    private static volatile Predicate<Player> fakePlayerCheck = player -> false;

    public static void installFakePlayerCheck(Predicate<Player> check) {
        fakePlayerCheck = check;
    }

    public static boolean isFakePlayer(Player player) {
        return fakePlayerCheck.test(player)
                || (player instanceof ServerPlayer sp && sp.connection == null);
    }

    public static void handleBreak(Player player, ServerLevel level, BlockPos pos, Runnable cancel) {
        if (player.isSpectator() || !isManagedAt(level, pos)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        RandomizableContainerBlockEntity container = be instanceof RandomizableContainerBlockEntity c ? c : null;
        ServerPlayer serverPlayer = player instanceof ServerPlayer sp ? sp : null;
        boolean fake = isFakePlayer(player);
        boolean sneaking = player.isShiftKeyDown();
        boolean creative = player.isCreative();
        boolean strict = LootrSettings.disableBreak();

        boolean allowed = LootrSettings.enableBreak() || (fake && LootrSettings.enableFakePlayerBreak());
        if (!allowed && (strict || !creative)) {
            if (LootrSettings.breakToDropLoot() && !sneaking && !fake
                    && container != null && serverPlayer != null
                    && ContainerInteractionHandler.kindOf(container) != null) {
                ContainerInteractionHandler.collectLoot(serverPlayer, container, level);
                cancel.run();
                return;
            }
            if (strict) {
                if (!creative) {
                    cancel.run();
                    player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay("Loot containers can't be broken."), true);
                    return;
                }
                if (!sneaking) {
                    cancel.run();
                    player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay(
                            "Sneak while breaking to destroy this loot container for everyone."), true);
                    return;
                }
            } else if (LootrSettings.protectContainers()) {
                cancel.run();
                player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay("Loot containers can't be broken."), true);
                return;
            } else if (LootrSettings.requireSneakToBreak() && !sneaking) {
                cancel.run();
                player.displayClientMessage(net.lootr.serveronly.config.MessageStyles.decay(
                        "Sneak while breaking to destroy this loot container for everyone."), true);
                return;
            }
        }

        if (LootrSettings.shouldDropPlayerLoot() && container != null && serverPlayer != null && !fake
                && ContainerInteractionHandler.kindOf(container) != null) {
            for (ItemStack stack : ContainerInteractionHandler.takeLoot(serverPlayer, container, level)) {
                Block.popResource(level, pos, stack);
            }
        }
    }

    private ContainerProtection() {}
}
