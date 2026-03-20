package mc.smpessentials.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Exposes private fields of {@link MinecraftServer} needed for runtime
 * dimension creation and deletion in {@link mc.smpessentials.dims.DimManager}.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerMixin {

    /** The live dimension map — mutating it adds/removes active levels. */
    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getLevels();

    /** Background executor used when constructing new ServerLevels. */
    @Accessor("executor")
    Executor getExecutor();

    /** World storage access needed for ServerLevel construction. */
    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getStorageSource();
}
