package mc.smpessentials.chatfilter;

import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * The blocklist and whitelist the filter ships with, and the merge that puts them into SavedData so
 * the filter works out of the box. Everything else in the lists is added from the panel or
 * {@code /chatfilter add}, both of which write straight to SavedData.
 */
public final class ChatFilterBuiltins {
    private ChatFilterBuiltins() {
    }

    /** Hardcoded blocklist; always loaded regardless of configuration. */
    private static final List<String> WORDS = List.of(
            "fuck", "shit", "ass", "bitch", "damn",
            "dick", "cock", "pussy", "cunt", "whore",
            "slut", "bastard", "nigger", "nigga", "nig", "faggot",
            "retard", "kys", "stfu", "porn", "hentai",
            "penis", "vagina", "tits", "boobs", "cum",
            "jizz", "blowjob", "handjob", "dildo", "anal");

    /**
     * Hardcoded whitelist; prevents common false positives (Scunthorpe problem).
     */
    private static final List<String> WHITELIST = List.of(
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

    /**
     * Merges both lists into the filter's SavedData. Idempotent, so it is safe to call on every
     * start and on every reload; a builtin an operator deleted from the panel comes back.
     */
    public static Result merge(MinecraftServer server) {
        ChatFilterSavedData data = ChatFilter.getData(server);
        int before = data.snapshot().size();

        for (String word : WORDS) {
            data.add(word);
        }
        for (String word : WHITELIST) {
            data.addWhitelist(word);
        }

        int after = data.snapshot().size();
        return new Result(Math.max(after - before, 0), after);
    }

    public record Result(int restored, int total) {
    }
}
