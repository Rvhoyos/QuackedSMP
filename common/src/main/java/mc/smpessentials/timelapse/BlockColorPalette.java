package mc.smpessentials.timelapse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.smpessentials.SmpUtilsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * The texture-averaged base colour of every block, keyed by registry id. Vanilla
 * {@link net.minecraft.world.level.material.MapColor} has only 62 base colours,
 * so it buckets hundreds of blocks together and the map reads like a filled map.
 * This palette (generated offline by {@code ./gradlew generateBlockColors} from
 * the client jar's textures) gives each block its own colour instead.
 *
 * Loaded once from the bundled {@code block_colors.json}. Any block missing
 * from the table (or if the table fails to load at all) falls back to its
 * {@code MapColor} at render time, so the map degrades to the old behaviour
 * rather than breaking.
 */
final class BlockColorPalette {

    /** Returned by {@link #rgbOf} when a block has no palette entry. */
    static final int NO_ENTRY = -1;

    private static final String RESOURCE = "/timelapse/block_colors.json";

    private static volatile boolean loaded = false;
    // Indexed by BuiltInRegistries.BLOCK.getId(block); NO_ENTRY where unmapped.
    private static int[] byBlockId = new int[0];

    private BlockColorPalette() {}

    /** Loads the palette on first call; a no-op afterwards. */
    static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;   // one attempt; a failed load leaves an empty table (all fall back)
        try {
            int[] table = new int[BuiltInRegistries.BLOCK.size()];
            Arrays.fill(table, NO_ENTRY);

            int count = 0;
            for (Map.Entry<String, com.google.gson.JsonElement> e : read().entrySet()) {
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(e.getKey()));
                // Defaulted registry returns AIR for unknown ids; ignore those stale entries.
                if (block == Blocks.AIR && !e.getKey().equals("minecraft:air")) continue;
                int id = BuiltInRegistries.BLOCK.getId(block);
                if (id >= 0 && id < table.length) {
                    table[id] = Integer.parseInt(e.getValue().getAsString(), 16) & 0xFFFFFF;
                    count++;
                }
            }
            byBlockId = table;
            SmpUtilsMod.LOGGER.info("[Timelapse] Loaded {} block colours.", count);
        } catch (Exception ex) {
            SmpUtilsMod.LOGGER.warn("[Timelapse] Could not load block colour palette: {}", ex.getMessage());
        }
    }

    /** @return packed {@code 0xRRGGBB}, or {@link #NO_ENTRY} if this block has no palette colour. */
    static int rgbOf(Block block) {
        int id = BuiltInRegistries.BLOCK.getId(block);
        return (id >= 0 && id < byBlockId.length) ? byBlockId[id] : NO_ENTRY;
    }

    private static JsonObject read() throws Exception {
        try (InputStream in = BlockColorPalette.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing palette resource " + RESOURCE);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
