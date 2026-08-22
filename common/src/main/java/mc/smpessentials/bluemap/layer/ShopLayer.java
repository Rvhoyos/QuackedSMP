package mc.smpessentials.bluemap.layer;

import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import mc.smpessentials.bluemap.IconAssets;
import mc.smpessentials.bluemap.MarkerHtml;
import mc.smpessentials.bluemap.MarkerRefresh;
import mc.smpessentials.bluemap.TagIcons;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.shops.ShopData;
import mc.smpessentials.shops.ShopEntry;
import mc.smpessentials.shops.ShopService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player chest shops, as a browsable market layer.
 *
 * Shops are grouped by chunk rather than marked individually: a shopping street is dozens of
 * chests within a few blocks, which as separate markers would be an unreadable pile of icons.
 * Grouping by chunk is also stable, in that adding a shop never regroups the ones already there,
 * which a distance based clustering pass would.
 *
 * Read only. There is no player session behind a web map, so the popup lists what is on sale and
 * nothing more.
 */
public final class ShopLayer implements MarkerLayer {

    @Override
    public String id() {
        return "quacksmp_shops";
    }

    @Override
    public String label() {
        return "Shops";
    }

    @Override
    public MarkerRefresh.Layer refreshKey() {
        return MarkerRefresh.Layer.SHOPS;
    }

    @Override
    public boolean enabled() {
        return SmpConfig.BLUEMAP_SHOW_SHOPS && SmpConfig.SHOPS_ENABLED;
    }

    @Override
    public void build(MinecraftServer server, BlueMapMap map, MarkerSet set, IconAssets icons) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!Maps.isDimension(map, level.dimension()))
                continue;

            // Chunk -> the shops standing in it, insertion ordered so the popup is stable.
            Map<Long, List<ShopEntry>> byChunk = new LinkedHashMap<>();
            for (ShopEntry shop : ShopData.get(server).listAll()) {
                if (!shop.dimension().equals(level.dimension()))
                    continue;
                byChunk.computeIfAbsent(new ChunkPos(shop.pos().getX() >> 4, shop.pos().getZ() >> 4).pack(), k -> new ArrayList<>()).add(shop);
            }

            for (var entry : byChunk.entrySet()) {
                List<ShopEntry> shops = entry.getValue();
                shops.sort(Comparator.comparing(ShopEntry::itemId));

                boolean anySpawnShop = shops.stream().anyMatch(ShopEntry::spawnShop);
                String iconUrl = icons.url(map, anySpawnShop ? TagIcons.SPAWN_SHOP : TagIcons.SHOP);
                if (iconUrl == null)
                    continue;

                // Anchor on the first shop rather than the chunk centre, so the marker sits on an
                // actual chest instead of floating in whatever happens to be mid chunk.
                ShopEntry anchor = shops.get(0);
                set.put("shops_" + entry.getKey(), Maps.ranged(POIMarker.builder())
                        .label(titleFor(shops))
                        .detail(detailFor(server, level, shops))
                        .position(anchor.pos().getX() + 0.5, anchor.pos().getY() + 1.0, anchor.pos().getZ() + 0.5)
                        .icon(iconUrl, 32, 32)
                        .build());
            }
        }
    }

    private static String titleFor(List<ShopEntry> shops) {
        if (shops.size() == 1)
            return "Shop: " + plainItemName(shops.get(0).itemId());
        return shops.size() + " shops";
    }

    private String detailFor(MinecraftServer server, ServerLevel level, List<ShopEntry> shops) {
        StringBuilder html = new StringBuilder("<div style=\"font-family:sans-serif;\">");
        html.append("<b>").append(shops.size() == 1 ? "Shop" : shops.size() + " shops").append("</b>");

        for (ShopEntry shop : shops) {
            var reading = ShopService.readStock(level, shop);
            var profile = server.services().nameToIdCache().get(shop.owner()).orElse(null);
            String ownerName = profile != null && profile.name() != null ? profile.name() : "unknown";

            String unit = shop.unit() > 1 ? " &times;" + shop.unit() : "";
            html.append("<div style=\"margin-top:4px;\">")
                    .append("<b>").append(MarkerHtml.itemName(shop.itemId())).append(unit).append("</b>")
                    .append("<br><span>").append(shop.pricePerItem()).append(' ')
                    .append(MarkerHtml.itemName(shop.currencyItemId())).append("</span>")
                    .append("<br><span style=\"opacity:0.75;\">Stock: ").append(MarkerHtml.escape(reading.describe()))
                    .append(" &middot; ").append(MarkerHtml.escape(ownerName))
                    .append("</span></div>");
        }

        return html.append("</div>").toString();
    }

    private static String plainItemName(String itemId) {
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        return path.replace('_', ' ');
    }
}
