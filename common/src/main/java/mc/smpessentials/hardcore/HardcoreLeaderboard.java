package mc.smpessentials.hardcore;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Pure read-model derived from finished-run history plus active sessions. Holds no state of its
// own and does no persistence, packet work, or formatting. Single source of truth for the
// leaderboard, reused by the command, the sidebar, and the dashboard.
//
// Two kinds of output: a shared leaderboard everyone sees identically (rankings, fun records,
// counters) and a per-player card (statFor). Names are resolved by consumers, which hold the
// server; this model deals only in UUIDs.
public final class HardcoreLeaderboard {

    public record RunEntry(String name, String creatorName, UUID creator, HardcoreRun.Outcome outcome,
                           long durationMillis, int peakPlayers, int deaths, int participantCount, long endedAt) {}

    public record ActiveEntry(String name, long runMillis, int aliveCount, int deadCount,
                              int deaths, int threshold) {}

    public record PlayerStat(UUID player, int runs, int wins, int losses,
                             long totalRunMillis, long fastestWinMillis, long longestRunMillis) {
        public double winRate() { return runs == 0 ? 0.0 : (double) wins / runs; }
        public boolean isEmpty() { return runs == 0; }
    }

    private final List<RunEntry> fastestWins;
    private final List<RunEntry> longestRuns;
    private final List<ActiveEntry> activeRuns;
    private final List<PlayerStat> topPlayers;
    private final Map<UUID, PlayerStat> byPlayer;

    // Fun records (single entries, may be null when no run qualifies)
    private final RunEntry bloodiestRun;
    private final RunEntry closestCall;
    private final RunEntry biggestParty;
    private final RunEntry loneWolf;
    private final PlayerStat veteran;
    private final PlayerStat champion;

    // Counters
    private final int bodyCount;
    private final int dragonsSlain;
    private final int totalRuns;

    private HardcoreLeaderboard(List<RunEntry> fastestWins, List<RunEntry> longestRuns,
                               List<ActiveEntry> activeRuns, List<PlayerStat> topPlayers,
                               Map<UUID, PlayerStat> byPlayer, RunEntry bloodiestRun, RunEntry closestCall,
                               RunEntry biggestParty, RunEntry loneWolf, PlayerStat veteran, PlayerStat champion,
                               int bodyCount, int dragonsSlain, int totalRuns) {
        this.fastestWins = fastestWins;
        this.longestRuns = longestRuns;
        this.activeRuns = activeRuns;
        this.topPlayers = topPlayers;
        this.byPlayer = byPlayer;
        this.bloodiestRun = bloodiestRun;
        this.closestCall = closestCall;
        this.biggestParty = biggestParty;
        this.loneWolf = loneWolf;
        this.veteran = veteran;
        this.champion = champion;
        this.bodyCount = bodyCount;
        this.dragonsSlain = dragonsSlain;
        this.totalRuns = totalRuns;
    }

    public List<RunEntry> fastestWins()   { return fastestWins; }
    public List<RunEntry> longestRuns()   { return longestRuns; }
    public List<ActiveEntry> activeRuns() { return activeRuns; }
    public List<PlayerStat> topPlayers()  { return topPlayers; }
    public RunEntry bloodiestRun()        { return bloodiestRun; }
    public RunEntry closestCall()         { return closestCall; }
    public RunEntry biggestParty()        { return biggestParty; }
    public RunEntry loneWolf()            { return loneWolf; }
    public PlayerStat veteran()           { return veteran; }
    public PlayerStat champion()          { return champion; }
    public int bodyCount()                { return bodyCount; }
    public int dragonsSlain()             { return dragonsSlain; }
    public int totalRuns()                { return totalRuns; }

    // The viewer's own record, or an empty stat if they have no finished runs.
    public PlayerStat statFor(UUID player) {
        return byPlayer.getOrDefault(player, new PlayerStat(player, 0, 0, 0, 0, 0, 0));
    }

    public static HardcoreLeaderboard of(HardcoreSavedData data, long now) {
        List<HardcoreRun> runs = data.getCompletedRuns();
        List<RunEntry> finished = runs.stream().map(HardcoreLeaderboard::toEntry).collect(Collectors.toList());

        List<RunEntry> fastestWins = finished.stream()
                .filter(e -> e.outcome() == HardcoreRun.Outcome.WIN)
                .sorted(Comparator.comparingLong(RunEntry::durationMillis))
                .collect(Collectors.toList());

        List<RunEntry> longestRuns = finished.stream()
                .sorted(Comparator.comparingLong(RunEntry::durationMillis).reversed())
                .collect(Collectors.toList());

        List<ActiveEntry> active = data.allSessions().stream()
                .map(s -> new ActiveEntry(s.getName(), s.runMillis(now), s.getAliveCount(),
                        s.getDeadCount(), s.getDeaths(), s.getThreshold()))
                .sorted(Comparator.comparingLong(ActiveEntry::runMillis).reversed())
                .collect(Collectors.toList());

        // Fun records
        RunEntry bloodiest = finished.stream()
                .filter(e -> e.deaths() > 0)
                .max(Comparator.comparingInt(RunEntry::deaths)).orElse(null);
        RunEntry closestCall = finished.stream()
                .filter(e -> e.outcome() == HardcoreRun.Outcome.WIN && e.deaths() > 0)
                .max(Comparator.comparingInt(RunEntry::deaths)).orElse(null);
        RunEntry biggestParty = finished.stream()
                .max(Comparator.comparingInt(RunEntry::peakPlayers)).orElse(null);
        RunEntry loneWolf = finished.stream()
                .filter(e -> e.outcome() == HardcoreRun.Outcome.WIN && e.peakPlayers() <= 1)
                .min(Comparator.comparingLong(RunEntry::durationMillis)).orElse(null);

        int bodyCount = finished.stream().mapToInt(RunEntry::deaths).sum();
        int dragonsSlain = (int) finished.stream().filter(e -> e.outcome() == HardcoreRun.Outcome.WIN).count();

        // Per-player aggregates from run participants.
        Map<UUID, long[]> agg = new HashMap<>(); // [runs, wins, losses, totalMs, fastestWinMs(-1), longestMs]
        for (HardcoreRun run : runs) {
            long dur = run.durationMillis();
            for (UUID p : run.participants()) {
                long[] a = agg.computeIfAbsent(p, k -> new long[]{0, 0, 0, 0, -1, 0});
                a[0]++;
                if (run.outcome() == HardcoreRun.Outcome.WIN) {
                    a[1]++;
                    if (a[4] < 0 || dur < a[4]) a[4] = dur;
                } else if (run.outcome() == HardcoreRun.Outcome.LOSS) {
                    a[2]++;
                }
                a[3] += dur;
                if (dur > a[5]) a[5] = dur;
            }
        }
        Map<UUID, PlayerStat> byPlayer = new HashMap<>();
        agg.forEach((uuid, a) -> byPlayer.put(uuid, new PlayerStat(
                uuid, (int) a[0], (int) a[1], (int) a[2], a[3], a[4] < 0 ? 0 : a[4], a[5])));

        List<PlayerStat> topPlayers = byPlayer.values().stream()
                .sorted(Comparator.comparingLong(PlayerStat::totalRunMillis).reversed())
                .collect(Collectors.toList());

        PlayerStat veteran = topPlayers.isEmpty() ? null : topPlayers.get(0);
        PlayerStat champion = byPlayer.values().stream()
                .filter(s -> s.wins() > 0)
                .max(Comparator.comparingInt(PlayerStat::wins)).orElse(null);

        return new HardcoreLeaderboard(fastestWins, longestRuns, active, topPlayers, byPlayer,
                bloodiest, closestCall, biggestParty, loneWolf, veteran, champion,
                bodyCount, dragonsSlain, finished.size());
    }

    private static RunEntry toEntry(HardcoreRun r) {
        return new RunEntry(r.name(), r.creatorName(), r.creator(), r.outcome(),
                r.durationMillis(), r.peakPlayers(), r.deaths(), r.participants().size(), r.endedAt());
    }
}
