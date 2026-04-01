package mc.smpessentials.chatfilter;

import mc.smpessentials.config.ConfigIO;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads chat filter words and whitelist from quackedsmp.json and merges into
 * SavedData. Also provides hardcoded builtin words so the filter works
 * out of the box without any configuration.
 */
public final class ChatFilterConfig {
    private ChatFilterConfig() {
    }

    /** Hardcoded blocklist; always loaded regardless of configuration. */
    private static final List<String> BUILTIN_WORDS = List.of(
            "fuck", "shit", "ass", "bitch", "damn",
            "dick", "cock", "pussy", "cunt", "whore",
            "slut", "bastard", "nigger", "nigga", "nig", "faggot",
            "retard", "kys", "stfu", "porn", "hentai",
            "penis", "vagina", "tits", "boobs", "cum",
            "jizz", "blowjob", "handjob", "dildo", "anal");

    /**
     * Hardcoded whitelist; prevents common false positives (Scunthorpe problem).
     */
    private static final List<String> BUILTIN_WHITELIST = List.of(
            // -- contains "ass" --
            "assassin", "assault", "assemble", "assert", "assess",
            "asset", "assign", "assist", "associate", "assume",
            "assurance", "assure", "bass", "class", "classic",
            "classify", "compass", "bypass", "embassy", "embarrass",
            "glass", "grass", "harass", "mass", "massive",
            "pass", "passage", "passenger", "passion", "passive",
            "passport", "amass", "brass", "cassette", "lassie",
            "morass", "sass", "sassafras", "trespass", "vassal",
            // -- contains "dick" --
            "benediction", "contradict", "dictate", "diction",
            "dictionary", "edict", "indicate", "jurisdiction",
            "predict", "verdict", "vindictive",
            // -- contains "cock" --
            "cockatoo", "cockerel", "cockle", "cockney", "cockpit",
            "cocktail", "haycock", "peacock", "shuttlecock",
            "stopcock", "woodcock",
            // -- contains "cum" --
            "accumulate", "acumen", "capsicum", "circumference",
            "circumstance", "cucumber", "cumulative", "document",
            "encumber", "incumbent", "succumb", "talcum",
            // -- contains "tit" --
            "altitude", "appetite", "aptitude", "attitude",
            "chastity", "competition", "constitute", "constitution",
            "destitute", "entity", "entitle", "fortitude",
            "gratitude", "identity", "institute", "latitude",
            "legitimate", "magnitude", "multitude", "partition",
            "petition", "quantity", "repetition", "restitution",
            "sanctity", "solstice", "stitch", "substitute",
            "superstition", "titan", "title", "titillate",
            // -- contains "damn" --
            "adamant", "fundamental",
            // -- contains "hell" --
            "hello", "shell", "shellfish", "seashell", "eggshell",
            "nutshell", "bombshell", "shelter",
            // -- contains "anal" --
            "analogy", "analogous", "analysis", "analyst", "analyze",
            "banal", "canal", "finale", "national", "journal",
            "penalty", "signal",
            // -- contains "cunt" --
            "scunthorpe",
            // -- contains "penis" --
            "penistone",
            // -- contains "rape" --
            "grape", "drape", "scrape", "trapeze",
            "night", "knight", "enigma", "benign", "enigmatic",
            // -- contains "ho" --
            "therapist",
            // -- Minecraft-relevant --
            "birch", "pitch", "ditch", "witch", "switch",
            "hitch", "snitch", "stitch", "basement");

    public static Result mergeFromConfig(MinecraftServer server) {
        ChatFilterSavedData data = ChatFilter.getData(server);
        int before = data.snapshot().size();
        List<String> warnings = new ArrayList<>();

        // 1. Always merge hardcoded builtins first
        for (String word : BUILTIN_WORDS) {
            data.add(word);
        }
        for (String word : BUILTIN_WHITELIST) {
            data.addWhitelist(word);
        }

        // 2. Then layer JSON config on top, then clear the queue so words aren't re-imported next reload
        var queue = ConfigIO.readChatFilterQueue();
        for (String word : queue.contents) {
            if (word != null && !word.isEmpty()) {
                data.add(word);
            }
        }
        for (String word : queue.whitelist) {
            if (word != null && !word.isEmpty()) {
                data.addWhitelist(word);
            }
        }
        // Clear the import queue in the file now that words are in NBT.
        ConfigIO.save();

        int after = data.snapshot().size();
        return new Result(Math.max(after - before, 0), after, warnings);
    }

    public record Result(int added, int total, List<String> warnings) {
    }
}
