package com.squadcore.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every 4 ticks. Each ambient NPC periodically (every 3-8 seconds)
 * picks a new random point within its wander radius and then takes small
 * step-wise moves toward it each tick until it arrives, at which point it
 * idles for a random pause before picking a new goal. No pathfinding, no
 * collision checks -- this is a population filler, not a navigating mob.
 */
public class AmbientRunnable extends BukkitRunnable {

    private static final long PERIOD_TICKS = 4L;
    private static final double STEP_DISTANCE = 0.12; // blocks moved per tick while wandering
    private static final double ARRIVAL_THRESHOLD = 0.3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SquadManager squadManager;

    public AmbientRunnable(SquadManager squadManager) {
        this.squadManager = squadManager;
    }

    public void start(org.bukkit.plugin.Plugin plugin) {
        runTaskTimer(plugin, PERIOD_TICKS, PERIOD_TICKS);
    }

    @Override
    public void run() {
        if (squadManager.getAmbientMembers().isEmpty()) {
            return;
        }

        List<Player> viewers = new ArrayList<>(Bukkit.getOnlinePlayers());
        long now = Bukkit.getCurrentTick();

        for (FakeNPC npc : squadManager.getAmbientMembers().values()) {
            Location anchor = npc.getAmbientAnchor();
            if (anchor == null) {
                continue;
            }

            if (!npc.isWandering()) {
                if (now >= npc.getNextWanderDecisionTick()) {
                    pickNewWanderTarget(npc, anchor);
                }
                continue;
            }

            Location current = npc.getCurrentLocation();
            Location target = npc.getTargetLocation();

            double dx = target.getX() - current.getX();
            double dz = target.getZ() - current.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist <= ARRIVAL_THRESHOLD) {
                npc.setWandering(false);
                // idle for 3-8 seconds (60-160 ticks) before picking a new goal
                npc.setNextWanderDecisionTick(now + 60 + RANDOM.nextInt(100));
                continue;
            }

            double stepX = (dx / dist) * STEP_DISTANCE;
            double stepZ = (dz / dist) * STEP_DISTANCE;
            float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

            Location next = current.clone().add(stepX, 0, stepZ);
            next.setYaw(lerpYaw(current.getYaw(), desiredYaw, 0.2));
            next.setPitch(0f);

            squadManager.getPacketService().teleportAbsolute(npc, next, viewers);
        }
    }

    private void pickNewWanderTarget(FakeNPC npc, Location anchor) {
        double angle = RANDOM.nextDouble() * 2 * Math.PI;
        double dist = RANDOM.nextDouble() * npc.getAmbientRadius();
        Location goal = anchor.clone().add(dist * Math.cos(angle), 0, dist * Math.sin(angle));
        npc.setTargetLocation(goal);
        npc.setWandering(true);
    }

    private static float lerpYaw(float from, float to, double factor) {
        float delta = ((to - from + 540f) % 360f) - 180f;
        return (float) (from + delta * factor);
    }
}
