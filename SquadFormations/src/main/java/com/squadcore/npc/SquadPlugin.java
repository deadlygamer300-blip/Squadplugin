package com.squadcore.npc;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SquadPlugin extends JavaPlugin implements Listener {

    private SquadManager squadManager;
    private FollowRunnable followRunnable;
    private AmbientRunnable ambientRunnable;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib is required but not installed. Disabling SquadFormations.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        NPCPacketService packetService = new NPCPacketService(protocolManager);
        squadManager = new SquadManager(packetService);

        // Example skin pool entries. Replace with real signed texture values obtained
        // from a service such as mineskin.org before deploying to production; unsigned
        // or placeholder values here will simply render as the default Steve/Alex skin.
        // squadManager.addSkin("<base64 texture value>", "<base64 signature>");

        SquadCommand squadCommand = new SquadCommand(squadManager);
        getCommand("squad").setExecutor(squadCommand);
        getCommand("squad").setTabCompleter(squadCommand);

        getServer().getPluginManager().registerEvents(this, this);

        followRunnable = new FollowRunnable(squadManager);
        followRunnable.start(this);

        ambientRunnable = new AmbientRunnable(squadManager);
        ambientRunnable.start(this);

        getLogger().info("SquadFormations enabled. Packet-based fake players active via ProtocolLib.");
    }

    @Override
    public void onDisable() {
        if (squadManager != null) {
            squadManager.clearAll();
        }
        if (followRunnable != null) {
            followRunnable.cancel();
        }
        if (ambientRunnable != null) {
            ambientRunnable.cancel();
        }
        getLogger().info("SquadFormations disabled, all NPCs despawned.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // New viewers need every currently-spawned NPC replayed to them individually,
        // since spawn packets are only ever broadcast to players online at spawn time.
        squadManager.resyncPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // No cleanup needed: fake entity IDs are never tied to a specific viewer's
        // client state beyond the packets already sent, and they are not real
        // entities, so there's nothing to unregister when a real player leaves.
    }

    public SquadManager getSquadManager() {
        return squadManager;
    }
}
