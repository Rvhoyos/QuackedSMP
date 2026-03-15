package mc.smpessentials.neoforge.voicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import mc.smpessentials.voicechat.QuackedVoicechatPlugin;

/**
 * NeoForge service-loader entry point for the QuackedSMP voice chat plugin.
 * The @ForgeVoicechatPlugin annotation tells Simple Voice Chat to discover
 * and register this class. All logic lives in the common-module parent class.
 */
@ForgeVoicechatPlugin
public class NeoForgeVoicechatPlugin extends QuackedVoicechatPlugin {
}
