package mc.smpessentials.mixin;

import mc.smpessentials.beacons.BeaconInfo;
import mc.smpessentials.beacons.BeaconManager;
import mc.smpessentials.beacons.BeaconTier;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jetbrains.annotations.Nullable;

@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin extends BlockEntity {

    @Shadow
    int levels;
    @Shadow
    @Nullable
    Holder<MobEffect> primaryPower;
    @Shadow
    @Nullable
    Holder<MobEffect> secondaryPower;

    public BeaconBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Inject(method = "applyEffects", at = @At("HEAD"), cancellable = true)
    private static void cancelVanillaEffects(Level level, BlockPos pos, int levels, @Nullable Holder<MobEffect> primary,
            @Nullable Holder<MobEffect> secondary, CallbackInfo ci) {
        if (SmpConfig.ENABLE_CUSTOM_BEACONS) {
            ci.cancel(); // Disable vanilla effect application
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void tick(Level level, BlockPos pos, BlockState state, BeaconBlockEntity blockEntity,
            CallbackInfo ci) {
        if (!SmpConfig.ENABLE_CUSTOM_BEACONS)
            return;

        // Run our logic
        // We let vanilla run first so 'levels' and 'beam' are calculated.

        // Let's run our periodic check
        if (level.getGameTime() % 80L == 0L) {
            BeaconBlockEntityMixin self = (BeaconBlockEntityMixin) (Object) blockEntity;
            updateBeaconRegistration(level, pos, self);
        }
    }

    private static void updateBeaconRegistration(Level level, BlockPos pos, BeaconBlockEntityMixin beacon) {
        if (beacon.levels > 0 && beacon.primaryPower != null) {
            // It has levels! Now we must determine the Tier based on material.
            // Vanilla only gives us 'int levels', not the material.
            BeaconTier tier = scanForTier(level, pos, beacon.levels);

            BeaconInfo info = new BeaconInfo(
                    pos,
                    level.dimension(),
                    tier,
                    beacon.primaryPower,
                    beacon.secondaryPower);

            BeaconManager.get().register(pos, level.dimension().location().toString(), info);
        } else {
            // Invalid or no power
            BeaconManager.get().unregister(pos, level.dimension().location().toString());
        }
    }

    private static BeaconTier scanForTier(Level level, BlockPos pos, int levels) {
        // We check the pyramid layers.
        // For simplicity and performance, we can just check the *bottom-most* layer of
        // the current valid level.
        // Or strictly: The tier is determined by the "Lowest Common Denominator"
        // material in the structure.

        // Strategy: Check the 3x3 ring immediately below.
        BeaconTier minTier = BeaconTier.NETHERITE; // Start high, downgrade if we find weaker blocks

        // We only scan the layers that are active.
        for (int i = 1; i <= levels; i++) {
            int y = pos.getY() - i;
            // Radius of layer i usually starts at 1 (3x3) for level 1?
            // Vanilla: Level 1 = 3x3. Level 2 = 5x5.
            int radius = i;

            for (int x = pos.getX() - radius; x <= pos.getX() + radius; x++) {
                for (int z = pos.getZ() - radius; z <= pos.getZ() + radius; z++) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    BeaconTier blockTier = BeaconTier.fromBlock(state);

                    // Downgrade logic
                    if (blockTier.ordinal() < minTier.ordinal()) {
                        minTier = blockTier;
                    }
                    if (minTier == BeaconTier.NONE)
                        return BeaconTier.NONE;
                }
            }
        }
        return minTier;
    }
}
