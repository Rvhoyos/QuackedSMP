package mc.smpessentials.beacons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public record BeaconInfo(
        BlockPos pos,
        ResourceKey<Level> dimension,
        BeaconTier tier,
        @Nullable Holder<MobEffect> primary,
        @Nullable Holder<MobEffect> secondary) {
}
