package mc.smpessentials;

import org.apache.logging.log4j.Logger;
import mc.smpessentials.commands.CommandRegistrar;
import org.apache.logging.log4j.LogManager;
import mc.smpessentials.events.JoinMessageHandler;
import mc.smpessentials.claims.ClaimProtection;
import mc.smpessentials.chatfilter.ChatFilter;;

public final class SmpUtilsMod {
    public static final String MOD_ID = "quacksmp";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static void init() {
        // Write common init code here.
        LOGGER.info("QuackedSMP Plugin initialized");
        mc.smpessentials.config.SmpConfig.load();
        CommandRegistrar.init(); // Initialize commands
        JoinMessageHandler.init(); // Initialize join message handler
        ClaimProtection.init(); // Initialize claim protection
        ChatFilter.init();
        mc.smpessentials.teleport.TeleportScheduler.init();
        mc.smpessentials.events.MessageScheduler.init();
        mc.smpessentials.skills.SkillEvents.init();
        mc.smpessentials.skills.ActiveAbilities.init();

    }
}
