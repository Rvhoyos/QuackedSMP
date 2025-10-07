package mc.smpessentials.claims.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;
import java.util.stream.Collectors;

/** Owner-wide whitelist (applies to ALL of an owner's claims). */
public final class WhitelistSavedData extends SavedData {
    private final Map<UUID, Set<UUID>> byOwner; // owner -> trusted set

    public static final Codec<WhitelistSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.list(Entry.CODEC).fieldOf("entries").forGetter(s ->
            s.byOwner.entrySet().stream()
                .map(e -> new Entry(e.getKey(), new ArrayList<>(e.getValue())))
                .collect(Collectors.toList())
        )
    ).apply(i, entries -> {
        Map<UUID, Set<UUID>> map = new Object2ObjectOpenHashMap<>();
        for (Entry e : entries) map.put(e.owner, new HashSet<>(e.trusted));
        return new WhitelistSavedData(map);
    }));

    public static final SavedDataType<WhitelistSavedData> TYPE = new SavedDataType<>(
        "quackedsmp_whitelist",
        ctx -> new WhitelistSavedData(new Object2ObjectOpenHashMap<>()),
        ctx -> WhitelistSavedData.CODEC,
        DataFixTypes.LEVEL
    );

    public WhitelistSavedData(Map<UUID, Set<UUID>> byOwner) {
        this.byOwner = byOwner;
    }

    public static WhitelistSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isTrusted(UUID owner, UUID actor) {
        if (owner.equals(actor)) return true;
        Set<UUID> set = byOwner.get(owner);
        return set != null && set.contains(actor);
    }

    public Set<UUID> list(UUID owner) {
        return Collections.unmodifiableSet(byOwner.getOrDefault(owner, Set.of()));
    }

    public boolean add(UUID owner, UUID trusted) {
        Set<UUID> set = byOwner.computeIfAbsent(owner, k -> new HashSet<>());
        boolean changed = set.add(trusted);
        if (changed) setDirty();
        return changed;
    }

    public boolean remove(UUID owner, UUID trusted) {
        Set<UUID> set = byOwner.get(owner);
        if (set == null) return false;
        boolean changed = set.remove(trusted);
        if (changed) setDirty();
        if (set.isEmpty()) byOwner.remove(owner);
        return changed;
    }

    /* small codec record for (owner, [trusted...]) */
    private static record Entry(UUID owner, List<UUID> trusted) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtilPlus.CODEC.fieldOf("owner").forGetter(Entry::owner),
            UUIDUtilPlus.CODEC.listOf().fieldOf("trusted").forGetter(Entry::trusted)
        ).apply(i, Entry::new));
    }

    /** Tiny UUID codec helper (vanilla doesn't expose one here). */
    public static final class UUIDUtilPlus {
        public static final Codec<UUID> CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
        private UUIDUtilPlus() {}
    }
}
