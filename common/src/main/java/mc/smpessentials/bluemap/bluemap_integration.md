# BlueMap Integration Guide

This document outlines the standard procedure for integrating custom markers and features into [BlueMap v5+](https://bluemap.bluecolored.de/) for this NeoForge project.

## Adding Custom SVG Icons to POIMarkers

BlueMap's frontend enforces a strict **Content-Security-Policy (CSP)**. 
- You **cannot** use `data:image/svg+xml;base64,...`
- You **cannot** use external URLs (e.g., `https://raw.githubusercontent.com/...`)

Attempting to inject these directly into `POIMarker.builder().icon("...", ...)` will cause UI parsing errors, and the entire `MarkerSet` will vanish from the BlueMap sidebar silently.

### The Correct Approach: Native AssetStorage

To bypass the CSP and keep files locally served by BlueMap's internal webserver, you must write your SVG byte array directly into the map's `AssetStorage`.

```java
// 1. Define your SVG as a raw string constant
private static final String MY_CUSTOM_SVG = "<svg ...> ... </svg>";

// 2. Upload it to the AssetStorage for EACH map during initialization
for (BlueMapMap map : api.getMaps()) {
    try {
        if (!map.getAssetStorage().assetExists("my_custom_icon.svg")) {
            try (java.io.OutputStream os = map.getAssetStorage().writeAsset("my_custom_icon.svg")) {
                os.write(MY_CUSTOM_SVG.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    } catch (Exception e) {
        LOGGER.error("Failed to write BlueMap asset", e);
    }
}

// 3. Retrieve the generated relative URL and use it in your POIMarker
String iconUrl = map.getAssetStorage().getAssetUrl("my_custom_icon.svg");

POIMarker marker = POIMarker.builder()
        .label("My custom POI")
        .position(x, y, z)
        .icon(iconUrl, 16, 16) // Size is defined here (width, height) in pixels
        .build();
```

## Parsing Offline Player Data (Minecraft 1.21.11+)

If you need to retrieve offline player data (like their home/respawn location), you must read their `.dat` file in the `world/playerdata/` directory.

**CRITICAL 1.21.11 BEHAVIOR CHANGE:**
In previous versions of Minecraft, respawn data was grouped under a CompoundTag named `respawn_data`. 

As of Minecraft 1.21+, `respawn_data` no longer exists in the raw `.dat` file. The `pos` (an IntArray `[X, Y, Z]`) and `dimension` (a String, e.g., `"minecraft:overworld"`) keys are now stored **directly** inside the `respawn` CompoundTag.

### The Correct Approach: Reading NbtIo

```java
try {
    CompoundTag nbt = NbtIo.readCompressed(datFile.toPath(), NbtAccounter.unlimitedHeap());
    if (nbt.contains("SpawnX") && nbt.contains("SpawnY") && nbt.contains("SpawnZ") && nbt.contains("SpawnDimension")) {
        // Fallback for older formats / vanilla bed respawns
        int x = nbt.getInt("SpawnX");
        int y = nbt.getInt("SpawnY");
        int z = nbt.getInt("SpawnZ");
        String dimStr = nbt.getString("SpawnDimension");
    } else if (nbt.contains("respawn", 10)) { // 10 is CompoundTag
        CompoundTag respawnNode = nbt.getCompound("respawn");
        if (respawnNode.contains("pos") && respawnNode.contains("dimension")) {
            // New 1.21+ QuackedSMP / Custom format
            int[] posArray = respawnNode.getIntArray("pos");
            String dimStr = respawnNode.getString("dimension");
            
            if (posArray.length == 3) {
                int x = posArray[0];
                int y = posArray[1];
                int z = posArray[2];
                // Process...
            }
        }
    }
} catch (Exception e) {
    LOGGER.error("Failed to read NBT", e);
}
```

## Creating Identifiers from Strings (Minecraft 1.21.11+)

When creating an `Identifier` (previously `ResourceLocation` on Forge) from a string like `"minecraft:overworld"`, do **NOT** use `Identifier.fromNamespaceAndPath()`. Instead, simply use the built parser which handles the string safely:

```java
// CORRECT:
Identifier id = Identifier.parse("minecraft:the_nether");

// INCORRECT (Will throw exceptions and corrupt class loading):
Identifier id = Identifier.fromNamespaceAndPath("minecraft", "the_nether"); 
```
