package mc.smpessentials.timelapse;

/** Inclusive bounding box of populated chunks, in chunk coordinates. */
record ChunkBounds(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {

    int widthChunks()  { return maxChunkX - minChunkX + 1; }
    int heightChunks() { return maxChunkZ - minChunkZ + 1; }

    int minBlockX() { return minChunkX << 4; }
    int minBlockZ() { return minChunkZ << 4; }
    int widthBlocks()  { return widthChunks()  << 4; }
    int heightBlocks() { return heightChunks() << 4; }
}
