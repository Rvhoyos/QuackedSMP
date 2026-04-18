package mc.smpessentials.shops;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

// Virtual emerald bank. Players deposit/withdraw physical emeralds and transfer between accounts.
// NOT used for shop purchases — shops require physical currency items in player inventory.
public final class EconomyData extends SavedData {

    private final Map<UUID, Long> balances = new HashMap<>();

    public EconomyData() {}

    private record BalanceEntry(UUID uuid, long balance) {}

    private static final Codec<BalanceEntry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(BalanceEntry::uuid),
            Codec.LONG.fieldOf("balance").forGetter(BalanceEntry::balance))
            .apply(i, BalanceEntry::new));

    private List<BalanceEntry> toEntries() {
        List<BalanceEntry> list = new ArrayList<>(balances.size());
        for (Map.Entry<UUID, Long> e : balances.entrySet()) {
            list.add(new BalanceEntry(e.getKey(), e.getValue()));
        }
        return list;
    }

    private static EconomyData fromEntries(List<BalanceEntry> entries) {
        EconomyData d = new EconomyData();
        for (BalanceEntry e : entries) {
            d.balances.put(e.uuid(), e.balance());
        }
        return d;
    }

    public static final Codec<EconomyData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRY_CODEC.listOf().optionalFieldOf("balances", List.of())
                    .forGetter(EconomyData::toEntries))
            .apply(i, EconomyData::fromEntries));

    public static final SavedDataType<EconomyData> TYPE = new SavedDataType<>(
            "quackedsmp_economy",
            EconomyData::new,
            CODEC,
            DataFixTypes.LEVEL);

    public static EconomyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public long getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0L);
    }

    public void deposit(UUID uuid, long amount) {
        balances.merge(uuid, amount, Long::sum);
        setDirty();
    }

    // Returns false if insufficient funds.
    public boolean withdraw(UUID uuid, long amount) {
        long current = getBalance(uuid);
        if (current < amount) return false;
        long remaining = current - amount;
        if (remaining == 0) {
            balances.remove(uuid);
        } else {
            balances.put(uuid, remaining);
        }
        setDirty();
        return true;
    }

    // Returns false if sender has insufficient funds.
    public boolean transfer(UUID from, UUID to, long amount) {
        if (!withdraw(from, amount)) return false;
        deposit(to, amount);
        return true;
    }

    public Map<UUID, Long> allBalances() {
        return Collections.unmodifiableMap(balances);
    }
}
