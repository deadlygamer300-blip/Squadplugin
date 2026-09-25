package com.squadcore.npc;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns every FakeNPC in existence: their UUID map, the entity-id allocator,
 * formation state, and the commander reference. Nothing here touches real
 * Bukkit entities or scheduler ticks directly -- that is FollowRunnable's
 * and AmbientRunnable's job.
 */
public class SquadManager {

    private static final char[] ALPHANUMERIC =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Entity IDs are allocated from a very high starting point, walking
     * downward, so they will never collide with the server's real entity-id
     * counter (which starts at 0 and counts up) even after weeks of uptime.
     */
    private final AtomicInteger entityIdAllocator = new AtomicInteger(2_100_000_000);

    /** Optional pool of valid Mojang-signed skin textures. Populate via config or /squad skin add. */
    private final List<SkinEntry> skinPool = new ArrayList<>();

    private final Map<UUID, FakeNPC> squadMembers = new LinkedHashMap<>();
    private final Map<UUID, FakeNPC> ambientMembers = new ConcurrentHashMap<>();

    private final NPCPacketService packetService;

    private Player commander;
    private boolean followEnabled = false;
    private FormationLogic.Shape currentShape = FormationLogic.Shape.CIRCLE;
    private double currentParam = 4.0;

    public record SkinEntry(String value, String signature) {
    }

    public SquadManager(NPCPacketService packetService) {
        this.packetService = packetService;
    }

    // ---------------------------------------------------------------
    // Skin pool
    // ---------------------------------------------------------------

    public void addSkin(String value, String signature) {
        skinPool.add(new SkinEntry(value, signature));
    }

    private SkinEntry randomSkin() {
        if (skinPool.isEmpty()) {
            return null;
        }
        return skinPool.get(RANDOM.nextInt(skinPool.size()));
    }

    // ---------------------------------------------------------------
    // Spawning
    // ---------------------------------------------------------------

    public static String randomUsername() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHANUMERIC[RANDOM.nextInt(ALPHANUMERIC.length)]);
        }
        return sb.toString();
    }

    /** Spawns {@code count} squad NPCs clustered around the player, and sends them to every online viewer. */
    public List<FakeNPC> spawnSquad(Player commander, int count) {
        this.commander = commander;
        List<FakeNPC> spawned = new ArrayList<>(count);
        List<Player> viewers = onlineViewers();

        for (int i = 0; i < count; i++) {
            Location spawnLoc = commander.getLocation().clone().add(
                    (RANDOM.nextDouble() - 0.5) * 4.0,
                    0,
                    (RANDOM.nextDouble() - 0.5) * 4.0
            );

            FakeNPC npc = buildNpc(spawnLoc, FakeNPC.Type.SQUAD);
            squadMembers.put(npc.getUuid(), npc);
            spawned.add(npc);
            packetService.spawnForAll(npc, viewers);
        }

        // Immediately snap the freshly spawned squad into the currently selected formation.
        applyFormation(currentShape, currentParam);
        return spawned;
    }

    /** Spawns {@code count} ambient wandering NPCs anchored around {@code center}, within {@code radius}. */
    public List<FakeNPC> spawnAmbient(Location center, int count, double radius) {
        List<FakeNPC> spawned = new ArrayList<>(count);
        List<Player> viewers = onlineViewers();

        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double dist = RANDOM.nextDouble() * radius;
            Location spawnLoc = center.clone().add(dist * Math.cos(angle), 0, dist * Math.sin(angle));

            FakeNPC npc = buildNpc(spawnLoc, FakeNPC.Type.AMBIENT);
            npc.setAmbientAnchor(center.clone());
            npc.setAmbientRadius(radius);
            ambientMembers.put(npc.getUuid(), npc);
            spawned.add(npc);
            packetService.spawnForAll(npc, viewers);
        }
        return spawned;
    }

    private FakeNPC buildNpc(Location spawnLoc, FakeNPC.Type type) {
        String name = randomUsername();
        UUID uuid = UUID.nameUUIDFromBytes(("SquadFormationsNPC:" + name + ":" + System.nanoTime()).getBytes());

        WrappedGameProfile profile = new WrappedGameProfile(uuid, name);
        SkinEntry skin = randomSkin();
        if (skin != null) {
            profile.getProperties().put("textures",
                    new WrappedSignedProperty("textures", skin.value(), skin.signature()));
        }

        int entityId = entityIdAllocator.decrementAndGet();
        return new FakeNPC(entityId, uuid, name, profile, type, spawnLoc);
    }

    // ---------------------------------------------------------------
    // Formations
    // ---------------------------------------------------------------

    public void applyFormation(FormationLogic.Shape shape, double param) {
        this.currentShape = shape;
        this.currentParam = param;

        if (commander == null || squadMembers.isEmpty()) {
            return;
        }

        List<FakeNPC> members = new ArrayList<>(squadMembers.values());
        List<Vector> offsets = FormationLogic.computeOffsets(shape, members.size(), param);

        for (int i = 0; i < members.size(); i++) {
            members.get(i).setFormationOffset(offsets.get(i));
        }

        // Snap instantly once on assignment; FollowRunnable will keep them locked
        // in afterward if follow mode is on, or they simply hold position if it's off.
        snapAllToFormation();
    }

    /** Immediately teleports every squad member to its current formation offset, ignoring smoothing. */
    public void snapAllToFormation() {
        if (commander == null) {
            return;
        }
        List<Player> viewers = onlineViewers();
        Location commanderLoc = commander.getLocation();
        float yaw = commanderLoc.getYaw();

        for (FakeNPC npc : squadMembers.values()) {
            Vector world = FormationLogic.rotateToWorld(npc.getFormationOffset(), yaw);
            Location target = commanderLoc.clone().add(world);
            target.setYaw(yaw);
            target.setPitch(0);
            npc.setTargetLocation(target);
            packetService.teleportAbsolute(npc, target, viewers);
        }
    }

    // ---------------------------------------------------------------
    // Lifecycle / accessors
    // ---------------------------------------------------------------

    public void clearAll() {
        List<Player> viewers = onlineViewers();
        for (FakeNPC npc : squadMembers.values()) {
            packetService.despawn(npc, viewers);
        }
        for (FakeNPC npc : ambientMembers.values()) {
            packetService.despawn(npc, viewers);
        }
        squadMembers.clear();
        ambientMembers.clear();
        commander = null;
        followEnabled = false;
    }

    /** Resends full spawn packets for every NPC to a single player, e.g. on join. */
    public void resyncPlayer(Player viewer) {
        for (FakeNPC npc : squadMembers.values()) {
            packetService.spawnForViewer(npc, viewer);
        }
        for (FakeNPC npc : ambientMembers.values()) {
            packetService.spawnForViewer(npc, viewer);
        }
    }

    private List<Player> onlineViewers() {
        return Collections.unmodifiableList(new ArrayList<>(Bukkit.getOnlinePlayers()));
    }

    public Map<UUID, FakeNPC> getSquadMembers() {
        return squadMembers;
    }

    public Map<UUID, FakeNPC> getAmbientMembers() {
        return ambientMembers;
    }

    public NPCPacketService getPacketService() {
        return packetService;
    }

    public Player getCommander() {
        return commander;
    }

    public void setCommander(Player commander) {
        this.commander = commander;
    }

    public boolean isFollowEnabled() {
        return followEnabled;
    }

    public void setFollowEnabled(boolean followEnabled) {
        this.followEnabled = followEnabled;
    }

    public FormationLogic.Shape getCurrentShape() {
        return currentShape;
    }

    public double getCurrentParam() {
        return currentParam;
    }
}
