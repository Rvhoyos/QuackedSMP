package mc.smpessentials.dashboard;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.config.ConfigData;
import mc.smpessentials.config.ConfigIO;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Map;

import static mc.smpessentials.dashboard.AdminHandler.err;

/**
 * HTTP routes for the kit config tree. Kits carry ItemStack JSON, so they travel through Gson in
 * the same shape quackedsmp.json uses rather than through the hand-rolled writer the general
 * config route uses. Items and effects themselves belong to {@link ItemHandler}.
 */
public final class KitHandler {

    private KitHandler() {}

    /** GET /api/admin/kits. Every kit plus the settings that apply to all of them. */
    public static String handleGet(String method, Map<String, String> headers, String body,
                                   MinecraftServer server) {
        String denied = ItemHandler.deny(method, "GET", headers, server);
        if (denied != null) return denied;

        JsonObject out = new JsonObject();
        out.addProperty("enabled", SmpConfig.KITS_ENABLED);
        out.addProperty("cooldownSeconds", SmpConfig.KIT_COOLDOWN_SECONDS);
        out.add("kits", ConfigIO.gson().toJsonTree(SmpConfig.KIT_DEFINITIONS));
        return out.toString();
    }

    /** POST /api/admin/kits/save. Replaces the kit list, then writes quackedsmp.json. */
    public static String handleSave(String method, Map<String, String> headers, String body,
                                    MinecraftServer server) {
        String denied = ItemHandler.deny(method, "POST", headers, server);
        if (denied != null) return denied;

        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            if (req.has("enabled")) {
                SmpConfig.KITS_ENABLED = req.get("enabled").getAsBoolean();
            }
            if (req.has("cooldownSeconds")) {
                SmpConfig.KIT_COOLDOWN_SECONDS = Math.max(0, req.get("cooldownSeconds").getAsLong());
            }

            ConfigData.KitDef[] kits =
                    ConfigIO.gson().fromJson(req.get("kits"), ConfigData.KitDef[].class);
            if (kits == null) return err(400, "Missing kits");

            String problem = firstProblem(kits, server);
            if (problem != null) return err(400, problem);

            SmpConfig.KIT_DEFINITIONS = new ArrayList<>(java.util.Arrays.asList(kits));
            ConfigIO.save();
            return "{\"ok\":true}";
        } catch (RuntimeException e) {
            return err(400, "Invalid kits: " + e.getMessage());
        }
    }

    /**
     * Runs every stored stack back through ItemStack.CODEC and reports the first one the game
     * will not accept. This is what stops an editor writing something that only fails later,
     * silently, when a player claims the kit.
     */
    private static String firstProblem(ConfigData.KitDef[] kits, MinecraftServer server) {
        for (ConfigData.KitDef kit : kits) {
            if (kit == null) continue;
            if (kit.name == null || kit.name.isBlank()) return "Every kit needs a name";

            String armor = firstUnreadableArmor(kit, server);
            if (armor != null) return kit.name + ": " + armor;

            if (kit.items == null) continue;
            for (ConfigData.KitItem item : kit.items) {
                if (item == null) continue;
                String bad = ItemHandler.describeIfUnreadable(item.stack, server);
                if (bad != null) return kit.name + ": " + bad;
            }
        }
        return null;
    }

    private static String firstUnreadableArmor(ConfigData.KitDef kit, MinecraftServer server) {
        if (kit.armor == null) return null;
        String head  = ItemHandler.describeIfUnreadable(kit.armor.head, server);
        if (head != null) return "head: " + head;
        String chest = ItemHandler.describeIfUnreadable(kit.armor.chest, server);
        if (chest != null) return "chest: " + chest;
        String legs  = ItemHandler.describeIfUnreadable(kit.armor.legs, server);
        if (legs != null) return "legs: " + legs;
        String feet  = ItemHandler.describeIfUnreadable(kit.armor.feet, server);
        if (feet != null) return "feet: " + feet;
        return null;
    }
}
