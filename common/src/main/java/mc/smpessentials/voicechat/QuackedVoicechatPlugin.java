package mc.smpessentials.voicechat;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import mc.smpessentials.ageverify.AgeVerifyData;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Simple Voice Chat plugin that enforces age-gating on all voice audio.
 * Cancels outgoing mic packets from unverified players and incoming audio packets for unverified
 * receivers. Common logic only; platform-specific annotations are in the platform wrapper class.
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
        registration.registerEvent(PlayerConnectedEvent.class, this::onPlayerConnected);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onPlayerDisconnected);
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

    private void onPlayerConnected(PlayerConnectedEvent event) {
        VoicechatConnection conn = event.getConnection();
        if (conn != null && conn.getPlayer() != null) {
            VoicechatIntegration.onPlayerVoicechatConnected(conn.getPlayer().getUuid());
        }
    }

    private void onPlayerDisconnected(PlayerDisconnectedEvent event) {
        VoicechatIntegration.onPlayerVoicechatDisconnected(event.getPlayerUuid());
    }

    private boolean isVerified(UUID uuid) {
        MinecraftServer server = VoicechatIntegration.getServer();
        if (server == null)
            return false;
        return AgeVerifyData.get(server).isVerified(uuid);
    }
}
