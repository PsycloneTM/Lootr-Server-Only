package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.ContainerProtection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric API has no callback for "is this entity immune to this damage", so
 * this hooks {@code Entity.isInvulnerableTo} directly (the NeoForge side uses
 * {@code EntityInvulnerabilityCheckEvent}, which fires from the same method).
 * <p>
 * Every hurt path a marked item frame or loot chest minecart can take goes
 * through that one method - {@code ItemFrame.hurt}, {@code VehicleEntity.hurt}
 * and {@code BlockAttachedEntity.hurt} each call it first - and no entity
 * subclass overrides it (checked against the 1.21.1 sources), so arrows,
 * explosions, fire and mobs are all covered. A creative player's hit is let
 * through so an admin can still remove one, via vanilla's own
 * {@code DamageSource.isCreativePlayer}.
 * <p>
 * This runs on every damage check for every entity, so the real decision is
 * in {@link ContainerProtection#isManagedEntity}, which rejects on a cheap
 * type test first. Not verified by a build.
 */
@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void lootr$protectLootEntities(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!source.isCreativePlayer() && ContainerProtection.isManagedEntity((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
