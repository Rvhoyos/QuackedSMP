package mc.smpessentials.bluemap;

import mc.smpessentials.claims.RegionTags;

import java.util.LinkedHashSet;
import java.util.Set;

/** The icon names the map needs uploaded: the region tag art plus the fixed per-layer icons. */
public final class TagIcons {
    private TagIcons() {
    }

    public static final String HOME = "house";
    public static final String SHOP = "chest";
    public static final String SPAWN_SHOP = "market-stall";
    public static final String YOUTUBE = "play-badge";
    public static final String UNTAGGED_REGION = "flag";

    public static Set<String> required() {
        Set<String> icons = new LinkedHashSet<>();
        for (String tag : RegionTags.all())
            RegionTags.iconFor(tag).ifPresent(icons::add);
        icons.add(HOME);
        icons.add(SHOP);
        icons.add(SPAWN_SHOP);
        icons.add(YOUTUBE);
        icons.add(UNTAGGED_REGION);
        return icons;
    }
}
