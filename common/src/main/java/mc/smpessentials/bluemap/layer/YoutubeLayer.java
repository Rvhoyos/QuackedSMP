package mc.smpessentials.bluemap.layer;

import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import mc.smpessentials.bluemap.IconAssets;
import mc.smpessentials.bluemap.MarkerHtml;
import mc.smpessentials.bluemap.MarkerRefresh;
import mc.smpessentials.bluemap.TagIcons;
import mc.smpessentials.bluemap.YoutubeMarkerData;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.server.MinecraftServer;

/** Video markers pinned with /youtube, linking out to the video. */
public final class YoutubeLayer implements MarkerLayer {

    @Override
    public String id() {
        return "quacksmp_youtube";
    }

    @Override
    public String label() {
        return "Video Content";
    }

    @Override
    public MarkerRefresh.Layer refreshKey() {
        return MarkerRefresh.Layer.YOUTUBE;
    }

    @Override
    public boolean enabled() {
        return SmpConfig.BLUEMAP_SHOW_YOUTUBE;
    }

    @Override
    public void build(MinecraftServer server, BlueMapMap map, MarkerSet set, IconAssets icons) {
        String iconUrl = icons.url(map, TagIcons.YOUTUBE);
        if (iconUrl == null)
            return;

        int idx = 0;
        for (var e : YoutubeMarkerData.get(server).all()) {
            if (!Maps.isDimension(map, e.dimension()))
                continue;

            // The URL passed isAllowedUrl when it was pinned, so it is an https YouTube link;
            // escaping here covers the attribute context it is about to sit in.
            String href = MarkerHtml.escape(e.url());
            set.put("youtube_" + idx++, Maps.ranged(POIMarker.builder())
                    .label(e.label())
                    .detail("<div style=\"font-family:sans-serif;\"><b>" + MarkerHtml.escape(e.label()) + "</b>"
                            + "<br><a href=\"" + href + "\" target=\"_blank\" rel=\"noopener noreferrer\""
                            + " style=\"color:#C4302B;font-weight:bold;\">&#9654; Watch on YouTube</a></div>")
                    .position(e.x(), e.y() + 1.0, e.z())
                    .icon(iconUrl, 32, 32)
                    .build());
        }
    }
}
