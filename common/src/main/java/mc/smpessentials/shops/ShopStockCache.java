package mc.smpessentials.shops;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Last-seen stock per shop.
 *
 * A shop's real stock can only be counted while its chunk is loaded, so anything that reports
 * stock without standing there (the admin panel, the BlueMap markers) would otherwise show zero
 * for every shop nobody happens to be visiting. {@link ShopService#getStockCount} writes through
 * to this cache whenever it does manage a live count, and those readers fall back to the last
 * value with {@code live=false} so they can say "last seen" instead of stating a wrong number.
 *
 * Deliberately not persisted: on restart nothing has been seen yet, and a remembered count from
 * a previous session is not evidence about the world now.
 */
public final class ShopStockCache {
    private ShopStockCache() {
    }

    /** A stock number plus whether it was counted just now or remembered from earlier. */
    public record Reading(int count, boolean live, boolean unlimited) {
        public String describe() {
            if (unlimited)
                return "Unlimited";
            return live ? String.valueOf(count) : count + " (last seen)";
        }
    }

    private record Key(ResourceKey<Level> dim, BlockPos pos) {
    }

    private static final Map<Key, Integer> LAST_SEEN = new ConcurrentHashMap<>();

    static void record(ShopEntry shop, int count) {
        LAST_SEEN.put(new Key(shop.dimension(), shop.pos()), count);
    }

    public static void forget(ResourceKey<Level> dim, BlockPos pos) {
        LAST_SEEN.remove(new Key(dim, pos));
    }

    public static Optional<Integer> lastSeen(ShopEntry shop) {
        return Optional.ofNullable(LAST_SEEN.get(new Key(shop.dimension(), shop.pos())));
    }

    public static void clear() {
        LAST_SEEN.clear();
    }
}
