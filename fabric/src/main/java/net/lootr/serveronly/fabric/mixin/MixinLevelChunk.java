package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.ChunkDiscovery;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk {
    @Inject(method = "registerTickContainerInLevel", at = @At("RETURN"))
    private void lootr$discoverLootContainers(ServerLevel level, CallbackInfo ci) {
        ChunkDiscovery.onChunkLoaded(level, (LevelChunk) (Object) this);
    }
}
