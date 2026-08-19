package mc.smpessentials.mixin;

import com.mojang.datafixers.util.Pair;
import mc.smpessentials.dims.DimManager;
import mc.smpessentials.dims.EtherStructureFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * Applies the ether dimension's structure rules. Vanilla ChunkGenerator is the only hook point:
 * NoiseBasedChunkGenerator is final on Fabric, so it cannot be subclassed.
 * Every injection is a no-op outside ether dims.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorStructureMixin {

    // Skips structure placement entirely when the dim was created with structures off.
    @Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    private void onCreateStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state,
                                     StructureManager structureManager, ChunkAccess centerChunk,
                                     StructureTemplateManager templateManager, ResourceKey<Level> level,
                                     CallbackInfo ci) {
        String dimId = level.identifier().toString();
        if (DimManager.isEtherDim(dimId) && !DimManager.etherStructuresEnabled(dimId)) {
            ci.cancel();
        }
    }

    // Drops whatever vanilla just placed into open sky.
    @Inject(method = "createStructures", at = @At("RETURN"))
    private void afterCreateStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state,
                                        StructureManager structureManager, ChunkAccess centerChunk,
                                        StructureTemplateManager templateManager, ResourceKey<Level> level,
                                        CallbackInfo ci) {
        String dimId = level.identifier().toString();
        if (!DimManager.isEtherDim(dimId) || !DimManager.etherStructuresEnabled(dimId)) return;
        EtherStructureFilter.discardFloatingStarts(
                (ChunkGenerator) (Object) this, state, structureManager, centerChunk);
    }

    // With no structures generated there is nothing to find, and vanilla's search would walk
    // thousands of candidate chunks on the server thread until the watchdog kills the server.
    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void onFindNearestMapStructure(ServerLevel level, HolderSet<Structure> wantedStructures,
                                            BlockPos pos, int maxSearchRadius, boolean createReference,
                                            CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        String dimId = level.dimension().identifier().toString();
        if (DimManager.isEtherDim(dimId) && !DimManager.etherStructuresEnabled(dimId)) {
            cir.setReturnValue(null);
        }
    }
}
