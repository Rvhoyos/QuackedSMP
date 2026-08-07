package mc.smpessentials.hardcore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mc.smpessentials.config.SmpConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.*;

public final class HardcoreSavedData extends SavedData {

    // Codec for CompoundTag values in maps, passthrough in NbtOps context
    private static final Codec<CompoundTag> COMPOUND_CODEC = Codec.PASSTHROUGH.comapFlatMap(
            dynamic -> {
                Tag tag = dynamic.convert(NbtOps.INSTANCE).getValue();
                return tag instanceof CompoundTag ct
                        ? DataResult.success(ct)
                        : DataResult.error(() -> "Expected compound tag");
            },
            ct -> new Dynamic<>(NbtOps.INSTANCE, ct.copy())
    );

    static final Codec<SessionData> SESSION_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(s -> s.name),
            Codec.STRING.fieldOf("password").forGetter(s -> s.password),
            UUIDUtil.CODEC.fieldOf("creator").forGetter(s -> s.creator),
            Codec.INT.fieldOf("start_x").forGetter(s -> s.startX),
            Codec.INT.fieldOf("start_y").forGetter(s -> s.startY),
            Codec.INT.fieldOf("start_z").forGetter(s -> s.startZ),
            Codec.STRING.fieldOf("start_dim").forGetter(s -> s.startDim),
            UUIDUtil.CODEC.listOf().fieldOf("alive").forGetter(s -> List.copyOf(s.alive)),
            UUIDUtil.CODEC.listOf().fieldOf("dead").forGetter(s -> List.copyOf(s.dead)),
            Codec.INT.fieldOf("peak_players").forGetter(s -> s.peakPlayers),
            Codec.INT.fieldOf("deaths").forGetter(s -> s.deaths),
            Codec.unboundedMap(Codec.STRING, COMPOUND_CODEC).optionalFieldOf("suspended", Map.of())
                    .forGetter(s -> s.suspendedToStringMap())
    ).apply(i, SessionData::new));

    public static final Codec<HardcoreSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            SESSION_CODEC.listOf().optionalFieldOf("sessions", List.of())
                    .forGetter(d -> List.copyOf(d.sessions.values())),
            Codec.unboundedMap(Codec.STRING, COMPOUND_CODEC)
                    .optionalFieldOf("stashes", Map.of())
                    .forGetter(d -> d.stashesToStringMap()),
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .optionalFieldOf("player_sessions", Map.of())
                    .forGetter(d -> d.playerSessionsToStringMap())
    ).apply(i, HardcoreSavedData::fromCodec));

    public static final SavedDataType<HardcoreSavedData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace("quackedsmp_hardcore"),
            HardcoreSavedData::new,
            CODEC,
            DataFixTypes.LEVEL);

    // Runtime state
    private final Map<String, SessionData> sessions = new LinkedHashMap<>();
    private final Map<UUID, CompoundTag> stashes = new HashMap<>();
    private final Map<UUID, String> playerSessions = new HashMap<>();

    // Transient, not persisted. Tracks the hardcore flag actually sent in each player's login
    // packet, so we can warn when live membership drifts from what the client HUD shows.
    private final Set<UUID> heartsShownHardcore = new HashSet<>();
    // Transient, not persisted. Last-seen dimension per player, for End-entry edge detection.
    private final Map<UUID, String> lastDim = new HashMap<>();

    public HardcoreSavedData() {}

    private static HardcoreSavedData fromCodec(List<SessionData> sessionList,
                                                Map<String, CompoundTag> stashMap,
                                                Map<String, String> playerSessionMap) {
        HardcoreSavedData data = new HardcoreSavedData();
        for (SessionData s : sessionList) {
            data.sessions.put(s.name, s);
        }
        stashMap.forEach((k, v) -> data.stashes.put(UUID.fromString(k), v));
        playerSessionMap.forEach((k, v) -> data.playerSessions.put(UUID.fromString(k), v));
        return data;
    }

    private Map<String, CompoundTag> stashesToStringMap() {
        Map<String, CompoundTag> map = new HashMap<>();
        stashes.forEach((k, v) -> map.put(k.toString(), v));
        return map;
    }

    private Map<String, String> playerSessionsToStringMap() {
        Map<String, String> map = new HashMap<>();
        playerSessions.forEach((k, v) -> map.put(k.toString(), v));
        return map;
    }

    public static HardcoreSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // Returns true (and sends denial message) if the player is in a hardcore session.
    // Used by other commands to block usage during hardcore.
    public static boolean denyIfInSession(net.minecraft.commands.CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) return false;
        HardcoreSavedData data = get(src.getServer());
        if (data.getPlayerSessionName(player.getUUID()) != null) {
            src.sendFailure(Component.literal(
                    "\u00a7c[Hardcore] You cannot use this command during a hardcore session."));
            return true;
        }
        return false;
    }

    // ── Hardcore hearts HUD ─────────────────────────────────────

    // Whether this player's client should render the withered hardcore heart HUD.
    // Keys on live membership, so a stepped-out (suspended) player correctly reads as false.
    public static boolean shouldShowHardcoreHearts(ServerPlayer player) {
        if (!SmpConfig.HARDCORE_WITHERED_HEARTS) return false;
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        return get(server).getPlayerSessionName(player.getUUID()) != null;
    }

    // Records the hardcore flag the login packet just sent this player. Must run at login
    // BEFORE onPlayerReconnect, which can change membership.
    public void markHeartsSent(ServerPlayer player) {
        if (shouldShowHardcoreHearts(player)) {
            heartsShownHardcore.add(player.getUUID());
        } else {
            heartsShownHardcore.remove(player.getUUID());
        }
    }

    // Warns the player if their live membership no longer matches the heart HUD their client
    // is showing (which only updates on reconnect). Silent when they already match.
    private void warnHeartsMismatchIfAny(ServerPlayer player) {
        if (!SmpConfig.HARDCORE_WITHERED_HEARTS) return;
        UUID uuid = player.getUUID();
        boolean shouldShow = playerSessions.get(uuid) != null;
        boolean clientShows = heartsShownHardcore.contains(uuid);
        if (shouldShow == clientShows) return;
        if (shouldShow) {
            player.sendSystemMessage(Component.literal(
                    "\u00a7e[Hardcore] Withered hearts appear the next time you reconnect. "
                            + "Reconnect whenever you like, no rush."));
        } else {
            player.sendSystemMessage(Component.literal(
                    "\u00a7e[Hardcore] Your hearts return to normal the next time you reconnect."));
        }
    }

    // ── Business logic ──────────────────────────────────────────

    public String createSession(String name, String password, ServerPlayer creator, ServerLevel level) {
        if (sessions.containsKey(name)) return "Session '" + name + "' already exists.";
        if (name.length() > 32) return "Session name too long (max 32).";
        if (password.isEmpty()) return "Password cannot be empty.";

        // Create auto-joins, so you must not already be in a session (else it would create
        // an orphan the creator can't join)
        if (playerSessions.containsKey(creator.getUUID())) {
            return "You are already in session '" + playerSessions.get(creator.getUUID())
                    + "'. Leave it before creating another.";
        }

        // One active authored session per person
        for (SessionData s : sessions.values()) {
            if (s.creator.equals(creator.getUUID())) {
                return "You already run a hardcore session ('" + s.name + "'). End it before creating another.";
            }
        }

        BlockPos pos = randomPosInBorder(level);
        SessionData session = new SessionData(
                name, password, creator.getUUID(),
                pos.getX(), pos.getY(), pos.getZ(),
                level.dimension().identifier().toString(),
                List.of(), List.of(), 0, 0, Map.of());
        sessions.put(name, session);
        setDirty();
        return null;
    }

    public String joinSession(String name, String password, ServerPlayer player) {
        SessionData session = sessions.get(name);
        if (session == null) return "Session '" + name + "' not found.";
        if (!session.password.equals(password)) return "Wrong password.";

        UUID uuid = player.getUUID();
        if (playerSessions.containsKey(uuid)) {
            return "You are already in session '" + playerSessions.get(uuid) + "'. Leave first.";
        }

        // Stepped-out player: resume exactly where they left off instead of a fresh start
        if (session.suspended.containsKey(uuid)) {
            resumeSession(session, player);
            return null;
        }

        // Stash full player state (inventory, equipment, game mode, XP, health, food, respawn point)
        stashes.put(uuid, savePlayerState(player));
        playerSessions.put(uuid, name);

        // Clear inventory, XP, and effects — start fresh
        player.getInventory().clearContent();
        clearEquipment(player);
        player.experienceLevel = 0;
        player.experienceProgress = 0f;
        player.totalExperience = 0;
        player.removeAllEffects();

        // Heal to full and reset hunger
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);

        // Set survival mode
        player.setGameMode(GameType.SURVIVAL);

        // Teleport to session start
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION,
                Identifier.parse(session.startDim));
        MinecraftServer srv = ((ServerLevel) player.level()).getServer();
        ServerLevel targetLevel = srv.getLevel(dimKey);
        if (targetLevel == null) targetLevel = srv.overworld();
        player.teleportTo(targetLevel,
                session.startX + 0.5, session.startY, session.startZ + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), false);

        // Add to alive
        session.alive.add(uuid);
        session.peakPlayers = Math.max(session.peakPlayers, session.alive.size());
        setDirty();

        player.sendSystemMessage(Component.literal(
                "\u00a7a[Hardcore] Joined session '" + name
                        + "'! Your inventory has been stashed. Stay alive!"));
        warnHeartsMismatchIfAny(player);
        return null;
    }

    // Restores a stepped-out player's full hardcore state and puts them back where they left off.
    private void resumeSession(SessionData session, ServerPlayer player) {
        UUID uuid = player.getUUID();

        // Stash the player's normal life before handing back their hardcore state
        stashes.put(uuid, savePlayerState(player));

        CompoundTag hardcore = session.suspended.remove(uuid);
        applyStashTag(player, hardcore);
        teleportToStashPos(player, hardcore);

        // Re-derive alive/dead from the restored game mode (spectator == dead)
        boolean dead = GameType.SPECTATOR.getName().equals(hardcore.getString("GameMode").orElse(""));
        if (dead) {
            session.dead.add(uuid);
        } else {
            session.alive.add(uuid);
            session.peakPlayers = Math.max(session.peakPlayers, session.alive.size());
        }
        playerSessions.put(uuid, session.name);
        setDirty();

        player.sendSystemMessage(Component.literal(
                "\u00a7a[Hardcore] Resumed session '" + session.name + "'. Welcome back!"));
        warnHeartsMismatchIfAny(player);
    }

    public String leaveSession(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String sessionName = playerSessions.get(uuid);
        if (sessionName == null) return "You are not in a hardcore session.";

        SessionData session = sessions.get(sessionName);
        if (session != null) {
            // Preserve full hardcore state so the player can rejoin exactly where they were
            session.suspended.put(uuid, savePlayerState(player));
            session.alive.remove(uuid);
            session.dead.remove(uuid);
        }
        playerSessions.remove(uuid);

        // Restore normal (pre-session) life and send them home
        restorePlayerState(player);
        teleportToSpawn(player);
        warnHeartsMismatchIfAny(player);

        setDirty();
        return null;
    }

    public void onPlayerDeath(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String sessionName = playerSessions.get(uuid);
        if (sessionName == null) return;

        SessionData session = sessions.get(sessionName);
        if (session == null) return;

        if (!session.alive.remove(uuid)) return; // was already dead
        session.dead.add(uuid);
        session.deaths++;
        setDirty();

        player.sendSystemMessage(Component.literal(
                "\u00a7c[Hardcore] You died! You are now spectating. Use /smp hardcore leave to exit."));
        player.sendSystemMessage(Component.literal(
                "\u00a77Tip: Click a player to spectate their POV. Press Shift to detach."));

        if (session.deaths >= session.getThreshold()) {
            resetSession(session, ((ServerLevel) player.level()).getServer());
        }
    }

    // Called after a hardcore player respawns to set them to spectator
    public void onPlayerRespawn(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String sessionName = playerSessions.get(uuid);
        if (sessionName == null) return;

        SessionData session = sessions.get(sessionName);
        if (session == null) return;

        if (session.dead.contains(uuid)) {
            player.setGameMode(GameType.SPECTATOR);
            // Teleport dead spectators to their death location
            player.getLastDeathLocation().ifPresent(deathPos -> {
                MinecraftServer srv = ((ServerLevel) player.level()).getServer();
                ServerLevel targetLevel = srv.getLevel(deathPos.dimension());
                if (targetLevel == null) targetLevel = srv.overworld();
                BlockPos pos = deathPos.pos();
                player.teleportTo(targetLevel,
                        pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        Set.of(), player.getYRot(), player.getXRot(), false);
            });
        }
    }

    // Refreshes the spectator action-bar HUD for dead session members: controls plus who
    // they are currently watching. Called every tick; self-throttles to once a second.
    public void tickSpectatorHud(ServerPlayer player) {
        if (player.tickCount % 20 != 0) return;
        UUID uuid = player.getUUID();
        String sessionName = playerSessions.get(uuid);
        if (sessionName == null) return;
        SessionData session = sessions.get(sessionName);
        if (session == null || !session.dead.contains(uuid)) return;

        net.minecraft.world.entity.Entity camera = player.getCamera();
        Component msg;
        if (camera != player) {
            msg = Component.literal("\u00a77Spectating \u00a7f" + camera.getName().getString()
                    + " \u00a78· \u00a77Sneak to detach");
        } else {
            msg = Component.literal(
                    "\u00a77Left-click a nearby player to view their POV \u00a78· \u00a77Sneak to exit");
        }
        player.sendSystemMessage(msg, true);
    }

    // Called when player reconnects to restore their hardcore state
    public void onPlayerReconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String sessionName = playerSessions.get(uuid);
        if (sessionName == null) return;

        SessionData session = sessions.get(sessionName);
        if (session == null) {
            // Session was removed while offline, restore and clean up
            restorePlayerState(player);
            teleportToSpawn(player);
            playerSessions.remove(uuid);
            setDirty();
            player.sendSystemMessage(Component.literal(
                    "\u00a7e[Hardcore] Your session no longer exists. Inventory restored."));
            warnHeartsMismatchIfAny(player);
            return;
        }

        if (session.dead.contains(uuid)) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal(
                    "\u00a7c[Hardcore] You are dead in session '" + sessionName
                            + "'. Spectating until session ends or you /smp hardcore leave."));
        } else if (session.alive.contains(uuid)) {
            player.setGameMode(GameType.SURVIVAL);
            player.sendSystemMessage(Component.literal(
                    "\u00a7a[Hardcore] Resumed session '" + sessionName + "'. Stay alive!"));
        }
    }

    private void resetSession(SessionData session, MinecraftServer server) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("\u00a7c[Hardcore] Session '" + session.name
                        + "' has ended! Death threshold reached (" + session.deaths + " deaths)."),
                false);

        restoreAllParticipants(session, server,
                "\u00a7e[Hardcore] Session over. Your inventory has been restored.");

        sessions.remove(session.name);
        setDirty();
    }

    // Force-ends a session (OP action). Restores all participants and removes the session entirely.
    public String forceEndSession(String name, MinecraftServer server) {
        SessionData session = sessions.get(name);
        if (session == null) return "Session '" + name + "' not found.";

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("\u00a7c[Hardcore] Session '" + name
                        + "' has been ended by an operator."),
                false);

        restoreAllParticipants(session, server,
                "\u00a7e[Hardcore] Session ended. Your inventory has been restored.");

        sessions.remove(name);
        setDirty();
        return null;
    }

    // Restores full player state, teleports to spawn, and messages all online participants.
    // Offline players stay in playerSessions so onPlayerReconnect can restore them.
    private void restoreAllParticipants(SessionData session, MinecraftServer server, String message) {
        Set<UUID> allParticipants = new HashSet<>();
        allParticipants.addAll(session.alive);
        allParticipants.addAll(session.dead);

        for (UUID uuid : allParticipants) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                restorePlayerState(player);
                teleportToSpawn(player);
                player.sendSystemMessage(Component.literal(message));
                playerSessions.remove(uuid);
                warnHeartsMismatchIfAny(player);
            }
        }
    }

    // ── Dragon respawn on End entry + dragon-kill win (TODO #9) ──

    // Edge-detects a session member entering the End and guarantees a dragon to fight.
    // Called every tick from the per-player loop; cheap (one map lookup) until the dim changes.
    public void tickEndEntry(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String cur = player.level().dimension().identifier().toString();
        String prev = lastDim.get(uuid);
        if (cur.equals(prev)) return;
        lastDim.put(uuid, cur);
        if (!SmpConfig.HARDCORE_ENABLED) return;
        if (!"minecraft:the_end".equals(cur)) return;
        if (playerSessions.get(uuid) == null) return;
        ensureDragon((ServerLevel) player.level());
    }

    // Respawns the End dragon only if none is currently present, so an in-progress fight
    // (or a fresh vanilla dragon) is never wiped.
    private void ensureDragon(ServerLevel end) {
        for (net.minecraft.world.entity.Entity entity : end.getAllEntities()) {
            if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
                return;
            }
        }
        mc.smpessentials.commands.EndResetLogic.resetDragon(end.getServer(), true);
    }

    // Called when an ender dragon dies. Every session with an alive member currently in the
    // End wins from this single death, so two sessions meeting in the End both win at once.
    public void onDragonKilled(ServerLevel end) {
        if (!SmpConfig.HARDCORE_ENABLED) return;
        Set<SessionData> winners = new HashSet<>();
        for (ServerPlayer player : end.players()) {
            String sessionName = playerSessions.get(player.getUUID());
            if (sessionName == null) continue;
            SessionData session = sessions.get(sessionName);
            if (session != null && session.alive.contains(player.getUUID())) {
                winners.add(session);
            }
        }
        for (SessionData session : winners) {
            winSession(session, end.getServer());
        }
    }

    // Ends a session as a WIN: broadcasts victory, restores all participants, removes it.
    private void winSession(SessionData session, MinecraftServer server) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("\u00a76\u00a7l[Hardcore] Session '" + session.name
                        + "' WON! The dragon has been slain."),
                false);

        restoreAllParticipants(session, server,
                "\u00a76[Hardcore] Victory! Your inventory has been restored.");

        sessions.remove(session.name);
        setDirty();
    }

    // ── Query methods ───────────────────────────────────────────

    public SessionData getSession(String name) {
        return sessions.get(name);
    }

    public String getPlayerSessionName(UUID uuid) {
        return playerSessions.get(uuid);
    }

    public Collection<SessionData> allSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    // ── Inventory stash helpers ─────────────────────────────────

    private static final EquipmentSlot[] STASH_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND
    };

    // Saves inventory, equipment, game mode, XP, health, food, and respawn point to a CompoundTag.
    private CompoundTag savePlayerState(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        var registryOps = ((ServerLevel) player.level()).getServer()
                .registryAccess().createSerializationContext(NbtOps.INSTANCE);

        // Main inventory
        ListTag items = new ListTag();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt("Slot", i);
                slotTag.put("Item", ItemStack.OPTIONAL_CODEC.encodeStart(registryOps, stack).getOrThrow());
                items.add(slotTag);
            }
        }
        tag.put("Items", items);

        // Equipment (armor + offhand)
        ListTag armor = new ListTag();
        for (EquipmentSlot slot : STASH_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putString("Slot", slot.name());
                slotTag.put("Item", ItemStack.OPTIONAL_CODEC.encodeStart(registryOps, stack).getOrThrow());
                armor.add(slotTag);
            }
        }
        tag.put("Armor", armor);

        // Game mode
        tag.putString("GameMode", player.gameMode.getGameModeForPlayer().getName());

        // XP
        tag.putInt("XpLevel", player.experienceLevel);
        tag.putFloat("XpProgress", player.experienceProgress);
        tag.putInt("TotalXp", player.totalExperience);

        // Health and hunger
        tag.putFloat("Health", player.getHealth());
        tag.putInt("FoodLevel", player.getFoodData().getFoodLevel());
        tag.putFloat("Saturation", player.getFoodData().getSaturationLevel());

        // Respawn point (bed/anchor)
        ServerPlayer.RespawnConfig respawnCfg = player.getRespawnConfig();
        if (respawnCfg != null) {
            ServerPlayer.RespawnConfig.CODEC.encodeStart(NbtOps.INSTANCE, respawnCfg)
                    .result().ifPresent(nbt -> tag.put("RespawnConfig", nbt));
        }

        // Position and dimension (used to rejoin exactly where the player stepped out)
        tag.putDouble("PosX", player.getX());
        tag.putDouble("PosY", player.getY());
        tag.putDouble("PosZ", player.getZ());
        tag.putString("PosDim", player.level().dimension().identifier().toString());

        return tag;
    }

    // Restores the player's normal (pre-session) life from the top-level stash.
    private void restorePlayerState(ServerPlayer player) {
        applyStashTag(player, stashes.remove(player.getUUID()));
    }

    // Restores inventory, equipment, game mode, XP, health, food, and respawn point from a stash tag.
    private void applyStashTag(ServerPlayer player, CompoundTag tag) {
        if (tag == null) return;

        var registryOps = ((ServerLevel) player.level()).getServer()
                .registryAccess().createSerializationContext(NbtOps.INSTANCE);

        // Clear current inventory
        player.getInventory().clearContent();
        clearEquipment(player);

        // Restore main inventory
        tag.getList("Items").ifPresent(items -> {
            for (int i = 0; i < items.size(); i++) {
                items.getCompound(i).ifPresent(slotTag -> {
                    int slot = slotTag.getInt("Slot").orElse(0);
                    Tag itemTag = slotTag.get("Item");
                    if (itemTag != null) {
                        ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(registryOps, itemTag)
                                .result().orElse(ItemStack.EMPTY);
                        if (slot >= 0 && slot < player.getInventory().getContainerSize()) {
                            player.getInventory().setItem(slot, stack);
                        }
                    }
                });
            }
        });

        // Restore equipment
        tag.getList("Armor").ifPresent(armor -> {
            for (int i = 0; i < armor.size(); i++) {
                armor.getCompound(i).ifPresent(slotTag -> {
                    String slotName = slotTag.getString("Slot").orElse("");
                    try {
                        EquipmentSlot slot = EquipmentSlot.valueOf(slotName);
                        Tag itemTag = slotTag.get("Item");
                        if (itemTag != null) {
                            ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(registryOps, itemTag)
                                    .result().orElse(ItemStack.EMPTY);
                            player.setItemSlot(slot, stack);
                        }
                    } catch (IllegalArgumentException ignored) {}
                });
            }
        });

        // Restore game mode
        tag.getString("GameMode").ifPresent(name -> {
            GameType mode = GameType.byName(name, GameType.SURVIVAL);
            player.setGameMode(mode);
        });

        // Restore XP
        player.experienceLevel = tag.getInt("XpLevel").orElse(0);
        player.experienceProgress = tag.getFloat("XpProgress").orElse(0f);
        player.totalExperience = tag.getInt("TotalXp").orElse(0);

        // Restore health and hunger — skip for dead players (session-ending death calls
        // restoreAllParticipants while the dying player is still in death state; setting
        // health on a dead player desyncs client/server). Respawn gives them max health anyway.
        if (player.isAlive()) {
            player.setHealth(tag.getFloat("Health").orElse(player.getMaxHealth()));
            player.getFoodData().setFoodLevel(tag.getInt("FoodLevel").orElse(20));
            player.getFoodData().setSaturation(tag.getFloat("Saturation").orElse(5.0f));
        }

        // Restore respawn point (bed/anchor)
        Tag respawnTag = tag.get("RespawnConfig");
        if (respawnTag != null) {
            ServerPlayer.RespawnConfig restored = ServerPlayer.RespawnConfig.CODEC
                    .parse(NbtOps.INSTANCE, respawnTag).result().orElse(null);
            player.setRespawnPosition(restored, false);
        } else {
            player.setRespawnPosition(null, false);
        }
    }

    private static void clearEquipment(ServerPlayer player) {
        for (EquipmentSlot slot : STASH_SLOTS) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void teleportToSpawn(ServerPlayer player) {
        ServerLevel overworld = ((ServerLevel) player.level()).getServer().overworld();
        BlockPos spawn = overworld.getRespawnData().pos();
        BlockPos safe = player.adjustSpawnLocation(overworld, spawn);
        player.teleportTo(overworld,
                safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot(), false);
    }

    // Teleports the player to the position and dimension recorded in a stash tag.
    private static void teleportToStashPos(ServerPlayer player, CompoundTag tag) {
        String dimStr = tag.getString("PosDim").orElse(null);
        if (dimStr == null) return;
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimStr));
        MinecraftServer srv = ((ServerLevel) player.level()).getServer();
        ServerLevel target = srv.getLevel(dimKey);
        if (target == null) target = srv.overworld();
        double x = tag.getDouble("PosX").orElse(player.getX());
        double y = tag.getDouble("PosY").orElse(player.getY());
        double z = tag.getDouble("PosZ").orElse(player.getZ());
        player.teleportTo(target, x, y, z, Set.of(), player.getYRot(), player.getXRot(), false);
    }

    private static final int MIN_DISTANCE_FROM_CENTER = 1000;
    private static final int BORDER_PADDING = 100;

    // Returns a surface position at the given XZ, generating the chunk if needed.
    private static BlockPos surfacePos(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return new BlockPos(x, y, z);
    }

    // Picks a random surface position between MIN_DISTANCE_FROM_CENTER and the world border.
    // Uses sqrt-weighted radius sampling for uniform distribution over the 2D annular area.
    static BlockPos randomPosInBorder(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        double cx = border.getCenterX();
        double cz = border.getCenterZ();
        double halfSize = (border.getSize() / 2.0) - BORDER_PADDING;

        if (halfSize <= MIN_DISTANCE_FROM_CENTER) {
            int x = (int) (cx + halfSize * 0.8);
            int z = (int) (cz + halfSize * 0.8);
            return surfacePos(level, x, z);
        }

        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        double minR = MIN_DISTANCE_FROM_CENTER;
        double maxR = halfSize;
        // sqrt sampling: r = sqrt(uniform(minR^2, maxR^2)) gives uniform density over area
        double dist = Math.sqrt(minR * minR + rng.nextDouble() * (maxR * maxR - minR * minR));
        double angle = rng.nextDouble() * 2 * Math.PI;
        int x = (int) (cx + dist * Math.cos(angle));
        int z = (int) (cz + dist * Math.sin(angle));

        x = (int) Math.max(border.getMinX() + BORDER_PADDING, Math.min(border.getMaxX() - BORDER_PADDING, x));
        z = (int) Math.max(border.getMinZ() + BORDER_PADDING, Math.min(border.getMaxZ() - BORDER_PADDING, z));

        return surfacePos(level, x, z);
    }

    // ── Session data model ──────────────────────────────────────

    public static final class SessionData {
        final String name;
        final String password;
        final UUID creator;
        int startX, startY, startZ;
        String startDim;
        final Set<UUID> alive;
        final Set<UUID> dead;
        int peakPlayers;
        int deaths;
        // Preserved hardcore state for players who stepped out; keyed per session so a
        // player can be suspended in several sessions at once. Rejoin restores from here.
        final Map<UUID, CompoundTag> suspended;

        SessionData(String name, String password, UUID creator,
                    int startX, int startY, int startZ, String startDim,
                    List<UUID> alive, List<UUID> dead,
                    int peakPlayers, int deaths, Map<String, CompoundTag> suspended) {
            this.name = name;
            this.password = password;
            this.creator = creator;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.startDim = startDim;
            this.alive = new HashSet<>(alive);
            this.dead = new HashSet<>(dead);
            this.peakPlayers = peakPlayers;
            this.deaths = deaths;
            this.suspended = new HashMap<>();
            suspended.forEach((k, v) -> this.suspended.put(UUID.fromString(k), v));
        }

        Map<String, CompoundTag> suspendedToStringMap() {
            Map<String, CompoundTag> map = new HashMap<>();
            suspended.forEach((k, v) -> map.put(k.toString(), v));
            return map;
        }

        public String getName()     { return name; }
        public int getStartX()      { return startX; }
        public int getStartY()      { return startY; }
        public int getStartZ()      { return startZ; }
        public String getStartDim() { return startDim; }
        public int getAliveCount()  { return alive.size(); }
        public int getDeadCount()   { return dead.size(); }
        public int getPeakPlayers() { return peakPlayers; }
        public int getDeaths()      { return deaths; }
        public int playerCount()    { return alive.size() + dead.size(); }

        public int getThreshold() {
            return Math.max(1,
                    (int) Math.ceil(peakPlayers * SmpConfig.HARDCORE_DEATH_PERCENT / 100.0));
        }
    }
}
