package mc.smpessentials;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class SmpUtilsMod {
    public static final String MOD_ID = "quacksmp";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static void init() {
        // Write common init code here.
        LOGGER.info("QuackedSMP Plugin initialized");
        mc.smpessentials.config.SmpConfig.load();
        mc.smpessentials.skills.SkillEvents.init(); // Still logs init message
        mc.smpessentials.bluemap.BlueMapIntegration.init();
        mc.smpessentials.voicechat.VoicechatIntegration.init();
        mc.smpessentials.dashboard.DashboardManager.init();
        purgeOldModVersions();
    }

    /**
     * Scans the mods/ directory for stale QuackedSMP JARs and deletes any that are
     * not the currently running one. Runs at startup so uploading a new version via
     * the mod manager automatically cleans up the old JAR on the next restart.
     */
    public static void purgeOldModVersions() {
        try {
            Optional<Path> activeOpt = mc.smpessentials.platform.SmpServices.PLATFORM.getActiveModJarPath();
            if (activeOpt.isEmpty()) return;
            Path activeJar = activeOpt.get().toAbsolutePath().normalize();

            Path modsDir = Path.of("mods").toAbsolutePath().normalize();
            if (!Files.isDirectory(modsDir)) return;

            try (var stream = Files.list(modsDir)) {
                stream
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return (n.startsWith("quacksmp") || n.startsWith("quackedsmp")) && n.endsWith(".jar");
                    })
                    .filter(p -> !p.toAbsolutePath().normalize().equals(activeJar))
                    .forEach(old -> {
                        try {
                            Files.delete(old);
                            LOGGER.info("[QuackedSMP] Removed old version {} — {} is now the active JAR",
                                    old.getFileName(), activeJar.getFileName());
                        } catch (Exception e) {
                            LOGGER.warn("[QuackedSMP] Could not remove old JAR {}: {}", old.getFileName(), e.getMessage());
                        }
                    });
            }
        } catch (Exception e) {
            LOGGER.warn("[QuackedSMP] purgeOldModVersions failed: {}", e.getMessage());
        }
    }
}
