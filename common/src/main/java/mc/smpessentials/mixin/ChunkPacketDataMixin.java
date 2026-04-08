package mc.smpessentials.mixin;

import mc.smpessentials.antixray.AntiXrayEngine;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class ChunkPacketDataMixin {

    @Mutable
    @Shadow
    @Final
    private byte[] buffer;

    @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("TAIL"))
    private void quackedsmp$obfuscateChunk(LevelChunk chunk, CallbackInfo ci) {
        byte[] obfuscated = AntiXrayEngine.obfuscate(chunk);
        if (obfuscated != null) {
            this.buffer = obfuscated;
        }
    }
}
