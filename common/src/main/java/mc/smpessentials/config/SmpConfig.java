package mc.smpessentials.config;

import com.google.gson.JsonObject;

public final class SmpConfig {
    public static int MAX_CLAIMS = 50;
    public static int TP_WARMUP = 5;
    public static String WELCOME_MESSAGE = "Welcome to QuackedSMP, {player}!";
    public static java.util.List<String> RULES = new java.util.ArrayList<>();
    public static java.util.List<String> PERIODIC_MESSAGES = new java.util.ArrayList<>();
    public static int MESSAGE_INTERVAL = 300; // Seconds
    public static java.util.List<String> VIPS = new java.util.ArrayList<>();
    public static int VIP_BONUS_CLAIMS = 20;
    public static boolean ALLOW_LAVA_WILDERNESS = false;
    public static java.util.Map<String, String> MESSAGES = new java.util.HashMap<>();

    private SmpConfig() {
    }

    public static void load() {
        JsonObject root = ConfigIO.readOrCreate();
        if (root.has("max_claims")) {
            MAX_CLAIMS = root.get("max_claims").getAsInt();
        }
        if (root.has("tp_warmup")) {
            TP_WARMUP = root.get("tp_warmup").getAsInt();
        }
        if (root.has("welcome_message")) {
            WELCOME_MESSAGE = root.get("welcome_message").getAsString();
        }
        if (root.has("message_interval")) {
            MESSAGE_INTERVAL = root.get("message_interval").getAsInt();
        }
        if (root.has("vip_bonus_claims")) {
            VIP_BONUS_CLAIMS = root.get("vip_bonus_claims").getAsInt();
        }
        if (root.has("allow_lava_wilderness")) {
            ALLOW_LAVA_WILDERNESS = root.get("allow_lava_wilderness").getAsBoolean();
        }

        loadList(root, "vips", VIPS);
        loadList(root, "rules", RULES);
        if (RULES.isEmpty()) {
            RULES.add("&e1. Be respectful.");
            RULES.add("&e2. No griefing inside claims.");
            RULES.add("&e3. Wilderness is dangerous (PvP enabled).");
            RULES.add("&e4. No cheating.");
        }

        loadList(root, "periodic_messages", PERIODIC_MESSAGES);
        if (PERIODIC_MESSAGES.isEmpty()) {
            PERIODIC_MESSAGES.add("&b[Tip] &fUse &a/claim &fto protect your land!");
            PERIODIC_MESSAGES.add("&b[Tip] &fSet your home by sleeping in a bed!");
            PERIODIC_MESSAGES.add("&b[Tip] &fType &a/smp help &ffor commands!");
            PERIODIC_MESSAGES.add("&b[Reminder] &fPlease respect the &6/rules&f!");
            PERIODIC_MESSAGES.add("&b[Tip] &fVisit Spawn Shops for blocks & gear! Trade items for Emeralds!");
        }

        if (root.has("messages") && root.get("messages").isJsonObject()) {
            JsonObject msgs = root.getAsJsonObject("messages");
            for (String key : msgs.keySet()) {
                MESSAGES.put(key, msgs.get(key).getAsString());
            }
        }
    }

    private static void loadList(JsonObject root, String key, java.util.List<String> list) {
        if (root.has(key) && root.get(key).isJsonArray()) {
            list.clear();
            for (var el : root.get(key).getAsJsonArray()) {
                list.add(el.getAsString());
            }
        }
    }
}
