package mc.smpessentials.mixin;

import mc.smpessentials.chatfilter.ChatFilter;
import mc.smpessentials.chatfilter.ChatFilterSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

// Filters sign text through the chat filter before it is applied.
@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin {

    @ModifyVariable(method = "updateSignText", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private List<FilteredText> quackedsmp$filterSignText(List<FilteredText> filteredText) {
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (!(level instanceof ServerLevel sl))
            return filteredText;

        ChatFilterSavedData data = ChatFilter.getData(sl.getServer());
        List<FilteredText> result = new ArrayList<>(filteredText.size());
        for (FilteredText ft : filteredText) {
            String newRaw = ChatFilter.filterText(ft.raw(), data);
            result.add(FilteredText.passThrough(newRaw));
        }
        return result;
    }
}
