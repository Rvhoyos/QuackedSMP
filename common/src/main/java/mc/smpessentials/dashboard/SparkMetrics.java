package mc.smpessentials.dashboard;

import me.lucko.spark.api.SparkProvider;
import me.lucko.spark.api.statistic.StatisticWindow;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.api.statistic.types.DoubleStatistic;
import me.lucko.spark.api.statistic.types.GenericStatistic;

import java.util.Locale;

/**
 * Wraps Spark API calls safely — every method is guarded by try/catch because
 * Spark may be on the classpath but not yet fully initialised.
 */
public final class SparkMetrics {
    private SparkMetrics() {}

    public static String getTpsJson() {
        try {
            DoubleStatistic<StatisticWindow.TicksPerSecond> tps = SparkProvider.get().tps();
            if (tps == null) return "{\"error\":\"no_data\"}";
            return String.format(Locale.US,
                    "{\"tps5s\":%.2f,\"tps10s\":%.2f,\"tps1m\":%.2f,\"tps5m\":%.2f,\"tps15m\":%.2f}",
                    tps.poll(StatisticWindow.TicksPerSecond.SECONDS_5),
                    tps.poll(StatisticWindow.TicksPerSecond.SECONDS_10),
                    tps.poll(StatisticWindow.TicksPerSecond.MINUTES_1),
                    tps.poll(StatisticWindow.TicksPerSecond.MINUTES_5),
                    tps.poll(StatisticWindow.TicksPerSecond.MINUTES_15));
        } catch (Exception e) {
            return "{\"error\":\"spark_unavailable\"}";
        }
    }

    public static String getCpuJson() {
        try {
            DoubleStatistic<StatisticWindow.CpuUsage> cpu = SparkProvider.get().cpuProcess();
            if (cpu == null) return "{\"error\":\"no_data\"}";
            return String.format(Locale.US,
                    "{\"cpu10s\":%.4f,\"cpu1m\":%.4f,\"cpu15m\":%.4f}",
                    cpu.poll(StatisticWindow.CpuUsage.SECONDS_10),
                    cpu.poll(StatisticWindow.CpuUsage.MINUTES_1),
                    cpu.poll(StatisticWindow.CpuUsage.MINUTES_15));
        } catch (Exception e) {
            return "{\"error\":\"spark_unavailable\"}";
        }
    }

    public static String getMsptJson() {
        try {
            GenericStatistic<DoubleAverageInfo, StatisticWindow.MillisPerTick> mspt =
                    SparkProvider.get().mspt();
            if (mspt == null) return "{\"error\":\"no_data\"}";
            DoubleAverageInfo s10 = mspt.poll(StatisticWindow.MillisPerTick.SECONDS_10);
            DoubleAverageInfo m1  = mspt.poll(StatisticWindow.MillisPerTick.MINUTES_1);
            return String.format(Locale.US,
                    "{\"mean10s\":%.2f,\"max10s\":%.2f,\"mean1m\":%.2f,\"max1m\":%.2f}",
                    s10 != null ? s10.mean() : -1.0,
                    s10 != null ? s10.max()  : -1.0,
                    m1  != null ? m1.mean()  : -1.0,
                    m1  != null ? m1.max()   : -1.0);
        } catch (Exception e) {
            return "{\"error\":\"spark_unavailable\"}";
        }
    }
}
