package mc.smpessentials.commands;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class EndResetLogic {

    public static int resetDragon(MinecraftServer server, boolean activePortal) {
        ServerLevel endLevel = server.getLevel(Level.END);
        if (endLevel == null)
            return 0;

        EndDragonFight fight = endLevel.getDragonFight();
        if (fight == null)
            return 0;

        try {
            // 1. Manage the exit portal blocks
            invokePrivateMethod(fight, "spawnExitPortal", new Class[] { boolean.class }, new Object[] { activePortal });

            // 2. Reset the internal state
            setPrivateField(fight, "dragonKilled", false);
            setPrivateField(fight, "previouslyKilled", false);
            setPrivateField(fight, "dragonUUID", null);
            setPrivateField(fight, "needsStateScanning", false);
            setPrivateField(fight, "respawnStage", null);

            // 3. Reset crystals on spikes
            fight.resetSpikeCrystals();

            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static int resetWorld(MinecraftServer server) {
        ServerLevel endLevel = server.getLevel(Level.END);
        if (endLevel == null)
            return 0;

        // 1. Teleport players out
        List<ServerPlayer> playersInEnd = endLevel.players();
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getRespawnData().pos();

        for (ServerPlayer player : playersInEnd) {
            BlockPos safe = player.adjustSpawnLocation(overworld, spawn);
            player.teleportTo(overworld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, java.util.Set.of(),
                    player.getYRot(), player.getXRot(), false);
            player.sendSystemMessage(Component.literal("\u00a7eThe End is being reset. Teleporting to spawn..."));
        }

        try {
            // 2. Flush all IO and wait for completion
            endLevel.save(null, true, false);

            Object chunkSource = endLevel.getChunkSource();
            Field chunkMapField = chunkSource.getClass().getDeclaredField("chunkMap");
            chunkMapField.setAccessible(true);
            Object chunkMap = chunkMapField.get(chunkSource);

            // Wait for activeWrites to finish (AtomicInteger)
            Field activeWritesField = chunkMap.getClass().getDeclaredField("activeChunkWrites");
            activeWritesField.setAccessible(true);
            java.util.concurrent.atomic.AtomicInteger activeWrites = (java.util.concurrent.atomic.AtomicInteger) activeWritesField
                    .get(chunkMap);

            int timeout = 100; // ~5 seconds
            while (activeWrites.get() > 0 && timeout > 0) {
                Thread.sleep(50);
                timeout--;
            }

            // 3. Force Unload all chunks
            Field updatingMapField = chunkMap.getClass().getDeclaredField("updatingChunkMap");
            updatingMapField.setAccessible(true);
            it.unimi.dsi.fastutil.longs.Long2ObjectMap<?> updatingMap = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>) updatingMapField
                    .get(chunkMap);

            Field toDropField = chunkMap.getClass().getDeclaredField("toDrop");
            toDropField.setAccessible(true);
            it.unimi.dsi.fastutil.longs.LongSet toDrop = (it.unimi.dsi.fastutil.longs.LongSet) toDropField
                    .get(chunkMap);

            // Add all currently loaded chunks to the drop list
            toDrop.addAll(updatingMap.keySet());

            // Process unloads until everything is out of updatingMap
            java.lang.reflect.Method processUnloads = chunkMap.getClass().getDeclaredMethod("processUnloads",
                    java.util.function.BooleanSupplier.class);
            processUnloads.setAccessible(true);

            // Call it until maps are empty
            int unloadAttempts = 10;
            while (!updatingMap.isEmpty() && unloadAttempts-- > 0) {
                processUnloads.invoke(chunkMap, (java.util.function.BooleanSupplier) () -> true);
                Thread.sleep(10);
            }

            // 4. Delete dimension file contents safely
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            Path dim1Dir = worldDir.resolve("DIM1");

            if (Files.exists(dim1Dir)) {
                // We delete contents but keep directories to avoid background IO errors
                deleteDirectoryContents(dim1Dir.resolve("region"));
                deleteDirectoryContents(dim1Dir.resolve("data"));
                deleteDirectoryContents(dim1Dir.resolve("poi"));
            }

            // 5. Reset Dragon Fight state / Re-initialize
            Class<?> dataClass = net.minecraft.world.level.dimension.end.EndDragonFight.Data.class;
            java.lang.reflect.Constructor<?> dataConst = dataClass.getConstructors()[0];
            Object[] dataArgs = new Object[dataConst.getParameterCount()];
            for (int i = 0; i < dataArgs.length; i++) {
                Class<?> pType = dataConst.getParameterTypes()[i];
                if (pType == boolean.class)
                    dataArgs[i] = false;
                else if (pType == java.util.Optional.class)
                    dataArgs[i] = java.util.Optional.empty();
                else
                    dataArgs[i] = null;
            }
            net.minecraft.world.level.dimension.end.EndDragonFight.Data emptyData = (net.minecraft.world.level.dimension.end.EndDragonFight.Data) dataConst
                    .newInstance(dataArgs);

            server.getWorldData().setEndDragonFightData(emptyData);

            // Re-instantiate dragon fight in the Level
            long worldSeed = server.getWorldData().worldGenOptions().seed();
            EndDragonFight newFight = new EndDragonFight(endLevel, worldSeed, emptyData);

            Field dragonFightField = ServerLevel.class.getDeclaredField("dragonFight");
            dragonFightField.setAccessible(true);
            dragonFightField.set(endLevel, newFight);

            // 6. Re-activate portal precisely using Vanilla logic
            // Force-load chunk (0,0) first to ensure generation is ready
            endLevel.getChunkSource().getChunk(0, 0, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);

            // Instead of guessing, we use the fight's own scanning logic
            // findExitPortal() returns BlockPatternMatch and sets the internal
            // portalLocation field
            Object match = invokePrivateMethod(newFight, "findExitPortal", new Class[0], new Object[0]);

            if (match != null) {
                // Vanilla pattern match sets portalLocation to the TOP bedrock block (Y+3).
                // But spawnExitPortal/place expects the portal level (origin Y).
                // We must shift it down by 3 to achieve perfect alignment/overlap.
                BlockPos topPos = (BlockPos) getPrivateField(newFight, "portalLocation");
                if (topPos != null) {
                    setPrivateField(newFight, "portalLocation", topPos.below(3));
                }
            }

            // Now call spawnExitPortal(true) - if still null, it will vanilla-scan the
            // fresh chunk.
            invokePrivateMethod(newFight, "spawnExitPortal", new Class[] { boolean.class }, new Object[] { true });

            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private static void setPrivateField(Object obj, String fieldName, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    private static Object getPrivateField(Object obj, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }

    private static Object invokePrivateMethod(Object obj, String methodName, Class<?>[] parameterTypes, Object[] args)
            throws Exception {
        java.lang.reflect.Method method = obj.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(obj, args);
    }

    private static void deleteDirectoryContents(Path path) throws IOException {
        if (!Files.exists(path))
            return;
        try (java.util.stream.Stream<Path> stream = Files.list(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            if (Files.isDirectory(p)) {
                                deleteDirectory(p); // Recursive delete for sub-dirs
                            } else {
                                Files.delete(p);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path))
            return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
