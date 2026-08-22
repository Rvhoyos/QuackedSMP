package mc.smpessentials.dims;

import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.util.OptionalInt;
import java.util.Set;

/**
 * Vertical travel between the overworld and the {@code ether} dimensions above it.
 *
 * Down: falling out the bottom of any ether drops you into the overworld at the same XZ, one block
 * above the build limit. Up: gliding past a threshold height in the overworld carries you into the
 * linked ether at the same XZ. Both directions keep your momentum, so a trident thrown up crosses,
 * arcs, falls back out, and lands under the XZ it left.
 *
 * Down is many-to-one, since every ether has the same overworld beneath it. Up is one-to-one:
 * {@link DimManager#skyEther} picks the single ether that counts as the sky.
 *
 * Player-made entities travel in both directions; mobs do not. Players are checked by
 * {@link #tick(ServerPlayer)} once per player per server tick. Everything else arrives through
 * {@link #transferOnVoidFall(Entity)} and {@link #transferOnSkyRise(Entity)}, both called from
 * EntityVerticalBoundsMixin.
 */
public final class EtherVerticalTravel {

    private EtherVerticalTravel() {}

    // Gap between the fall-out height and the climb-in height. Falling out leaves you at dropY
    // still holding an elytra, so without a gap you could re-enter on the same glide.
    private static final int ENTRY_MARGIN = 20;

    // How far below the ether's terrain floor to start looking for room to arrive in.
    private static final int ARRIVAL_CLEARANCE = 8;

    // Height of the space an arriving traveller needs, in blocks.
    private static final int ARRIVAL_HEADROOM = 2;

    // Ticks between repeats of the refusal message, so a player parked above a sealed column sees
    // it once rather than every tick.
    private static final int REFUSAL_MESSAGE_INTERVAL = 20;

    /**
     * Runs both directions for one player: the void fall when they are in an ether, the climb when
     * they are gliding high over the overworld. A no-op anywhere else.
     */
    public static void tick(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        if (DimManager.isEtherDim(level.dimension().identifier().toString())) {
            if (player.getY() >= level.dimensionTypeRegistration().value().minY()) return;
            dropIntoOverworld(player, level);
            player.sendSystemMessage(
                    Component.literal("The void pulls you back to the overworld."), true);
            return;
        }

        if (level.dimension().equals(Level.OVERWORLD)) {
            climbIntoEther(player, level);
        }
    }

    /**
     * Sends a player-made entity that fell out of an ether dim to the overworld instead of letting
     * vanilla discard it. Returns true when the entity was moved, in which case the caller must skip
     * the discard.
     *
     * Mobs and anything carrying a passenger are left to vanilla, so a mob cannot ride a boat into
     * the overworld.
     */
    public static boolean transferOnVoidFall(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        if (!DimManager.isEtherDim(level.dimension().identifier().toString())) return false;
        if (!mayTravel(entity)) return false;

        dropIntoOverworld(entity, level);
        return true;
    }

    /**
     * The mirror: sends a player-made entity that climbed past the threshold in the overworld up
     * into the linked ether. Same whitelist as the fall, so a player's trident makes the trip and a
     * skeleton's arrow does not.
     */
    public static void transferOnSkyRise(Entity entity) {
        if (!SmpConfig.ETHER_SKY_ENTRY_ENABLED) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        if (entity.getY() < entryY(level)) return;
        if (!mayTravel(entity)) return;

        DimManager.skyEther(level.getServer())
                .ifPresent(ether -> riseInto(entity, ether));
    }

    // Players cross while gliding only, which keeps creative flight out of it and means no amount
    // of standing, jumping or being launched can trigger it from the ground.
    private static void climbIntoEther(ServerPlayer player, ServerLevel overworld) {
        if (!SmpConfig.ETHER_SKY_ENTRY_ENABLED) return;
        if (!player.isFallFlying()) return;
        if (player.getY() < entryY(overworld)) return;

        ServerLevel ether = DimManager.skyEther(overworld.getServer()).orElse(null);
        if (ether == null) return;

        if (riseInto(player, ether)) {
            player.sendSystemMessage(Component.literal("You break through into the ether."), true);
        } else if (player.tickCount % REFUSAL_MESSAGE_INTERVAL == 0) {
            player.sendSystemMessage(
                    Component.literal("The ether is solid above you here."), true);
        }
    }

    // Things a player dropped, built, placed, lit, or threw, and nothing carrying a passenger, so a
    // mob cannot ride a boat across. Deliberately a whitelist: ArmorStand is a LivingEntity, so
    // "not a mob" would not have been enough to tell them apart. Projectiles go by owner, so a
    // player's trident travels and can still hit someone on the other side, while a skeleton's
    // arrow does not. Players are excluded here because they have their own per-tick path.
    private static boolean mayTravel(Entity entity) {
        if (!entity.getPassengers().isEmpty()) return false;
        return entity instanceof ItemEntity
                || entity instanceof VehicleEntity        // boats, rafts, every minecart
                || entity instanceof ArmorStand
                || entity instanceof FallingBlockEntity
                || entity instanceof PrimedTnt
                || (entity instanceof Projectile projectile && projectile.getOwner() instanceof Player);
    }

    // Drops into the overworld sky at the XZ it fell through. Ether is 1:1 with the overworld, so
    // the coordinates carry over unscaled.
    private static void dropIntoOverworld(Entity entity, ServerLevel etherLevel) {
        ServerLevel overworld = etherLevel.getServer().overworld();
        move(entity, overworld, dropY(overworld));
    }

    // Carries a traveller up into the ether at the XZ it crossed at. False when the column there has
    // no room, in which case nothing moves.
    private static boolean riseInto(Entity entity, ServerLevel ether) {
        double x = clampX(entity.getX(), ether);
        double z = clampZ(entity.getZ(), ether);

        OptionalInt y = arrivalY(ether, Mth.floor(x), Mth.floor(z));
        if (y.isEmpty()) return false;

        move(entity, ether, y.getAsInt());
        return true;
    }

    // One move for both directions: same XZ, clamped inside the destination border so a crossing
    // from beyond it cannot strand anything outside, and DELTA relatives so the traveller keeps the
    // speed it arrived with rather than starting from rest.
    private static void move(Entity entity, ServerLevel dest, double y) {
        entity.teleportTo(dest, clampX(entity.getX(), dest), y, clampZ(entity.getZ(), dest),
                Set.of(Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z),
                entity.getYRot(), entity.getXRot(), false);
    }

    private static double clampX(double x, ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        return Mth.clamp(x, border.getMinX() + 1.0, border.getMaxX() - 1.0);
    }

    private static double clampZ(double z, ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        return Mth.clamp(z, border.getMinZ() + 1.0, border.getMaxZ() - 1.0);
    }

    /**
     * Where the fall out of an ether lands: one above the overworld build limit, the lowest height
     * at which no block can exist, so nothing is ever dropped inside a tall build.
     */
    public static int dropY(ServerLevel overworld) {
        return overworld.getMaxY() + 1;
    }

    /**
     * The height a glide has to reach to cross into the ether. Configured, or the fall-out height
     * plus a margin. Anything at or below the fall-out height is rejected, since it would send a
     * player who just fell out straight back up.
     */
    public static int entryY(ServerLevel overworld) {
        int floor = dropY(overworld);
        int configured = SmpConfig.ETHER_SKY_ENTRY_Y;
        return configured > floor ? configured : floor + ENTRY_MARGIN;
    }

    /**
     * The Y an arriving traveller fits at in this column, searching upward from just below the
     * dim's terrain floor. Empty when the whole column is blocked.
     *
     * Ether dims normally generate nothing below Y 0, but that comes from the noise settings of the
     * preset they are built on, which a datapack can change, and players can build in the void band
     * either way. So this is a search rather than a fixed height. Upward, because downward is the
     * void the traveller would immediately fall back through.
     */
    public static OptionalInt arrivalY(ServerLevel ether, int x, int z) {
        int lowest = ether.getMinY() + 1;
        int highest = ether.getMaxY() - ARRIVAL_HEADROOM;
        int start = Mth.clamp(terrainFloor(ether) - ARRIVAL_CLEARANCE, lowest, highest);

        // One chunk lookup for the whole column instead of one per block.
        LevelChunk chunk = ether.getChunk(x >> 4, z >> 4);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = start; y <= highest; y++) {
            if (fits(chunk, cursor, x, y, z)) return OptionalInt.of(y);
        }
        return OptionalInt.empty();
    }

    private static boolean fits(LevelChunk chunk, BlockPos.MutableBlockPos cursor, int x, int y, int z) {
        for (int dy = 0; dy < ARRIVAL_HEADROOM; dy++) {
            if (chunk.getBlockState(cursor.set(x, y + dy, z)).blocksMotion()) return false;
        }
        return true;
    }

    // Lowest Y this dim's generator can place terrain at. Ether dims copy the noise settings of the
    // floating islands preset, which stops well above the dim's own minimum, leaving a band of open
    // void beneath the islands to arrive in.
    private static int terrainFloor(ServerLevel ether) {
        if (ether.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noise) {
            return noise.generatorSettings().value().noiseSettings().minY();
        }
        return ether.getMinY();
    }
}
