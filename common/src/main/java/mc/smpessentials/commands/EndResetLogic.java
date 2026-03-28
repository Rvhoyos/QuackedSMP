package mc.smpessentials.commands;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.Field;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

// Logic for resetting the End dimension.
// Dragon-only resets run live; terrain resets are queued via a RESET_PENDING marker file
// and executed on shutdown to avoid file-lock issues.
public class EndResetLogic {

    // Returns 1 if successful, 0 if End level not found, -1 on error.
    public static int resetDragon(MinecraftServer server, boolean activePortal) {
        Level endLevel = server.getLevel(Level.END);
        if (endLevel == null || !(endLevel instanceof ServerLevel))
            return 0;

        ServerLevel sEndLevel = (ServerLevel) endLevel;


        // 2. Discard existing dragon and crystals and CLEAR boss bar
        cleanUpOldFight(sEndLevel);
        discardEndEntities(sEndLevel);

        EndDragonFight fight = sEndLevel.getDragonFight();
        if (fight == null)
            return 0;

        try {
            // 3. Manage the exit portal blocks
            invokePrivateMethod(fight, "spawnExitPortal", new Class[] { boolean.class }, new Object[] { activePortal });

            // 4. Reset the internal state
            setPrivateField(fight, "dragonKilled", false);
            setPrivateField(fight, "previouslyKilled", false);
            setPrivateField(fight, "dragonUUID", null);
            setPrivateField(fight, "needsStateScanning", false);
            setPrivateField(fight, "respawnStage", null);

            // 5. Reset crystals on spikes
            fight.resetSpikeCrystals();

            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    // Returns 2 if queued, 0 if End level not found, -1 on error.
    public static int resetWorld(MinecraftServer server) {
        Level endLevel = server.getLevel(Level.END);
        if (endLevel == null || !(endLevel instanceof ServerLevel))
            return 0;

        ServerLevel sEndLevel = (ServerLevel) endLevel;

        try {
            // 1. Reset Global WorldData state immediately (saved on next save)
            resetWorldDataDragonState(server);

            // 2. Clear entities and boss bar live (stops the fight ticking)
            cleanUpOldFight(sEndLevel);
            discardEndEntities(sEndLevel);

            // 3. Mark the DIM1 folder for deletion on shutdown
            Path dim1Dir = server.getWorldPath(LevelResource.ROOT).resolve("DIM1");
            Path marker = dim1Dir.resolve("RESET_PENDING");
            if (!Files.exists(dim1Dir)) Files.createDirectories(dim1Dir);
            Files.createFile(marker);

            // 4. Trigger a live dragon fight reset in the current level too
            resetDragon(server, true);

            return 2; // Returns "Queued for restart"
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    // Deletes End region/data/poi files if a RESET_PENDING marker exists. Called from the server-stopped event.
    public static void onServerStopping(MinecraftServer server) {
        try {
            Path dim1Dir = server.getWorldPath(LevelResource.ROOT).resolve("DIM1");
            Path marker = dim1Dir.resolve("RESET_PENDING");
            
            if (Files.exists(marker)) {
                System.out.println("[QuackedSMP] Performing scheduled End dimension reset...");
                
                // At this point, the server is shutting down, so we can attempt to delete files
                deleteDirectoryContents(dim1Dir.resolve("region"));
                deleteDirectoryContents(dim1Dir.resolve("data"));
                deleteDirectoryContents(dim1Dir.resolve("poi"));
                
                Files.deleteIfExists(marker);
                System.out.println("[QuackedSMP] End dimension reset complete.");
            }
        } catch (Exception e) {
            System.err.println("[QuackedSMP] Failed to perform End reset on shutdown: " + e.getMessage());
        }
    }

    // Removes all players from the dragon boss bar so it stops displaying during and after the reset.
    private static void cleanUpOldFight(Level endLevel) {
        if (!(endLevel instanceof ServerLevel))
            return;
        EndDragonFight fight = ((ServerLevel) endLevel).getDragonFight();
        if (fight != null) {
            try {
                Object bossEvent = getPrivateField(fight, "dragonEvent");
                if (bossEvent != null) {
                    java.lang.reflect.Method removeAll = bossEvent.getClass().getMethod("removeAllPlayers");
                    removeAll.invoke(bossEvent);
                }
            } catch (Exception e) {
                // Ignore if field changed, we tried our best
            }
        }
    }


    // Discards all EnderDragon and EndCrystal entities in the level.
    private static void discardEndEntities(Level endLevel) {
        if (!(endLevel instanceof ServerLevel))
            return;
        for (Entity entity : ((ServerLevel) endLevel).getAllEntities()) {
            if (entity instanceof EnderDragon ||
                    entity instanceof EndCrystal) {
                entity.discard();
            }
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

    private static boolean deleteDirectoryContents(Path path) throws IOException {
        if (!Files.exists(path))
            return true;

        final java.util.concurrent.atomic.AtomicBoolean allDeleted = new java.util.concurrent.atomic.AtomicBoolean(
                true);
        try (java.util.stream.Stream<Path> stream = Files.list(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            if (Files.isDirectory(p)) {
                                allDeleted.set(allDeleted.get() && deleteDirectory(p));
                            } else {
                                Files.delete(p);
                            }
                        } catch (IOException e) {
                            allDeleted.set(false);
                            System.err.println("Could not delete file: " + p.toAbsolutePath());
                        }
                    });
        }
        return allDeleted.get();
    }

    private static boolean deleteDirectory(Path path) {
        if (!Files.exists(path))
            return true;
        try {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(f -> {
                        if (!f.delete()) {
                            System.err.println("Could not delete file/dir: " + f.getAbsolutePath());
                        }
                    });
            return !Files.exists(path);
        } catch (IOException e) {
            return false;
        }
    }

    // Resets EndDragonFight.Data in WorldData so the next startup spawns a fresh dragon fight.
    private static void resetWorldDataDragonState(MinecraftServer server) {
        try {
            Class<?> dataClass = net.minecraft.world.level.dimension.end.EndDragonFight.Data.class;
            java.lang.reflect.Constructor<?> dataConst = dataClass.getConstructors()[0];
            Object[] dataArgs = new Object[dataConst.getParameterCount()];
            for (int i = 0; i < dataArgs.length; i++) {
                Class<?> pType = dataConst.getParameterTypes()[i];
                if (pType == boolean.class) {
                    dataArgs[i] = false;
                } else if (pType == java.util.Optional.class) {
                    dataArgs[i] = java.util.Optional.empty();
                } else {
                    dataArgs[i] = null;
                }
            }
            net.minecraft.world.level.dimension.end.EndDragonFight.Data emptyData = 
                (net.minecraft.world.level.dimension.end.EndDragonFight.Data) dataConst.newInstance(dataArgs);

            server.getWorldData().setEndDragonFightData(emptyData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
