package mc.smpessentials.timelapse;

import java.util.Locale;

/**
 * Live progress of one timelapse capture, polled by the dashboard while a
 * capture runs. It only records: {@link TimelapseService} drives the phases and
 * {@link WorldMapRenderer} reports painted chunks. Written by the single capture
 * worker and read by the HTTP handler thread; fields are volatile and any brief
 * read/write overlap only affects a progress display, so no locking is needed.
 */
final class CaptureProgress {

    // Longest side of the reported occupancy grid. Caps the JSON payload and the
    // number of SVG cells the dashboard draws, regardless of world size.
    private static final int MAX_CELLS = 64;

    private volatile String phase = "idle";
    // Which dimension is rendering now and its position in the sequential batch,
    // so the dashboard can label a multi-dimension capture ("overworld · 1/3").
    private volatile String dim = "";
    private volatile int    dimIndex;
    private volatile int    dimCount;
    private volatile int    chunksDone;
    private volatile int    chunksTotal;
    private volatile int    displayW;
    private volatile int    displayH;
    // One byte per display cell: 0 unscanned, 1 scanned-empty, 2 scanned-land.
    private volatile byte[] cells = new byte[0];

    // Source chunk extent, used to map a chunk to its display cell.
    private int minChunkX, minChunkZ, widthChunks, heightChunks;

    /** Begins a capture over {@code bounds}: sizes the grid and clears counts. */
    void begin(ChunkBounds bounds) {
        minChunkX    = bounds.minChunkX();
        minChunkZ    = bounds.minChunkZ();
        widthChunks  = bounds.widthChunks();
        heightChunks = bounds.heightChunks();
        // Scale both sides by the same factor so the grid keeps the world's
        // aspect ratio, longer side capped at MAX_CELLS.
        int longer = Math.max(widthChunks, heightChunks);
        if (longer <= MAX_CELLS) {
            displayW = widthChunks;
            displayH = heightChunks;
        } else {
            displayW = Math.max(1, widthChunks  * MAX_CELLS / longer);
            displayH = Math.max(1, heightChunks * MAX_CELLS / longer);
        }
        cells        = new byte[displayW * displayH];
        chunksDone   = 0;
        chunksTotal  = widthChunks * heightChunks;
    }

    void phase(String phase) { this.phase = phase; }

    /** Marks the dimension currently rendering, 1-based {@code index} of {@code count}. */
    void beginDim(String dimId, int index, int count) {
        dim      = dimId;
        dimIndex = index;
        dimCount = count;
    }

    /** Clears back to idle when no capture is running. */
    void idle() {
        phase = "idle";
        dim = "";
        dimIndex = dimCount = 0;
        chunksDone = chunksTotal = displayW = displayH = 0;
        cells = new byte[0];
    }

    /**
     * Records one scanned chunk, upgrading its display cell. Called in the same
     * cx-major order the paint loop uses, once per chunk whether or not it held
     * data, so the reported sweep stays smooth.
     */
    void markChunk(int chunkX, int chunkZ, boolean present) {
        chunksDone++;
        byte[] c = cells;
        if (c.length == 0) return;
        int dx = (int) ((long) (chunkX - minChunkX) * displayW / Math.max(1, widthChunks));
        int dz = (int) ((long) (chunkZ - minChunkZ) * displayH / Math.max(1, heightChunks));
        if (dx < 0 || dx >= displayW || dz < 0 || dz >= displayH) return;
        int idx = dz * displayW + dx;
        byte want = (byte) (present ? 2 : 1);
        if (want > c[idx]) c[idx] = want;
    }

    /** Compact JSON snapshot for the dashboard poll; cells is a run of '0'/'1'/'2'. */
    String toJson() {
        byte[] c = cells;
        StringBuilder cellStr = new StringBuilder(c.length);
        for (byte b : c) cellStr.append((char) ('0' + b));
        return String.format(Locale.US,
                "{\"phase\":\"%s\",\"dim\":\"%s\",\"dimIndex\":%d,\"dimCount\":%d,"
                        + "\"chunksDone\":%d,\"chunksTotal\":%d,\"w\":%d,\"h\":%d,\"cells\":\"%s\"}",
                phase, dim, dimIndex, dimCount, chunksDone, chunksTotal, displayW, displayH, cellStr);
    }
}
