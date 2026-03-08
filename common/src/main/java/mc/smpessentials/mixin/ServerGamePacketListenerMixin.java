package mc.smpessentials.mixin;

import mc.smpessentials.chatfilter.ChatFilter;
import mc.smpessentials.chatfilter.ChatFilterSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Filters book content (writable and signed) through the chat filter.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    @Shadow
    public ServerPlayer player;

    // --- Writable book pages ---

    @ModifyVariable(method = "updateBookContents", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<FilteredText> quackedsmp$filterWritablePages(List<FilteredText> pages) {
        if (this.player.level().isClientSide())
            return pages;
        ChatFilterSavedData data = ChatFilter.getData(((ServerLevel) this.player.level()).getServer());
        List<FilteredText> result = new ArrayList<>(pages.size());
        for (FilteredText ft : pages) {
            result.add(quackedsmp$applyFilter(ft, data));
        }
        return result;
    }

    // --- Signed book title ---

    @ModifyVariable(method = "signBook", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private FilteredText quackedsmp$filterBookTitle(FilteredText title) {
        if (this.player.level().isClientSide())
            return title;
        ChatFilterSavedData data = ChatFilter.getData(((ServerLevel) this.player.level()).getServer());
        return quackedsmp$applyFilter(title, data);
    }

    // --- Signed book pages ---

    @ModifyVariable(method = "signBook", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<FilteredText> quackedsmp$filterSignedPages(List<FilteredText> pages) {
        if (this.player.level().isClientSide())
            return pages;
        ChatFilterSavedData data = ChatFilter.getData(((ServerLevel) this.player.level()).getServer());
        List<FilteredText> result = new ArrayList<>(pages.size());
        for (FilteredText ft : pages) {
            result.add(quackedsmp$applyFilter(ft, data));
        }
        return result;
    }

    private static FilteredText quackedsmp$applyFilter(FilteredText ft, ChatFilterSavedData data) {
        String newRaw = ChatFilter.filterText(ft.raw(), data);
        return FilteredText.passThrough(newRaw);
    }
}
