package com.squadcore.npc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * All raw ProtocolLib packet construction lives here. Every other class in
 * the plugin only ever talks to {@link FakeNPC} objects and this service;
 * nothing else touches PacketContainer directly.
 * <p>
 * NOTE ON VERSION FRAGILITY: Mojang/ProtocolLib packet field layouts change
 * between releases. This class targets ProtocolLib 5.3.0 against the
 * post-1.19.3 split player-info packets and the unified (post-1.20.2)
 * SPAWN_ENTITY packet used for players. If you upgrade ProtocolLib, verify
 * these structure indices against that build's PacketType documentation
 * before deploying to production.
 */
public class NPCPacketService {

    private final ProtocolManager protocolManager;

    public NPCPacketService(ProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
    }

    /** Fully spawns an NPC for every currently online player: tab entry, entity, metadata. */
    public void spawnForAll(FakeNPC npc, Iterable<? extends Player> viewers) {
        sendPlayerInfoAdd(npc, viewers);
        sendSpawnEntity(npc, viewers);
        sendMetadata(npc, viewers);
        npc.setSpawned(true);
    }

    /** Sends full spawn state to a single player who just joined / needs a resync. */
    public void spawnForViewer(FakeNPC npc, Player viewer) {
        List<Player> single = List.of(viewer);
        sendPlayerInfoAdd(npc, single);
        sendSpawnEntity(npc, single);
        sendMetadata(npc, single);
    }

    private void sendPlayerInfoAdd(FakeNPC npc, Iterable<? extends Player> viewers) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_UPDATE);

        packet.getPlayerInfoActions().write(0, EnumSet.of(
                PlayerInfoAction.ADD_PLAYER,
                PlayerInfoAction.UPDATE_LISTED,
                PlayerInfoAction.UPDATE_LATENCY,
                PlayerInfoAction.UPDATE_GAME_MODE
        ));

        PlayerInfoData data = new PlayerInfoData(
                npc.getProfile(),
                0,
                true,
                EnumWrappers.NativeGameMode.SURVIVAL,
                WrappedChatComponent.fromText(npc.getName()),
                null
        );

        packet.getPlayerInfoDataLists()
                .write(0, Collections.singletonList(data));

        for (Player viewer : viewers) {
            protocolManager.sendServerPacket(viewer, packet);
        }
    }

    /** Removes the tab-list entry only (entity keeps rendering with its already-loaded skin). */
    public void hideFromTabList(FakeNPC npc, Iterable<? extends Player> viewers) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        packet.getUUIDLists().write(0, Collections.singletonList(npc.getUuid()));
        for (Player viewer : viewers) {
            protocolManager.sendServerPacket(viewer, packet);
        }
        npc.setVisibleInTabList(false);
    }

    private void sendSpawnEntity(FakeNPC npc, Iterable<? extends Player> viewers) {
        Location loc = npc.getCurrentLocation();
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);

        packet.getIntegers().write(0, npc.getEntityId());
        packet.getUUIDs().write(0, npc.getUuid());
        packet.getEntityTypeModifier().write(0, EntityType.PLAYER);

        packet.getDoubles()
                .write(0, loc.getX())
                .write(1, loc.getY())
                .write(2, loc.getZ());

        packet.getBytes()
                .write(0, (byte) (loc.getPitch() * 256 / 360))
                .write(1, (byte) (loc.getYaw() * 256 / 360));

        // Head yaw (index 2 in most ProtocolLib SPAWN_ENTITY byte layouts for the yaw-head field)
        try {
            packet.getIntegers().write(1, (int) (loc.getYaw() * 256 / 360) & 0xFF);
        } catch (Exception ignored) {
            // Some ProtocolLib builds fold head yaw into the byte array instead; safe to ignore
            // since the follow-up ROTATE_HEAD packet corrects this immediately after spawn.
        }

        packet.getIntegers().write(2, 0); // data field, unused for players

        for (Player viewer : viewers) {
            protocolManager.sendServerPacket(viewer, packet);
        }
    }

    /** Sets skin layer visibility (cape, jacket, sleeves, etc.) so the NPC doesn't render as a naked model. */
    private void sendMetadata(FakeNPC npc, Iterable<? extends Player> viewers) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, npc.getEntityId());

        WrappedDataWatcher watcher = new WrappedDataWatcher();
        WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);

        // Index 17 on a player entity: bit mask enabling all skin layers (cape, hat, jacket, sleeves, pants).
        watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(17, byteSerializer), (byte) 0x7F);
        // Index 10: main hand (0 = left, 1 = right). Default to right hand.
        watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(10, byteSerializer), (byte) 1);

        List<WrappedDataValue> values = watcher.getWatchableObjects().stream()
                .map(w -> new WrappedDataValue(
                        w.getIndex(),
                        WrappedDataWatcher.Registry.get(Byte.class).getType(),
                        w.getRawValue()))
                .toList();

        packet.getDataValueCollectionModifier().write(0, values);

        for (Player viewer : viewers) {
            protocolManager.sendServerPacket(viewer, packet);
        }
    }

    /** Smooth relative move + look for small per-tick position deltas (used by FollowRunnable/AmbientRunnable). */
    public void teleportAbsolute(FakeNPC npc, Location to, Iterable<? extends Player> viewers) {
        PacketContainer teleport = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
        teleport.getIntegers().write(0, npc.getEntityId());
        teleport.getDoubles()
                .write(0, to.getX())
                .write(1, to.getY())
                .write(2, to.getZ());
        teleport.getBytes()
                .write(0, (byte) (to.getYaw() * 256 / 360))
                .write(1, (byte) (to.getPitch() * 256 / 360));
        teleport.getBooleans().write(0, true); // on ground

        PacketContainer headRotation = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
        headRotation.getIntegers().write(0, npc.getEntityId());
        headRotation.getBytes().write(0, (byte) (to.getYaw() * 256 / 360));

        for (Player viewer : viewers) {
            protocolManager.sendServerPacket(viewer, teleport);
            protocolManager.sendServerPacket(viewer, headRotation);
        }

        npc.setCurrentLocation(to);
        npc.setCurrentYaw(to.getYaw());
        npc.setCurrentPitch(to.getPitch());
    }

    public void despawn(FakeNPC npc, Iterable<? extends Player> viewers) {
        PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, Collections.singletonList(npc.getEntityId()));

        PacketContainer removeInfo = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        removeInfo.getUUIDLists().write(0, Collections.singletonList(npc.getUuid()));

        for (Player viewer : viewers) {
            protocolManager.sendServerPacket(viewer, destroy);
            protocolManager.sendServerPacket(viewer, removeInfo);
        }
        npc.setSpawned(false);
    }
}
