package mc.smpessentials.welcomebook;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * The guide handed to players out of the box, as minecraft:written_book_content JSON.
 *
 * This is only the default. Once quackedsmp.json exists the copy there is what players get, so
 * editing the book in the panel is never overwritten by a mod update.
 *
 * Page geometry is vanilla's: about 14 lines of roughly 19 characters. Every page here was
 * measured against that before being committed, so nothing spills off the parchment.
 *
 * Backslashes are doubled below because a text block processes escape sequences: the JSON needs
 * a literal backslash-n, so the source needs two backslashes.
 */
public final class DefaultWelcomeBook {

    private DefaultWelcomeBook() {}

    private static final String JSON = """
{
  "title": {"raw": "QuackedSMP Guide"},
  "author": "QuackedSMP",
  "generation": 0,
  "pages": [
    {"raw":{"text":"","extra":[{"text":"Welcome to\\nQuackedSMP!\\n\\n","color":"gold","bold":true},{"text":"Survival with land claims, RPG skills, kits and shops.\\n\\n","color":"dark_gray"},{"text":"Every player command is in here. Some may be switched off on this server.","color":"dark_green"}]}},
    {"raw":{"text":"","extra":[{"text":"Contents\\n\\n","color":"gold","bold":true},{"text":"> Getting Around\\n","color":"dark_aqua","underlined":true,"click_event":{"action":"change_page","page":3}},{"text":"> Land Claims\\n","color":"dark_aqua","underlined":true,"click_event":{"action":"change_page","page":4}},{"text":"> Friends\\n","color":"dark_aqua","underlined":true,"click_event":{"action":"change_page","page":5}},{"text":"> Skills\\n","color":"dark_aqua","underlined":true,"click_event":{"action":"change_page","page":6}},{"text":"> Kits\\n","color":"dark_aqua","underlined":true,"click_event":{"action":"change_page","page":7}},{"text":"> Survival\\n","color":"dark_aqua","underlined":true,"click_event":{"action":"change_page","page":8}},{"text":"> Shops\\n","color":"dark_aqua","underlined":true,"click_event":{"action":"change_page","page":9}},{"text":"> Links\\n","color":"dark_aqua","underlined":true,"click_event":{"action":"change_page","page":10}},{"text":"> Support\\n","color":"dark_aqua","underlined":true,"click_event":{"action":"change_page","page":11}},{"text":"\\nUnderlined = clickable.","color":"dark_green"}]}},
    {"raw":{"text":"","extra":[{"text":"Getting Around\\n\\n","color":"gold","bold":true},{"text":"/home","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/home"}},{"text":" your bed\\n","color":"dark_gray"},{"text":"/spawn","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/spawn"}},{"text":" spawn\\n","color":"dark_gray"},{"text":"/rtp","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/rtp"}},{"text":" a random spot\\n","color":"dark_gray"},{"text":"/visit <name>","color":"dark_aqua"},{"text":" a region\\n","color":"dark_gray"},{"text":"/tpr <player>","color":"dark_aqua"},{"text":" ask to tp\\n","color":"dark_gray"},{"text":"/tpa accept","color":"dark_aqua"},{"text":" or deny\\n","color":"dark_gray"}]}},
    {"raw":{"text":"","extra":[{"text":"Land Claims\\n\\n","color":"gold","bold":true},{"text":"/claim","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/claim"}},{"text":" protect a chunk\\n","color":"dark_gray"},{"text":"/unclaim","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/unclaim"}},{"text":" undo\\n","color":"dark_gray"},{"text":"/claim map","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/claim map"}},{"text":" nearby\\n","color":"dark_gray"},{"text":"/claim info","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/claim info"}},{"text":" count + limit\\n","color":"dark_gray"},{"text":"/claim size <1-7>","color":"dark_aqua"},{"text":" brush\\n","color":"dark_gray"},{"text":"/claim name <name>","color":"dark_aqua"},{"text":" VIP\\n","color":"dark_gray"}]}},
    {"raw":{"text":"","extra":[{"text":"Friends\\n\\n","color":"gold","bold":true},{"text":"/trust <player>","color":"dark_aqua"},{"text":" let them build\\n","color":"dark_gray"},{"text":"/untrust <player>","color":"dark_aqua"},{"text":" take it back\\n","color":"dark_gray"},{"text":"/trustlist","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/trustlist"}},{"text":" who you trust\\n","color":"dark_gray"},{"text":"/sos","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/sos"}},{"text":" eject untrusted players\\n","color":"dark_gray"}]}},
    {"raw":{"text":"","extra":[{"text":"Skills\\n\\n","color":"gold","bold":true},{"text":"/skills","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/skills"}},{"text":" your levels\\n","color":"dark_gray"},{"text":"/skills <skill>","color":"dark_aqua"},{"text":" one in detail\\n","color":"dark_gray"},{"text":"/skills top","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/skills top"}},{"text":" leaderboard\\n","color":"dark_gray"},{"text":"/skills view <player>","color":"dark_aqua"},{"text":" someone else\\n","color":"dark_gray"}]}},
    {"raw":{"text":"","extra":[{"text":"Kits\\n\\n","color":"gold","bold":true},{"text":"/smp kit","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/smp kit"}},{"text":" claim one\\n","color":"dark_gray"},{"text":"/smp kit list","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/smp kit list"}},{"text":" what you can take\\n","color":"dark_gray"},{"text":"\\nKits share one cooldown, so pick the best one you can claim.","color":"dark_green"}]}},
    {"raw":{"text":"","extra":[{"text":"Survival\\n\\n","color":"gold","bold":true},{"text":"/smp keepinv on","color":"dark_aqua"},{"text":" or off\\n","color":"dark_gray"},{"text":"/smp hardcore","color":"dark_aqua"},{"text":" create, join, leave, status, list\\n","color":"dark_gray"},{"text":"/verify","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/verify"}},{"text":" 18+ voice chat\\n","color":"dark_gray"},{"text":"/rules","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/rules"}},{"text":" the rules\\n","color":"dark_gray"},{"text":"/guide","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/guide"}},{"text":" this book\\n","color":"dark_gray"}]}},
    {"raw":{"text":"","extra":[{"text":"Shops\\n\\n","color":"gold","bold":true},{"text":"/shop create <price>","color":"dark_aqua"},{"text":" sell from a chest\\n","color":"dark_gray"},{"text":"/shop list","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/shop list"}},{"text":" yours\\n","color":"dark_gray"},{"text":"/shop info","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/shop info"}},{"text":" the one you face\\n","color":"dark_gray"},{"text":"/shop delete","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/shop delete"}},{"text":" remove it\\n","color":"dark_gray"},{"text":"/shop balance","color":"dark_aqua","underlined":true,"click_event":{"action":"run_command","command":"/shop balance"}},{"text":" emeralds\\n","color":"dark_gray"},{"text":"/shop deposit <n>","color":"dark_aqua"},{"text":" also withdraw\\n","color":"dark_gray"}]}},
    {"raw":{"text":"","extra":[{"text":"Quacked Projects\\n\\n","color":"gold","bold":true},{"text":"Ducky Quack Pack\\n","color":"dark_gray"},{"text":"CurseForge\\n","color":"blue","underlined":true,"click_event":{"action":"open_url","url":"https://www.curseforge.com/minecraft/mc-mods/ducky-quack-pack"}},{"text":"Modrinth\\n\\n","color":"blue","underlined":true,"click_event":{"action":"open_url","url":"https://modrinth.com/mod/ducky-quack-pack"}},{"text":"QuackedSMP Pack\\n","color":"dark_gray"},{"text":"CurseForge\\n","color":"blue","underlined":true,"click_event":{"action":"open_url","url":"https://www.curseforge.com/minecraft/modpacks/play-quackedmod-wiki"}},{"text":"Modrinth\\n","color":"blue","underlined":true,"click_event":{"action":"open_url","url":"https://modrinth.com/modpack/quackedsmppack"}}]}},
    {"raw":{"text":"","extra":[{"text":"Support\\n\\n","color":"gold","bold":true},{"text":"Enjoying the server? Buy me a coffee:\\n\\n","color":"dark_gray"},{"text":"Buy Me a Coffee\\n\\n","color":"blue","underlined":true,"click_event":{"action":"open_url","url":"https://buymeacoffee.com/monte.carlo.sim"}},{"text":"Help or bugs:\\n","color":"dark_gray"},{"text":"Dev@Quackedmod.wiki\\n","color":"dark_aqua","underlined":true,"click_event":{"action":"copy_to_clipboard","value":"Dev@Quackedmod.wiki"}},{"text":"(click to copy)","color":"dark_green"}]}}
  ],
  "resolved": true
}""";

    public static JsonElement content() {
        return JsonParser.parseString(JSON);
    }
}
