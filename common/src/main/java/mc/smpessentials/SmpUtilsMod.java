package mc.smpessentials;

import org.apache.logging.log4j.Logger;
import mc.smpessentials.commands.CommandRegistrar;
import org.apache.logging.log4j.LogManager;
import mc.smpessentials.events.JoinMessageHandler;
public final class SmpUtilsMod {
    public static final String MOD_ID = "quacksmp";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);


    public static void init() {
        // Write common init code here.
        LOGGER.info("QuackedSMP Plugin initialized");
        CommandRegistrar.init(); 
        JoinMessageHandler.init();

    }
}
