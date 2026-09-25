package com.squadcore.npc;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Pure data holder for a packet-level fake player NPC.
 * <p>
 * A FakeNPC is NEVER a real Bukkit/NMS entity. It has no AI goal selector,
 * no pathfinder, no entity-tracker overhead, and cannot appear in
 * getNearbyEntities() calls. All state here is purely server-side bookkeeping
 * used to decide what packets to send to real players.
 */
public class FakeNPC {

    public enum Type {
        SQUAD,
        AMBIENT
    }

    private final int entityId;
    private final UUID uuid;
    private final String name;
    private final WrappedGameProfile profile;
    private final Type type;

    /** Current interpolated position actually shown to clients. */
    private Location currentLocation;

    /** Where the NPC should be heading (formation slot or wander goal). */
    private Location targetLocation;

    /** Formation slot offset relative to the commander, in commander-local space (X = right, Z = forward). */
    private Vector formationOffset = new Vector(0, 0, 0);

    /** Anchor point ambient NPCs wander around. */
    private Location ambientAnchor;
    private double ambientRadius = 8.0;
    private long nextWanderDecisionTick = 0L;
    private boolean wandering = false;

    private float currentYaw;
    private float currentPitch;

    private boolean visibleInTabList = true;
    private boolean spawned = false;

    public FakeNPC(int entityId, UUID uuid, String name, WrappedGameProfile profile, Type type, Location spawnLocation) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.name = name;
        this.profile = profile;
        this.type = type;
        this.currentLocation = spawnLocation.clone();
        this.targetLocation = spawnLocation.clone();
        this.currentYaw = spawnLocation.getYaw();
        this.currentPitch = spawnLocation.getPitch();
    }

    public int getEntityId() {
        return entityId;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public WrappedGameProfile getProfile() {
        return profile;
    }

    public Type getType() {
        return type;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation;
    }

    public Vector getFormationOffset() {
        return formationOffset;
    }

    public void setFormationOffset(Vector formationOffset) {
        this.formationOffset = formationOffset;
    }

    public Location getAmbientAnchor() {
        return ambientAnchor;
    }

    public void setAmbientAnchor(Location ambientAnchor) {
        this.ambientAnchor = ambientAnchor;
    }

    public double getAmbientRadius() {
        return ambientRadius;
    }

    public void setAmbientRadius(double ambientRadius) {
        this.ambientRadius = ambientRadius;
    }

    public long getNextWanderDecisionTick() {
        return nextWanderDecisionTick;
    }

    public void setNextWanderDecisionTick(long nextWanderDecisionTick) {
        this.nextWanderDecisionTick = nextWanderDecisionTick;
    }

    public boolean isWandering() {
        return wandering;
    }

    public void setWandering(boolean wandering) {
        this.wandering = wandering;
    }

    public float getCurrentYaw() {
        return currentYaw;
    }

    public void setCurrentYaw(float currentYaw) {
        this.currentYaw = currentYaw;
    }

    public float getCurrentPitch() {
        return currentPitch;
    }

    public void setCurrentPitch(float currentPitch) {
        this.currentPitch = currentPitch;
    }

    public boolean isVisibleInTabList() {
        return visibleInTabList;
    }

    public void setVisibleInTabList(boolean visibleInTabList) {
        this.visibleInTabList = visibleInTabList;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void setSpawned(boolean spawned) {
        this.spawned = spawned;
    }
}
