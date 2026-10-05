package net.lootr.serveronly.mixin;

import net.lootr.serveronly.interaction.LootrHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(EntitySelector.class)
public abstract class MixinEntitySelector {
    @Shadow
    @Final
    @Mutable
    public static Predicate<Entity> CONTAINER_ENTITY_SELECTOR;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void lootr$excludeManagedCart(CallbackInfo ci) {
        CONTAINER_ENTITY_SELECTOR = CONTAINER_ENTITY_SELECTOR.and(LootrHooks::isNotManagedCart);
    }
}
