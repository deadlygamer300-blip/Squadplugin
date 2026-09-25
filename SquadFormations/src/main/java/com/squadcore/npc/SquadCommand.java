package com.squadcore.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SquadCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("spawn", "formation", "follow", "ambient", "clear");
    private static final List<String> SHAPES = List.of("circle", "square", "grid", "triangle");
    private static final List<String> BOOLEANS = List.of("true", "false");
    private static final int MAX_SPAWN_PER_COMMAND = 2000;

    private final SquadManager squadManager;

    public SquadCommand(SquadManager squadManager) {
        this.squadManager = squadManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("squad.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "spawn" -> handleSpawn(sender, args);
            case "formation" -> handleFormation(sender, args);
            case "follow" -> handleFollow(sender, args);
            case "ambient" -> handleAmbient(sender, args);
            case "clear" -> handleClear(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /squad spawn.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /squad spawn <count>", NamedTextColor.RED));
            return;
        }
        int count = parseInt(args[1], -1);
        if (count <= 0 || count > MAX_SPAWN_PER_COMMAND) {
            sender.sendMessage(Component.text(
                    "Count must be between 1 and " + MAX_SPAWN_PER_COMMAND + ".", NamedTextColor.RED));
            return;
        }

        squadManager.spawnSquad(player, count);
        sender.sendMessage(Component.text(
                "Spawned " + count + " squad NPCs around you.", NamedTextColor.GREEN));
    }

    private void handleFormation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /squad formation.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /squad formation <circle|square|grid|triangle> [radius/spacing]", NamedTextColor.RED));
            return;
        }
        FormationLogic.Shape shape = FormationLogic.Shape.fromString(args[1]);
        if (shape == null) {
            sender.sendMessage(Component.text(
                    "Unknown shape. Choose one of: circle, square, grid, triangle.", NamedTextColor.RED));
            return;
        }
        double param = args.length >= 3 ? parseDouble(args[2], 4.0) : 4.0;
        if (param <= 0) {
            param = 4.0;
        }

        if (squadManager.getCommander() == null) {
            squadManager.setCommander(player);
        }
        squadManager.applyFormation(shape, param);
        sender.sendMessage(Component.text(
                "Formation set to " + shape.name().toLowerCase() + " (param=" + param + ").", NamedTextColor.GREEN));
    }

    private void handleFollow(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /squad follow.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2 || (!args[1].equalsIgnoreCase("true") && !args[1].equalsIgnoreCase("false"))) {
            sender.sendMessage(Component.text("Usage: /squad follow <true|false>", NamedTextColor.RED));
            return;
        }
        boolean enabled = Boolean.parseBoolean(args[1]);
        if (enabled && squadManager.getCommander() == null) {
            squadManager.setCommander(player);
        }
        squadManager.setFollowEnabled(enabled);
        sender.sendMessage(Component.text(
                "Formation follow mode " + (enabled ? "enabled." : "disabled."), NamedTextColor.GREEN));
    }

    private void handleAmbient(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /squad ambient.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("spawn")) {
            sender.sendMessage(Component.text("Usage: /squad ambient spawn <count> <radius>", NamedTextColor.RED));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /squad ambient spawn <count> <radius>", NamedTextColor.RED));
            return;
        }
        int count = parseInt(args[2], -1);
        double radius = parseDouble(args[3], -1);
        if (count <= 0 || count > MAX_SPAWN_PER_COMMAND) {
            sender.sendMessage(Component.text(
                    "Count must be between 1 and " + MAX_SPAWN_PER_COMMAND + ".", NamedTextColor.RED));
            return;
        }
        if (radius <= 0) {
            sender.sendMessage(Component.text("Radius must be a positive number.", NamedTextColor.RED));
            return;
        }

        squadManager.spawnAmbient(player.getLocation(), count, radius);
        sender.sendMessage(Component.text(
                "Spawned " + count + " ambient NPCs wandering within " + radius + " blocks.", NamedTextColor.GREEN));
    }

    private void handleClear(CommandSender sender) {
        squadManager.clearAll();
        sender.sendMessage(Component.text("All squad and ambient NPCs cleared.", NamedTextColor.GREEN));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("--- SquadFormations ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/squad spawn <count>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/squad formation <circle|square|grid|triangle> [radius/spacing]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/squad follow <true|false>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/squad ambient spawn <count> <radius>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/squad clear", NamedTextColor.YELLOW));
    }

    private int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double parseDouble(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("squad.admin")) {
            return List.of();
        }

        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "formation" -> options.addAll(SHAPES);
                case "follow" -> options.addAll(BOOLEANS);
                case "ambient" -> options.add("spawn");
                case "spawn" -> options.addAll(List.of("10", "50", "100"));
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("formation")) {
            options.addAll(List.of("4", "6", "8"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ambient") && args[1].equalsIgnoreCase("spawn")) {
            options.addAll(List.of("10", "20", "50"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("ambient") && args[1].equalsIgnoreCase("spawn")) {
            options.addAll(List.of("8", "16", "32"));
        }

        String current = args[args.length - 1].toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(current))
                .collect(Collectors.toList());
    }
}
