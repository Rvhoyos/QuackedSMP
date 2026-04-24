package mc.smpessentials.commandblocks;

public record CommandBlockInfo(
        int x, int y, int z, String dimension, String command,
        String mode, boolean conditional, boolean auto, boolean powered,
        String customName, boolean trackOutput, int successCount
) {}
