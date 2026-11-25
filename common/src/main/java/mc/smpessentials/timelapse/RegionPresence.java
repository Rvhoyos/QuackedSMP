package mc.smpessentials.timelapse;

/**
 * Which chunks one region file actually stores, taken from its 4 KB location
 * table. Owns the table's bit layout and the region-to-world coordinate
 * arithmetic; holds no chunk data.
 */
public record RegionPresence(int regionX, int regionZ, long[] mask) {

    /** Chunks along one side of a region file. */
    public static final int SIDE = 32;

    private static final int CHUNKS = SIDE * SIDE;
    private static final int MASK_LONGS = CHUNKS / Long.SIZE;

    /**
     * Reads a region's presence from its header. A chunk is stored when its
     * 4-byte location-table entry is nonzero.
     *
     * @param header the first 4096 bytes of the region file
     */
    public static RegionPresence fromHeader(int regionX, int regionZ, byte[] header) {
        long[] mask = new long[MASK_LONGS];
        for (int i = 0; i < CHUNKS; i++) {
            int off = i * 4;
            if ((header[off] | header[off + 1] | header[off + 2] | header[off + 3]) != 0) {
                mask[i >>> 6] |= 1L << (i & 63);
            }
        }
        return new RegionPresence(regionX, regionZ, mask);
    }

    public boolean present(int localX, int localZ) {
        int i = localZ * SIDE + localX;
        return (mask[i >>> 6] & (1L << (i & 63))) != 0;
    }

    int chunkCount() {
        int n = 0;
        for (long word : mask) n += Long.bitCount(word);
        return n;
    }

    int chunkX(int localX) { return regionX * SIDE + localX; }
    int chunkZ(int localZ) { return regionZ * SIDE + localZ; }
}
