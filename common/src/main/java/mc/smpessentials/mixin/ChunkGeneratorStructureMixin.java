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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

    // With no structures generated there is nothing to find, so answer immediately.
    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void onFindNearestMapStructure(ServerLevel level, HolderSet<Structure> wantedStructures,
                                            BlockPos pos, int maxSearchRadius, boolean createReference,
                                            CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir) {
        String dimId = level.dimension().identifier().toString();
        if (DimManager.isEtherDim(dimId) && !DimManager.etherStructuresEnabled(dimId)) {
            cir.setReturnValue(null);
        }
    }

    /*
     * Caps how far the search may walk in an ether dim, whether or not structures are on.
     *
     * findNearestMapStructure walks rings 0..maxSearchRadius and only stops early once a ring finds
     * something. Vanilla passes 100 from /locate, which is roughly 4 * 100 * 100 candidate chunks,
     * and each candidate blocks the server thread on a chunk scan plus a noise-column placement
     * check. In the overworld a village turns up in the first ring or two. An ether dim is mostly
     * void, so the wanted structure is usually absent and the full walk runs: measured at over 60
     * seconds on a 26.2 Fabric server, which trips the 60 second watchdog and kills the server.
     *
     * 8 rings is roughly 4 * 8 * 8 = 256 candidates. The crashed run proves a candidate costs at
     * least 1.5 ms (over 60 s for fewer than 40400 of them), so 256 is on the order of a second, and
     * stays under the watchdog even if a candidate turns out to be several times more expensive than
     * that floor. The search just reports nothing found past that range.
     */
    private static final int ETHER_MAX_SEARCH_RADIUS = 8;

    // Mixin only accepts the bare value or the value followed by every parameter of the target,
    // and the dimension is needed here, so the full list is repeated.
    @ModifyVariable(method = "findNearestMapStructure", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int clampEtherSearchRadius(int radius, ServerLevel level, HolderSet<Structure> wantedStructures,
                                        BlockPos pos, int maxSearchRadius, boolean createReference) {
        if (!DimManager.isEtherDim(level.dimension().identifier().toString())) return radius;
        return Math.min(radius, ETHER_MAX_SEARCH_RADIUS);
    }
}
