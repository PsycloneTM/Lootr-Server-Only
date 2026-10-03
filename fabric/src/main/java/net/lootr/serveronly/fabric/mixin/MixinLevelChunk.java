package net.lootr.serveronly.fabric.mixin;

import net.lootr.serveronly.fabric.interaction.ChunkDiscovery;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chunk-load hook for {@link ChunkDiscovery}. {@code registerTickContainerInLevel} is called exactly once,
 * on the server thread, when a chunk becomes a full level chunk (right next to where NeoForge fires
 * {@code ChunkEvent.Load}). Not verified by a build.
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk {
    @Inject(method = "registerTickContainerInLevel", at = @At("RETURN"))
    private void lootr$discoverLootContainers(ServerLevel level, CallbackInfo ci) {
        ChunkDiscovery.onChunkLoaded(level, (LevelChunk) (Object) this);
    }
}
