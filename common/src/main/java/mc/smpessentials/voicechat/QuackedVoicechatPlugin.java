package mc.smpessentials.voicechat;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import mc.smpessentials.ageverify.AgeVerifyData;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Simple Voice Chat plugin that enforces age-gating on all voice audio.
 *
 * <p>Registers two server-side events:
 * <ul>
 *   <li>{@link de.maxhenkel.voicechat.api.events.MicrophonePacketEvent} — cancels
 *       outgoing microphone packets from players who have not verified their age,
 *       preventing them from being heard by others.</li>
 *   <li>{@link de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent} — cancels
 *       incoming audio packets destined for unverified players, preventing them from
 *       hearing others speak.</li>
 * </ul>
 *
 * <p>This class contains only common logic. Platform-specific discovery annotations
 * ({@code @ForgeVoicechatPlugin}) are applied in the platform module wrapper class.
 */
public class QuackedVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "quacksmp";
    }

    @Override
    public void initialize(VoicechatApi api) {
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
        registration.registerEvent(EntitySoundPacketEvent.class, this::onEntitySoundPacket);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (!event.isCancellable())
            return;
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || sender.getPlayer() == null)
            return;
        if (!isVerified(sender.getPlayer().getUuid()))
            event.cancel();
    }

    private void onEntitySoundPacket(EntitySoundPacketEvent event) {
        if (!event.isCancellable())
            return;
        VoicechatConnection receiver = event.getReceiverConnection();
        if (receiver == null || receiver.getPlayer() == null)
            return;
        if (!isVerified(receiver.getPlayer().getUuid()))
            event.cancel();
    }

    private boolean isVerified(UUID uuid) {
        MinecraftServer server = VoicechatIntegration.getServer();
        if (server == null)
            return false;
        return AgeVerifyData.get(server).isVerified(uuid);
    }
}
