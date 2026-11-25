package mc.smpessentials.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.concurrent.Executor;

// Exposes MinecraftServer internals needed by DimManager (runtime dimension creation) and by
// PregenRunner (the startup chunk run, which has to keep the watchdog fed the way vanilla's own
// prepareLevels does).
@Mixin(MinecraftServer.class)
public interface MinecraftServerMixin {

    // Live dimension map; mutating it adds/removes active levels.
    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getLevels();

    // Background executor used when constructing new ServerLevels.
    @Accessor("executor")
    Executor getExecutor();

    // World storage access needed for ServerLevel construction.
    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getStorageSource();

    // The deadline the server watchdog measures against. Long work on the server thread has to
    // push it forward or the watchdog calls the tick crashed and forcibly shuts the server down.
    @Accessor("nextTickTimeNanos")
    void setNextTickTimeNanos(long nanos);

    // Runs pending main-thread tasks and parks until the deadline above. Paired with the setter,
    // this is the loop vanilla's prepareLevels uses to load chunks without tripping the watchdog.
    @Invoker("waitUntilNextTick")
    void invokeWaitUntilNextTick();
}
