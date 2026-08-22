package mc.smpessentials.rtp;

import mc.smpessentials.config.ConfigData;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Resolves which RTP profile applies to a dimension. Profiles are keyed by dimension id, so
 * /rtp can behave differently in each world.
 */
final class RtpProfiles {
    private RtpProfiles() {}

    /** The enabled profile for this dimension, or null when /rtp is not offered there. */
    static ConfigData.RtpProfile forDimension(ResourceKey<Level> dimension) {
        String id = dimension.identifier().toString();
        for (ConfigData.RtpProfile profile : SmpConfig.RTP_PROFILES) {
            if (profile.enabled && id.equals(profile.dimension)) return profile;
        }
        return null;
    }
}
