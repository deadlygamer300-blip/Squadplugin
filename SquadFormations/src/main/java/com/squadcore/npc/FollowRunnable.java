package com.squadcore.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs every 2 ticks. Recomputes each squad member's world-space target from
 * the commander's current position/yaw and lerps the NPC's displayed
 * position toward that target, so the formation glides into place instead
 * of snapping -- while still costing nothing more than a handful of double
 * multiplications and a packet send per member per tick, no pathfinding.
 */
public class FollowRunnable extends BukkitRunnable {

    private static final long PERIOD_TICKS = 2L;
    private static final double LERP_FACTOR = 0.35; // higher = snappier, lower = smoother/laggier-looking
    private static final double SNAP_DISTANCE = 0.05; // below this, just set exactly to avoid infinite creep

    private final SquadManager squadManager;

    public FollowRunnable(SquadManager squadManager) {
        this.squadManager = squadManager;
    }

    public void start(org.bukkit.plugin.Plugin plugin) {
        runTaskTimer(plugin, PERIOD_TICKS, PERIOD_TICKS);
    }

    @Override
    public void run() {
        if (!squadManager.isFollowEnabled()) {
            return;
        }
        Player commander = squadManager.getCommander();
        if (commander == null || !commander.isOnline() || squadManager.getSquadMembers().isEmpty()) {
            return;
        }

        List<Player> viewers = new ArrayList<>(Bukkit.getOnlinePlayers());
        Location commanderLoc = commander.getLocation();
        float commanderYaw = commanderLoc.getYaw();

        for (FakeNPC npc : squadManager.getSquadMembers().values()) {
            Vector worldOffset = FormationLogic.rotateToWorld(npc.getFormationOffset(), commanderYaw);
            Location desired = commanderLoc.clone().add(worldOffset);
            desired.setYaw(commanderYaw);
            desired.setPitch(0f);

            Location current = npc.getCurrentLocation();
            double dx = desired.getX() - current.getX();
            double dy = desired.getY() - current.getY();
            double dz = desired.getZ() - current.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            Location next;
            if (distSq < SNAP_DISTANCE * SNAP_DISTANCE) {
                next = desired;
            } else {
                next = current.clone().add(dx * LERP_FACTOR, dy * LERP_FACTOR, dz * LERP_FACTOR);
                next.setYaw(lerpYaw(current.getYaw(), commanderYaw, LERP_FACTOR));
                next.setPitch(0f);
            }

            npc.setTargetLocation(desired);
            squadManager.getPacketService().teleportAbsolute(npc, next, viewers);
        }
    }

    /** Shortest-path yaw interpolation so NPCs don't spin the long way around when the commander turns. */
    private static float lerpYaw(float from, float to, double factor) {
        float delta = ((to - from + 540f) % 360f) - 180f;
        return (float) (from + delta * factor);
    }
}
