package mc.smpessentials.bluemap.layer;

import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.HtmlMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import mc.smpessentials.bluemap.IconAssets;
import mc.smpessentials.bluemap.MarkerColors;
import mc.smpessentials.bluemap.MarkerHtml;
import mc.smpessentials.bluemap.MarkerRefresh;
import mc.smpessentials.claims.ClaimRegions;
import mc.smpessentials.claims.RegionDecoration;
import mc.smpessentials.claims.RegionTags;
import mc.smpessentials.claims.model.ClaimData;
import mc.smpessentials.claims.storage.ClaimedSavedData;
import mc.smpessentials.config.SmpConfig;
import mc.smpessentials.tier.TierService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Claimed land, drawn as a translucent fill per chunk with one label per connected region.
 *
 * A named region's label carries its tag icon above the name, so the map reads as a place rather
 * than a coloured box. The name is player authored, so it only ever reaches the page through
 * {@link MarkerHtml}.
 */
public final class ClaimLayer implements MarkerLayer {

    // Markers sit flat on the surface rather than at a real height, so they read as an overlay.
    private static final int SURFACE_Y = 70;

    // Half the exported icon size, so the icon sits centred on the region.
    private static final int ICON_ANCHOR = 32;

    @Override
    public String id() {
        return "quacksmp_claims";
    }

    @Override
    public String label() {
        return "Claimed Regions";
    }

    @Override
    public MarkerRefresh.Layer refreshKey() {
        return MarkerRefresh.Layer.CLAIMS;
    }

    @Override
    public boolean enabled() {
        return SmpConfig.BLUEMAP_SHOW_CLAIMS;
    }

    @Override
    public void build(MinecraftServer server, BlueMapMap map, MarkerSet set, IconAssets icons) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!Maps.isDimension(map, level.dimension()))
                continue;

            ClaimedSavedData store = ClaimedSavedData.get(level);
            Map<UUID, List<ClaimData>> byOwner = new HashMap<>();
            for (ClaimData c : store.listClaims(level))
                byOwner.computeIfAbsent(c.owner(), k -> new java.util.ArrayList<>()).add(c);

            for (var entry : byOwner.entrySet()) {
                UUID owner = entry.getKey();
                var profile = server.services().nameToIdCache().get(owner).orElse(null);
                String ownerName = profile != null && profile.name() != null ? profile.name() : owner.toString();

                boolean isOp = profile != null && server.getPlayerList().isOp(profile);
                boolean isVip = TierService.getTier(owner, server) >= 1;

                Color fill = MarkerColors.parse(isOp ? SmpConfig.BLUEMAP_OP_CLAIM_COLOR
                        : isVip ? SmpConfig.BLUEMAP_VIP_CLAIM_COLOR
                                : SmpConfig.BLUEMAP_CLAIM_COLOR);

                List<Set<ChunkPos>> regions = ClaimRegions.connectedComponents(
                        entry.getValue().stream().map(c -> ChunkPos.unpack(c.chunk())).collect(Collectors.toSet()));

                int regionIdx = 0;
                for (Set<ChunkPos> region : regions)
                    drawRegion(set, icons, map, store, level, owner, ownerName, regionIdx++, region, fill);
            }
        }
    }

    private void drawRegion(MarkerSet set, IconAssets icons, BlueMapMap map, ClaimedSavedData store,
            ServerLevel level, UUID owner, String ownerName, int regionIdx, Set<ChunkPos> region, Color fill) {

        String rawName = store.namedChunkIn(level.dimension(), region)
                .flatMap(ClaimData::name)
                .filter(n -> !n.isBlank())
                .orElse(null);

        RegionDecoration decoration = RegionDecoration.parse(rawName);
        String plainName = rawName == null ? null : decoration.plain();
        String label = plainName != null && !plainName.isBlank() ? plainName : ownerName + "'s Claim";
        String detail = "Claim owned by <b>" + MarkerHtml.escape(ownerName) + "</b>";

        int chunkIdx = 0;
        for (ChunkPos cp : region) {
            set.put(owner + "_" + regionIdx + "_" + chunkIdx++, ShapeMarker.builder()
                    .label(label)
                    .detail(detail)
                    .shape(chunkShape(cp), SURFACE_Y)
                    .fillColor(fill)
                    .lineColor(MarkerColors.opaque(fill))
                    // No per-chunk outline, so neighbouring chunks read as one region.
                    .lineWidth(0)
                    .depthTestEnabled(false)
                    .build());
        }

        if (rawName == null)
            return;

        double avgX = 0, avgZ = 0;
        for (ChunkPos cp : region) {
            avgX += cp.getMiddleBlockX();
            avgZ += cp.getMiddleBlockZ();
        }
        avgX /= region.size();
        avgZ /= region.size();

        String iconUrl = decoration.tag()
                .flatMap(RegionTags::iconFor)
                .map(icon -> icons.url(map, icon))
                .orElse(null);
        String key = owner + "_" + regionIdx + "_label";

        if (iconUrl != null) {
            // Tagged: a POI marker, whose icon stays pinned at a fixed size instead of growing and
            // shrinking with the zoom the way HtmlMarker content in the 3D scene does. The name is
            // on the icon, which is something there is obviously something to click.
            set.put(key, Maps.ranged(POIMarker.builder())
                    .label(label)
                    .detail("<div style=\"font-family:sans-serif;\"><b>"
                            + MarkerHtml.colored(decoration.body()) + "</b><br>"
                            + detail + "</div>")
                    .position(new Vector3d(avgX, SURFACE_Y, avgZ))
                    .icon(iconUrl, ICON_ANCHOR, ICON_ANCHOR)
                    .build());
            return;
        }

        // Untagged: no icon to click, so the name has to be readable on the map itself. This one
        // does scale with the zoom, which is the trade for being visible without interaction.
        set.put(key, Maps.ranged(HtmlMarker.builder())
                .label(label)
                .html("<div style=\"color:white;text-shadow:2px 2px 4px black;font-weight:bold;"
                        + "font-family:sans-serif;white-space:nowrap;pointer-events:none;font-size:"
                        + labelFontSize(region.size()) + "px;\">"
                        + MarkerHtml.colored(decoration.body()) + "</div>")
                .position(new Vector3d(avgX, SURFACE_Y + 0.1, avgZ))
                .anchor(new Vector2i(0, 0))
                .build());
    }

    // Scale with region size so a small region stays legible next to a sprawling one.
    private static int labelFontSize(int chunkCount) {
        return Math.max(12, Math.min(48, 12 + (int) (Math.sqrt(chunkCount) * 6)));
    }

    private static Shape chunkShape(ChunkPos cp) {
        int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
        int x1 = cp.getMaxBlockX() + 1, z1 = cp.getMaxBlockZ() + 1;
        return new Shape(
                new Vector2d(x0, z0), new Vector2d(x1, z0),
                new Vector2d(x1, z1), new Vector2d(x0, z1));
    }
}
