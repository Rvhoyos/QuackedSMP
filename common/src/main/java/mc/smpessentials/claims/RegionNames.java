package mc.smpessentials.claims;

import mc.smpessentials.claims.model.ClaimData;
import mc.smpessentials.claims.model.WarpAnchor;
import mc.smpessentials.claims.storage.ClaimedSavedData;
import mc.smpessentials.claims.storage.WhitelistSavedData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the "region naming" concept end to end: canonicalization, global uniqueness, the
 * name+warp anchor set, merge resolution when two named regions join, legacy migration, and
 * /visit target resolution. Operates over {@link ClaimedSavedData} storage primitives; the
 * storage class holds no naming policy. A region is one owner's connected blob of chunks and
 * carries at most one named (anchor) chunk.
 */
public final class RegionNames {
    private RegionNames() {
    }

    // Canonical form of a name: decoration ([tag] prefix, & colour codes) removed, then folded
    // to lower case. Everything that matches names by text goes through here, so a decorated name
    // and its plain spelling are the same name and neither can be used to dodge uniqueness.
    public static String normalize(String name) {
        return name == null ? "" : RegionDecoration.strip(name).trim().toLowerCase(Locale.ROOT);
    }

    // The name as players should see it: tag removed, colour codes intact for chat rendering.
    public static String display(String name) {
        return RegionDecoration.parse(name).body();
    }

    public static Optional<ClaimData> findByName(ClaimedSavedData store, String name) {
        String norm = normalize(name);
        if (norm.isEmpty())
            return Optional.empty();
        for (ClaimData c : store.allClaims()) {
            if (c.name().isPresent() && normalize(c.name().get()).equals(norm))
                return Optional.of(c);
        }
        return Optional.empty();
    }

    // Sets a region's single name + warp anchor on the chunk the owner is standing in.
    public static RenameResult nameRegion(ClaimedSavedData store, ServerLevel level, ChunkPos standChunk,
            UUID owner, String rawName, WarpAnchor anchor) {
        ResourceKey<Level> dim = level.dimension();
        ClaimData standClaim = store.getClaim(dim, standChunk).orElse(null);
        if (standClaim == null || !standClaim.owner().equals(owner))
            return RenameResult.NOT_OWNER;

        String name = rawName.trim();
        Set<ChunkPos> region = ClaimRegions.componentContaining(store.ownerChunks(dim, owner), standChunk);

        Optional<ClaimData> holder = findByName(store, name);
        if (holder.isPresent()) {
            ClaimData h = holder.get();
            boolean sameRegion = h.dimension().equals(dim) && region.contains(ChunkPos.unpack(h.chunk()));
            if (!sameRegion)
                return RenameResult.NAME_TAKEN;
        }

        // Enforce one named chunk per region: clear the existing anchor before setting the new one.
        store.namedChunkIn(dim, region).ifPresent(c -> store.clearName(dim, c.chunk()));
        store.setNameAnchor(dim, standChunk.pack(), name, anchor);
        return RenameResult.OK;
    }

    // Called right after a chunk is claimed. If that chunk bridged two or more of the owner's own
    // named regions, the biggest pre-merge region keeps its name (tie: oldest claim); losers cleared.
    public static void resolveMerge(ClaimedSavedData store, ServerLevel level, ChunkPos justClaimed, UUID owner) {
        ResourceKey<Level> dim = level.dimension();
        Set<ChunkPos> owned = store.ownerChunks(dim, owner);

        Set<ChunkPos> bridgeNeighbors = new HashSet<>();
        for (ChunkPos n : ClaimRegions.neighbors(justClaimed)) {
            if (owned.contains(n))
                bridgeNeighbors.add(n);
        }
        if (bridgeNeighbors.size() < 2)
            return; // cannot bridge two components

        Set<ChunkPos> graph = new HashSet<>(owned);
        graph.remove(justClaimed);

        List<ClaimData> namedWinnersPerComp = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        for (Set<ChunkPos> comp : ClaimRegions.connectedComponents(graph)) {
            if (bridgeNeighbors.stream().noneMatch(comp::contains))
                continue; // not merged by this claim
            store.namedChunkIn(dim, comp).ifPresent(named -> {
                namedWinnersPerComp.add(named);
                sizes.add(comp.size());
            });
        }
        if (namedWinnersPerComp.size() < 2)
            return; // at most one name among the merged components

        int winner = 0;
        for (int i = 1; i < namedWinnersPerComp.size(); i++) {
            boolean bigger = sizes.get(i) > sizes.get(winner);
            boolean tieOlder = sizes.get(i).equals(sizes.get(winner))
                    && namedWinnersPerComp.get(i).createdAtMillis() < namedWinnersPerComp.get(winner).createdAtMillis();
            if (bigger || tieOlder)
                winner = i;
        }
        for (int i = 0; i < namedWinnersPerComp.size(); i++) {
            if (i != winner)
                store.clearName(dim, namedWinnersPerComp.get(i).chunk());
        }
    }

    public static List<String> visitable(ClaimedSavedData store, ServerPlayer player) {
        UUID me = player.getUUID();
        MinecraftServer server = player.level().getServer();
        boolean isOp = server != null && server.getPlayerList().isOp(player.nameAndId());
        WhitelistSavedData wl = WhitelistSavedData.get(player.level());
        List<String> out = new ArrayList<>();
        for (ClaimData c : store.allClaims()) {
            if (c.name().isEmpty() || c.name().get().isBlank())
                continue;
            if (isOp || c.owner().equals(me) || wl.isTrusted(c.owner(), me))
                out.add(RegionDecoration.strip(c.name().get()));
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static VisitResult resolveVisit(ClaimedSavedData store, ServerPlayer player, String name) {
        Optional<ClaimData> found = findByName(store, name);
        if (found.isEmpty())
            return VisitResult.fail(VisitStatus.UNKNOWN);
        ClaimData claim = found.get();

        MinecraftServer server = player.level().getServer();
        boolean isOp = server != null && server.getPlayerList().isOp(player.nameAndId());
        boolean allowed = claim.owner().equals(player.getUUID())
                || WhitelistSavedData.get(player.level()).isTrusted(claim.owner(), player.getUUID());
        if (!allowed && !isOp)
            return VisitResult.fail(VisitStatus.NO_ACCESS);

        ServerLevel target = server == null ? null : server.getLevel(claim.dimension());
        if (target == null)
            return VisitResult.fail(VisitStatus.DIM_UNAVAILABLE);

        String display = display(claim.name().orElse(name.trim()));
        if (claim.warp().isPresent()) {
            WarpAnchor a = claim.warp().get();
            return new VisitResult(VisitStatus.OK, target, a.x(), a.y(), a.z(), a.yaw(), a.pitch(), display);
        }

        // Legacy named region with no anchor: aim at the region center at the surface.
        Set<ChunkPos> region = ClaimRegions.componentContaining(
                store.ownerChunks(claim.dimension(), claim.owner()), ChunkPos.unpack(claim.chunk()));
        double ax = 0, az = 0;
        for (ChunkPos cp : region) {
            ax += cp.getMiddleBlockX();
            az += cp.getMiddleBlockZ();
        }
        int cx = (int) Math.round(ax / region.size());
        int cz = (int) Math.round(az / region.size());
        int cy = target.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
        return new VisitResult(VisitStatus.OK, target, cx + 0.5, cy, cz + 0.5, 0f, 0f, display);
    }

    // One-shot legacy cleanup collapsing per-chunk names into one name per region.
    // Intra-region: keep the name the region effectively has (the most common one, i.e. what it
    // already showed on the map), clearing the other named chunks. Only a genuine tie for most
    // common (no clear winner) deletes every name in the region.
    // Cross-region: if the same name survives on two regions, the bigger region keeps it (older
    // claim as tie-break), matching the live merge rule. Idempotent once persisted.
    public static boolean migrate(ClaimedSavedData store) {
        record Survivor(ClaimData kept, int regionSize) {
        }

        // Group by dimension then owner.
        Map<ResourceKey<Level>, Map<UUID, List<ClaimData>>> byDimOwner = new HashMap<>();
        for (ClaimData c : store.allClaims()) {
            byDimOwner.computeIfAbsent(c.dimension(), k -> new HashMap<>())
                    .computeIfAbsent(c.owner(), k -> new ArrayList<>()).add(c);
        }

        List<ClaimData> toClear = new ArrayList<>();
        Map<String, List<Survivor>> survivorsByName = new HashMap<>();

        for (var dimEntry : byDimOwner.entrySet()) {
            for (List<ClaimData> ownerClaims : dimEntry.getValue().values()) {
                Set<ChunkPos> owned = new HashSet<>();
                Map<ChunkPos, ClaimData> byPos = new HashMap<>();
                for (ClaimData c : ownerClaims) {
                    ChunkPos cp = ChunkPos.unpack(c.chunk());
                    owned.add(cp);
                    byPos.put(cp, c);
                }
                for (Set<ChunkPos> comp : ClaimRegions.connectedComponents(owned)) {
                    Map<String, List<ClaimData>> chunksByName = new HashMap<>();
                    for (ChunkPos cp : comp) {
                        ClaimData c = byPos.get(cp);
                        if (c != null && c.name().isPresent() && !c.name().get().isBlank())
                            chunksByName.computeIfAbsent(normalize(c.name().get()), k -> new ArrayList<>()).add(c);
                    }
                    if (chunksByName.isEmpty())
                        continue;

                    int max = chunksByName.values().stream().mapToInt(List::size).max().orElse(0);
                    List<String> topNames = chunksByName.entrySet().stream()
                            .filter(e -> e.getValue().size() == max).map(Map.Entry::getKey).toList();

                    if (topNames.size() > 1) {
                        // No clear winner: delete every name in the region.
                        chunksByName.values().forEach(toClear::addAll);
                        continue;
                    }

                    // Clear majority winner: keep one chunk of it, clear all other named chunks.
                    List<ClaimData> winnerChunks = chunksByName.get(topNames.get(0));
                    winnerChunks.sort(Comparator.comparingLong(ClaimData::createdAtMillis)
                            .thenComparingLong(ClaimData::chunk));
                    ClaimData keep = winnerChunks.get(0);
                    for (List<ClaimData> list : chunksByName.values())
                        for (ClaimData c : list)
                            if (c != keep)
                                toClear.add(c);
                    survivorsByName.computeIfAbsent(topNames.get(0), k -> new ArrayList<>())
                            .add(new Survivor(keep, comp.size()));
                }
            }
        }

        // Cross-region duplicate names: bigger region keeps it, older claim as tie-break.
        for (List<Survivor> survs : survivorsByName.values()) {
            if (survs.size() <= 1)
                continue;
            survs.sort(Comparator.comparingInt(Survivor::regionSize).reversed()
                    .thenComparingLong(s -> s.kept().createdAtMillis())
                    .thenComparing(s -> s.kept().dimension().identifier().toString())
                    .thenComparingLong(s -> s.kept().chunk()));
            for (int i = 1; i < survs.size(); i++)
                toClear.add(survs.get(i).kept());
        }

        boolean changed = false;
        Set<String> done = new HashSet<>();
        for (ClaimData c : toClear) {
            if (done.add(c.dimension().identifier() + ":" + c.chunk()))
                changed |= store.clearName(c.dimension(), c.chunk());
        }
        return changed;
    }

    public enum RenameResult {
        OK, NOT_OWNER, NAME_TAKEN
    }

    public enum VisitStatus {
        OK, UNKNOWN, NO_ACCESS, DIM_UNAVAILABLE
    }

    public record VisitResult(VisitStatus status, ServerLevel level,
            double x, double y, double z, float yaw, float pitch, String name) {
        static VisitResult fail(VisitStatus status) {
            return new VisitResult(status, null, 0, 0, 0, 0, 0, null);
        }
    }
}
