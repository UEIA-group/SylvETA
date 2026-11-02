package com.sylveta;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.bukkit.Location;
import java.util.Locale;
import java.util.Map;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import java.util.Set;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.ItemFrame;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

public final class SylvETAPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private final Map<String, EtaBoard> boards = new HashMap<>();
    private final Set<String> lines = new HashSet<>();
    private final Map<String, String> lineLogoFiles = new HashMap<>();
    private final Set<String> disabledInfoTextLines = new HashSet<>();
    private final Map<String, String> infoTexts = new HashMap<>();

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        getLineFolder().mkdirs();
        loadLines();
        loadInfoTexts();
        loadBoards();
        getCommand("eta").setExecutor(this);
        getCommand("eta").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tickBoards, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickInfoText, 60L, 60L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickDestinationPages, 200L, 200L);
    }

    @Override
    public void onDisable() {
        saveBoards();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /eta <create|set|arrived|list|delete|line|get|infotext>");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!(sender instanceof BlockCommandSender) && !action.equals("get")
                && !sender.hasPermission("sylveta.admin")) {
            sender.sendMessage("You do not have permission to manage ETA boards.");
            return true;
        }

        switch (action) {
            case "create" -> createBoard(sender, args);
            case "set" -> setBoard(sender, args);
            case "arrived" -> markBoardArrived(sender, args);
            case "list" -> listBoards(sender);
            case "delete" -> deleteBoard(sender, args);
            case "line" -> addLine(sender, args);
            case "get" -> getBoard(sender, args);
            case "infotext" -> setInfoText(sender, args);
            default -> sender.sendMessage("Usage: /eta <create|set|arrived|list|delete|line|get|infotext>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "set", "arrived", "list", "delete", "line", "get", "infotext").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("arrived")
            || args[0].equalsIgnoreCase("delete")
            || args[0].equalsIgnoreCase("get"))) {
            return boards.keySet().stream().filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("arrived"))) {
            EtaBoard board = boards.get(normalizeId(args[1]));
            if (board != null) {
                List<String> destinations = new ArrayList<>();
                for (int index = 1; index <= board.destinations.size(); index++) {
                    destinations.add(Integer.toString(index));
                }
                return destinations.stream().filter(index -> index.startsWith(args[2])).toList();
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("line")) {
            return List.of("create").stream().filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("infotext")) {
            List<String> options = new ArrayList<>(lines);
            options.add("global");
            options.add("disable");
            return options.stream().filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("infotext")
                && args[1].equalsIgnoreCase("disable")) {
            List<String> options = new ArrayList<>(lines);
            options.add("global");
            return options.stream().filter(option -> option.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("line") && args[1].equalsIgnoreCase("create")) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return listLinePngFiles().stream()
                .filter(fileName -> fileName.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("line") && args[1].equalsIgnoreCase("create")) {
            return lines.stream().filter(name -> name.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length >= 5 && args[0].equalsIgnoreCase("create")) {
            if (args.length >= 7 && args[args.length - 2].equalsIgnoreCase("--player")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT)))
                        .toList();
            }
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            return lines.stream().filter(name -> name.startsWith(prefix)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("get")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    private void createBoard(CommandSender sender, String[] args) {
        int boardArgsEnd = args.length;
        Player recipient;
        if (args.length >= 7 && args[args.length - 2].equalsIgnoreCase("--player")) {
            recipient = resolvePlayer(sender, args[args.length - 1]);
            boardArgsEnd -= 2;
            if (recipient == null) {
                sender.sendMessage("Could not find the target player.");
                return;
            }
        } else if (sender instanceof Player player) {
            recipient = player;
        } else {
            sender.sendMessage("Usage: /eta create <id> <minutes> <destination...> <line> --player <player>");
            return;
        }
        if (boardArgsEnd < 5) {
            sender.sendMessage("Usage: /eta create <id> <minutes> <destination...> <line> [--player <player>]");
            return;
        }

        String id = normalizeId(args[1]);
        int minutes = parseMinutes(sender, args[2]);
        if (id == null) {
            sender.sendMessage("Board ids may contain only letters, numbers, underscores, and hyphens.");
            return;
        }
        if (minutes < 0) {
            return;
        }
        String line = normalizeId(args[boardArgsEnd - 1]);
        if (line == null || !lines.contains(line)) {
            sender.sendMessage("That line is not registered. Create it with /eta line create <id> <file.png>.");
            return;
        }
        if (boards.containsKey(id)) {
            sender.sendMessage("A board with that id already exists.");
            return;
        }

        List<String> destinationNames = parseDestinations(joinDestination(args, 3, boardArgsEnd - 1));
        if (destinationNames.isEmpty()) {
            sender.sendMessage("Provide at least one destination. Separate multiple destinations with '|'.");
            return;
        }
        BufferedImage logo = loadLineLogo(line);
        if (logo == null) {
            sender.sendMessage("Missing or invalid PNG: " + new File(getLineFolder(), line + ".png").getPath());
            return;
        }
        MapView leftMap = Bukkit.createMap(recipient.getWorld());
        MapView rightMap = Bukkit.createMap(recipient.getWorld());
        configureMap(leftMap);
        configureMap(rightMap);
        List<DestinationEta> destinations = destinationNames.stream()
            .map(name -> new DestinationEta(name, minutes, false))
            .toList();
        EtaBoard board = new EtaBoard(id, destinations, line,
            leftMap.getId(), rightMap.getId(), logo);
        board.tickerText = getInfoText(line);
        attachRenderers(board, leftMap, rightMap);
        boards.put(id, board);
        saveBoards();
        Map<Integer, ItemStack> leftovers = recipient.getInventory().addItem(
            createMapItem(board, leftMap, "left"), createMapItem(board, rightMap, "right"));
        leftovers.values().forEach(item -> recipient.getWorld().dropItemNaturally(recipient.getLocation(), item));
        sender.sendMessage("Created two-map ETA board for " + String.join(" | ", destinationNames) + " on line " + line
                + " for " + recipient.getName() + ". Hold the left map and right-click the leftmost block of a vertical wall.");
    }

    private void setBoard(CommandSender sender, String[] args) {
        if (args.length == 4 && isInteger(args[2]) && isInteger(args[3])) {
            EtaBoard board = boards.get(normalizeId(args[1]));
            if (board == null) {
                sender.sendMessage("No board exists with that id.");
                return;
            }
            int destinationIndex = Integer.parseInt(args[2]) - 1;
            int minutes = parseMinutes(sender, args[3]);
            if (destinationIndex < 0 || destinationIndex >= board.destinations.size()) {
                sender.sendMessage("Destination number must be between 1 and " + board.destinations.size() + ".");
                return;
            }
            if (minutes < 0) {
                return;
            }
            DestinationEta destination = board.destinations.get(destinationIndex);
            destination.minutes = minutes;
            destination.arrived = false;
            board.invalidate();
            saveBoards();
            sender.sendMessage("Updated " + destination.name + ": " + formatEta(destination));
            return;
        }
        if (args.length >= 4) {
            EtaBoard board = boards.get(normalizeId(args[1]));
            if (board != null) {
                int minutes = parseMinutes(sender, args[2]);
                if (minutes < 0) {
                    return;
                }
                List<String> destinationNames = parseDestinations(joinDestination(args, 3));
                if (destinationNames.isEmpty()) {
                    sender.sendMessage("Provide at least one destination. Separate multiple destinations with '|'.");
                    return;
                }
                board.destinations = destinationNames.stream()
                        .map(name -> new DestinationEta(name, minutes, false))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                board.destinationPage = 0;
                board.invalidate();
                saveBoards();
                sender.sendMessage("Updated " + board.id + ": " + String.join(" | ", destinationNames)
                        + " | " + formatMinutes(minutes));
                return;
            }
        }
        if (args.length >= 3 && isInteger(args[args.length - 1])) {
            int minutes = parseMinutes(sender, args[args.length - 1]);
            if (minutes < 0) {
                return;
            }
            String destinationName = joinDestination(args, 1, args.length - 1);
            EtaBoard matchedBoard = null;
            DestinationEta matchedDestination = null;
            int matchCount = 0;
            for (EtaBoard board : boards.values()) {
                for (DestinationEta destination : board.destinations) {
                    if (destination.name.equalsIgnoreCase(destinationName)) {
                        matchedBoard = board;
                        matchedDestination = destination;
                        matchCount++;
                    }
                }
            }
            if (matchCount == 0) {
                sender.sendMessage("No destination found named '" + destinationName + "'.");
                return;
            }
            if (matchCount > 1) {
                sender.sendMessage("That destination is on multiple boards. Use /eta set <id> <destination-number> <minutes>.");
                return;
            }
            matchedDestination.minutes = minutes;
            matchedDestination.arrived = false;
            matchedBoard.invalidate();
            saveBoards();
            sender.sendMessage("Updated " + destinationName + ": " + formatEta(matchedDestination));
            return;
        }
        sender.sendMessage("Usage: /eta set <destination...> <minutes>, /eta set <id> <minutes> <destinations...>, or /eta set <id> <destination-number> <minutes>");
    }

    private void markBoardArrived(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /eta arrived <id> <destination>");
            return;
        }
        String id = normalizeId(args[1]);
        EtaBoard board = id == null ? null : boards.get(id);
        if (board == null) {
            sender.sendMessage("No board exists with that id.");
            return;
        }
        String destinationTarget = joinDestination(args, 2);
        if (isInteger(destinationTarget)) {
            int destinationIndex = Integer.parseInt(destinationTarget) - 1;
            if (destinationIndex < 0 || destinationIndex >= board.destinations.size()) {
                sender.sendMessage("Destination number must be between 1 and " + board.destinations.size() + ".");
                return;
            }
            markArrived(board.destinations.get(destinationIndex));
        } else {
            DestinationEta destination = board.destinations.stream()
                    .filter(entry -> entry.name.equalsIgnoreCase(destinationTarget))
                    .findFirst().orElse(null);
            if (destination == null && board.destinations.size() == 1) {
                destination = board.destinations.get(0);
                destination.name = destinationTarget;
            }
            if (destination == null) {
                sender.sendMessage("Destination not found. Use its name or 1-based destination number.");
                return;
            }
            markArrived(destination);
        }
        board.invalidate();
        saveBoards();
        sender.sendMessage("Updated " + board.id + ": " + formatDestinations(board));
    }

    private void getBoard(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage("Usage: /eta get <id> [player]");
            return;
        }
        Player recipient = args.length == 3 ? resolvePlayer(sender, args[2])
                : sender instanceof Player player ? player : null;
        if (recipient == null) {
            sender.sendMessage("Usage: /eta get <id> <player> when run by a command block or console.");
            return;
        }
        String id = normalizeId(args[1]);
        EtaBoard board = id == null ? null : boards.get(id);
        if (board == null) {
            sender.sendMessage("No board exists with that id.");
            return;
        }
        MapView leftMap = Bukkit.getMap(board.leftMapId);
        MapView rightMap = Bukkit.getMap(board.rightMapId);
        if (leftMap == null || rightMap == null) {
            sender.sendMessage("The maps for that board are unavailable. Ask an admin to recreate the board.");
            return;
        }
        Map<Integer, ItemStack> leftovers = recipient.getInventory().addItem(
                createMapItem(board, leftMap, "left"), createMapItem(board, rightMap, "right"));
        leftovers.values().forEach(item -> recipient.getWorld().dropItemNaturally(recipient.getLocation(), item));
        sender.sendMessage("Gave both live maps for " + formatDestinations(board) + " to " + recipient.getName() + ".");
    }

    private Player resolvePlayer(CommandSender sender, String target) {
        if (target.startsWith("@")) {
            try {
                return Bukkit.selectEntities(sender, target).stream()
                        .filter(Player.class::isInstance)
                        .map(Player.class::cast)
                        .findFirst()
                        .orElse(null);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        return Bukkit.getPlayerExact(target);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBoardMapRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Integer heldMapId = getFilledMapId(event.getPlayer().getInventory().getItemInMainHand());
        if (heldMapId == null) {
            return;
        }
        EtaBoard board = boards.values().stream()
                .filter(candidate -> candidate.leftMapId == heldMapId)
                .findFirst()
                .orElse(null);
        if (board == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        Block support = event.getClickedBlock();
        BlockFace facing = event.getBlockFace();
        BlockFace right = getBoardRight(facing);
        if (right == null) {
            player.sendMessage("Place ETA boards on a vertical wall.");
            return;
        }

        MapView leftMap = Bukkit.getMap(board.leftMapId);
        MapView rightMap = Bukkit.getMap(board.rightMapId);
        if (leftMap == null || rightMap == null || !hasMap(player, board.rightMapId)) {
            player.sendMessage("Keep both board maps in your inventory, with the left map in your main hand.");
            return;
        }

        Block rightSupport = support.getRelative(right);
        Block leftCell = support.getRelative(facing);
        Block rightCell = leftCell.getRelative(right);
        if (!support.getType().isSolid() || !rightSupport.getType().isSolid()
                || !leftCell.getType().isAir() || !rightCell.getType().isAir()) {
            player.sendMessage("There must be two adjacent clear spaces on a solid wall for the board.");
            return;
        }

        ItemFrame leftFrame = spawnBoardFrame(player, board, support, facing, leftCell, leftMap, "left");
        if (leftFrame == null) {
            player.sendMessage("You cannot place the ETA board here.");
            return;
        }
        ItemFrame rightFrame = spawnBoardFrame(player, board, rightSupport, facing, rightCell, rightMap, "right");
        if (rightFrame == null) {
            leftFrame.remove();
            player.sendMessage("You cannot place the ETA board here.");
            return;
        }

        removeMap(player, board.leftMapId);
        removeMap(player, board.rightMapId);
        player.sendMessage("Placed ETA board " + board.id + ".");
    }

    private ItemFrame spawnBoardFrame(Player player, EtaBoard board, Block support, BlockFace facing,
                                      Block target, MapView mapView, String side) {
        Location location = target.getLocation().add(0.5, 0.5, 0.5);
        ItemFrame frame = target.getWorld().spawn(location, ItemFrame.class);
        if (!frame.setFacingDirection(facing, true)) {
            frame.remove();
            return null;
        }
        frame.setItem(createMapItem(board, mapView, side));
        HangingPlaceEvent placeEvent = new HangingPlaceEvent(frame, player, support, facing,
            EquipmentSlot.HAND, new ItemStack(Material.ITEM_FRAME));
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            frame.remove();
            return null;
        }
        return frame;
    }

    private static BlockFace getBoardRight(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            case WEST -> BlockFace.SOUTH;
            default -> null;
        };
    }

    private static Integer getFilledMapId(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta)) {
            return null;
        }
        MapView mapView = meta.getMapView();
        return mapView == null ? null : mapView.getId();
    }

    private static boolean hasMap(Player player, int mapId) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (Integer.valueOf(mapId).equals(getFilledMapId(item))) {
                return true;
            }
        }
        return false;
    }

    private static void removeMap(Player player, int mapId) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!Integer.valueOf(mapId).equals(getFilledMapId(item))) {
                continue;
            }
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                contents[slot] = null;
            }
            player.getInventory().setStorageContents(contents);
            return;
        }
    }

    private void listBoards(CommandSender sender) {
        if (boards.isEmpty()) {
            sender.sendMessage("There are no ETA boards.");
            return;
        }
        for (EtaBoard board : boards.values()) {
            sender.sendMessage(board.id + ": " + formatDestinations(board)
                + " | line " + board.line);
        }
    }

    private void deleteBoard(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /eta delete <id>");
            return;
        }
        String id = normalizeId(args[1]);
        if (id == null || boards.remove(id) == null) {
            sender.sendMessage("No board exists with that id.");
            return;
        }
        saveBoards();
        sender.sendMessage("Deleted ETA board " + id + ". Existing maps will no longer update.");
    }

    private void addLine(CommandSender sender, String[] args) {
        if (args.length != 4 || !args[1].equalsIgnoreCase("create")) {
            sender.sendMessage("Usage: /eta line create <id> <file.png>");
            return;
        }
        String line = normalizeId(args[2]);
        if (line == null) {
            sender.sendMessage("Line names may contain only letters, numbers, underscores, and hyphens.");
            return;
        }
        if (lines.contains(line)) {
            sender.sendMessage("Line '" + line + "' already exists.");
            return;
        }
        String fileName = validateLogoFileName(args[3]);
        if (fileName == null) {
            sender.sendMessage("Logo files must be PNG filenames inside the 'line numbers' folder.");
            return;
        }
        BufferedImage logo = readLineLogo(new File(getLineFolder(), fileName));
        if (logo == null) {
            sender.sendMessage("Could not load " + fileName + ". Put a valid PNG in "
                    + getLineFolder().getPath() + " and try again.");
            return;
        }
        lines.add(line);
        lineLogoFiles.put(line, fileName);
        saveBoards();
        sender.sendMessage("Created line '" + line + "' using " + fileName + ".");
    }

    private void setInfoText(CommandSender sender, String[] args) {
        if (args.length == 3 && args[1].equalsIgnoreCase("disable")) {
            String target = args[2].equalsIgnoreCase("global") ? "global" : normalizeId(args[2]);
            if (target == null || (!target.equals("global") && !lines.contains(target))) {
                sender.sendMessage("Register that line with /eta line create <id> <file.png>, or use 'global'.");
                return;
            }
            if (target.equals("global")) {
                if (infoTexts.isEmpty()) {
                    sender.sendMessage("No infotext is configured.");
                    return;
                }
                infoTexts.clear();
                disabledInfoTextLines.clear();
            } else {
                if (!infoTexts.containsKey(target) && !infoTexts.containsKey("global")) {
                    sender.sendMessage("No infotext is configured for " + target + ".");
                    return;
                }
                infoTexts.remove(target);
                disabledInfoTextLines.add(target);
            }
            refreshInfoText(target);
            saveBoards();
            sender.sendMessage(target.equals("global")
                    ? "Cleared all infotext and disabled every ticker."
                    : "Cleared infotext and disabled the ticker for " + target + ".");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /eta infotext <line|global> <text> or /eta infotext disable <line|global>");
            return;
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        if (!target.equals("global")) {
            target = normalizeId(target);
            if (target == null || !lines.contains(target)) {
                sender.sendMessage("Register that line with /eta line create <id> <file.png>, or use 'global'.");
                return;
            }
        }
        infoTexts.put(target, joinDestination(args, 2));
        if (!target.equals("global")) {
            disabledInfoTextLines.remove(target);
        }
        refreshInfoText(target);
        saveBoards();
        sender.sendMessage("Updated " + target + " infotext.");
    }

    private void refreshInfoText(String target) {
        for (EtaBoard board : boards.values()) {
            if (target.equals("global") || board.line.equals(target)) {
                board.tickerText = getInfoText(board.line);
                board.scrollOffset = 0;
                board.invalidate();
            }
        }
    }

    private int parseMinutes(CommandSender sender, String value) {
        try {
            int minutes = Integer.parseInt(value);
            if (minutes < 0) {
                sender.sendMessage("Minutes must be zero or greater.");
                return -1;
            }
            return minutes;
        } catch (NumberFormatException exception) {
            sender.sendMessage("Minutes must be a whole number.");
            return -1;
        }
    }

    private void tickBoards() {
        boolean changed = false;
        for (EtaBoard board : boards.values()) {
            for (DestinationEta destination : board.destinations) {
                if (!destination.arrived && destination.minutes > 0) {
                    destination.minutes--;
                    board.invalidate();
                    changed = true;
                }
            }
        }
        if (changed) {
            saveBoards();
        }
    }

    private void tickDestinationPages() {
        for (EtaBoard board : boards.values()) {
            if (board.destinations.size() > 2) {
                int pageCount = (board.destinations.size() + 1) / 2;
                board.destinationPage = (board.destinationPage + 1) % pageCount;
                board.invalidate();
            }
        }
    }

    private void loadLines() {
        for (String line : getConfig().getStringList("lines")) {
            String normalized = normalizeId(line);
            if (normalized != null) {
                lines.add(normalized);
                lineLogoFiles.putIfAbsent(normalized, normalized + ".png");
            }
        }
        var logoSection = getConfig().getConfigurationSection("line-logo-files");
        if (logoSection != null) {
            for (String key : logoSection.getKeys(false)) {
                String line = normalizeId(key);
                String fileName = validateLogoFileName(logoSection.getString(key));
                if (line != null && fileName != null) {
                    lines.add(line);
                    lineLogoFiles.put(line, fileName);
                }
            }
        }
    }

    private void loadInfoTexts() {
        for (String line : getConfig().getStringList("infotext-disabled-lines")) {
            String normalized = normalizeId(line);
            if (normalized != null) {
                disabledInfoTextLines.add(normalized);
            }
        }
        var section = getConfig().getConfigurationSection("infotext");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String target = key.equalsIgnoreCase("global") ? "global" : normalizeId(key);
            String text = getConfig().getString("infotext." + key, "");
            if (target != null && !text.isBlank()) {
                infoTexts.put(target, text);
            }
        }
    }

    private String getInfoText(String line) {
        if (disabledInfoTextLines.contains(line)) {
            return "";
        }
        String global = infoTexts.getOrDefault("global", "");
        String local = infoTexts.getOrDefault(line, "");
        if (local.isBlank()) {
            return global;
        }
        if (global.isBlank()) {
            return local;
        }
        return local + "   |   " + global;
    }

    private void tickInfoText() {
        for (EtaBoard board : boards.values()) {
            if (!board.tickerText.isBlank()) {
                board.scrollOffset += 8;
                board.invalidate();
            }
        }
    }

    private void loadBoards() {
        var section = getConfig().getConfigurationSection("boards");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            String path = "boards." + id + ".";
            List<DestinationEta> destinations = new ArrayList<>();
            var destinationSection = getConfig().getConfigurationSection(path + "destinations");
            if (destinationSection != null) {
                for (String index : destinationSection.getKeys(false)) {
                    String destinationPath = path + "destinations." + index + ".";
                    String name = getConfig().getString(destinationPath + "name", "");
                    if (!name.isBlank()) {
                        destinations.add(new DestinationEta(name,
                                Math.max(0, getConfig().getInt(destinationPath + "minutes")),
                                getConfig().getBoolean(destinationPath + "arrived", false)));
                    }
                }
            }
            if (destinations.isEmpty()) {
                List<String> destinationNames = getConfig().getStringList(path + "destinations");
                if (destinationNames.isEmpty()) {
                    destinationNames = parseDestinations(getConfig().getString(path + "destination", "Unknown"));
                }
                int minutes = Math.max(0, getConfig().getInt(path + "minutes"));
                boolean arrived = getConfig().getBoolean(path + "arrived", false);
                for (String name : destinationNames) {
                    destinations.add(new DestinationEta(name, minutes, arrived));
                }
            }
            if (destinations.isEmpty()) {
                destinations.add(new DestinationEta("Unknown", 0, false));
            }
            String line = normalizeId(getConfig().getString(path + "line", ""));
            int leftId = getConfig().getInt(path + "map-id-left", getConfig().getInt(path + "map-id", -1));
            int rightId = getConfig().getInt(path + "map-id-right", -1);
            World world = Bukkit.getWorlds().stream().findFirst().orElse(null);
            if (world == null) {
                getLogger().warning("Could not load ETA board '" + id + "': no worlds are available.");
                continue;
            }
            MapView leftMap = leftId < 0 ? null : Bukkit.getMap(leftId);
            if (leftMap == null) {
                leftMap = Bukkit.createMap(world);
            }
            MapView rightMap = rightId < 0 ? null : Bukkit.getMap(rightId);
            if (rightMap == null) {
                rightMap = Bukkit.createMap(world);
            }
            BufferedImage logo = line == null ? null : loadLineLogo(line);
                EtaBoard board = new EtaBoard(id, destinations, line == null ? "" : line,
                    leftMap.getId(), rightMap.getId(), logo);
                board.tickerText = getInfoText(board.line);
            configureMap(leftMap);
            configureMap(rightMap);
            attachRenderers(board, leftMap, rightMap);
            boards.put(id, board);
        }
        saveBoards();
    }

    private void saveBoards() {
        getConfig().set("boards", null);
        for (EtaBoard board : boards.values()) {
            String path = "boards." + board.id + ".";
            getConfig().set(path + "line", board.line);
            getConfig().set(path + "map-id-left", board.leftMapId);
            getConfig().set(path + "map-id-right", board.rightMapId);
            for (int index = 0; index < board.destinations.size(); index++) {
                DestinationEta destination = board.destinations.get(index);
                String destinationPath = path + "destinations." + index + ".";
                getConfig().set(destinationPath + "name", destination.name);
                getConfig().set(destinationPath + "minutes", destination.minutes);
                getConfig().set(destinationPath + "arrived", destination.arrived);
            }
        }
        getConfig().set("lines", new ArrayList<>(lines));
        getConfig().set("line-logo-files", new HashMap<>(lineLogoFiles));
        getConfig().set("infotext", new HashMap<>(infoTexts));
        getConfig().set("infotext-disabled-lines", new ArrayList<>(disabledInfoTextLines));
        saveConfig();
    }

    private void configureMap(MapView mapView) {
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        for (MapRenderer renderer : new ArrayList<>(mapView.getRenderers())) {
            mapView.removeRenderer(renderer);
        }
    }

    private void attachRenderers(EtaBoard board, MapView leftMap, MapView rightMap) {
        leftMap.addRenderer(new EtaMapRenderer(board, 0));
        rightMap.addRenderer(new EtaMapRenderer(board, 1));
    }

    private ItemStack createMapItem(EtaBoard board, MapView mapView, String side) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(mapView);
        meta.setDisplayName("ETA " + board.id + " (" + side + "): " + destinationNames(board));
        item.setItemMeta(meta);
        return item;
    }

    private File getLineFolder() {
        return new File(getDataFolder(), "line numbers");
    }

    private BufferedImage loadLineLogo(String line) {
        String fileName = lineLogoFiles.getOrDefault(line, line + ".png");
        File file = new File(getLineFolder(), fileName);
        if (!file.isFile()) {
            file = new File(new File(getDataFolder(), "lines"), line + ".png");
        }
        return readLineLogo(file);
    }

    private BufferedImage readLineLogo(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            return ImageIO.read(file);
        } catch (IOException exception) {
            getLogger().warning("Could not read line logo " + file.getName() + ": " + exception.getMessage());
            return null;
        }
    }

    private List<String> listLinePngFiles() {
        String[] fileNames = getLineFolder().list((folder, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (fileNames == null) {
            return List.of();
        }
        Arrays.sort(fileNames);
        return Arrays.asList(fileNames);
    }

    private static String validateLogoFileName(String fileName) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".png")
                || fileName.contains("/") || fileName.contains("\\") || !new File(fileName).getName().equals(fileName)) {
            return null;
        }
        return fileName;
    }

    private static String normalizeId(String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT);
        return id.matches("[a-z0-9_-]+") ? id : null;
    }

    private static String joinDestination(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    private static String joinDestination(String[] args, int start, int end) {
        return String.join(" ", Arrays.copyOfRange(args, start, end));
    }

    private static String formatMinutes(int minutes) {
        return minutes == 0 ? "Arriving" : minutes + " Min";
    }

    private static String formatEta(DestinationEta destination) {
        return destination.arrived ? "Arrived" : formatMinutes(destination.minutes);
    }

    private static String destinationNames(EtaBoard board) {
        List<String> names = new ArrayList<>();
        for (DestinationEta destination : board.destinations) {
            names.add(destination.name);
        }
        return String.join(" | ", names);
    }

    private static String formatDestinations(EtaBoard board) {
        List<String> statuses = new ArrayList<>();
        for (int index = 0; index < board.destinations.size(); index++) {
            DestinationEta destination = board.destinations.get(index);
            statuses.add((index + 1) + ". " + destination.name + ": " + formatEta(destination));
        }
        return String.join(" | ", statuses);
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static void markArrived(DestinationEta destination) {
        destination.minutes = 0;
        destination.arrived = true;
    }

    private static List<String> parseDestinations(String text) {
        return Arrays.stream(text.split("\\|"))
                .map(String::trim)
                .filter(destination -> !destination.isEmpty())
                .toList();
    }

    private static void drawEtaRow(Graphics2D graphics, DestinationEta destination, int baseline) {
        String countdown = formatEta(destination);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 16);
        FontMetrics metrics = graphics.getFontMetrics(font);
        while (metrics.stringWidth(countdown) > 84 && font.getSize() > 9) {
            font = font.deriveFont((float) (font.getSize() - 1));
            metrics = graphics.getFontMetrics(font);
        }
        graphics.setFont(font);

        String name = destination.name;
        while (!name.isEmpty() && metrics.stringWidth(name) > 148) {
            name = name.substring(0, name.length() - 1);
        }
        if (!name.equals(destination.name)) {
            while (!name.isEmpty() && metrics.stringWidth(name + "...") > 148) {
                name = name.substring(0, name.length() - 1);
            }
            name += "...";
        }

        graphics.setColor(Color.WHITE);
        graphics.drawString(name, 8, baseline);
        graphics.drawString("|", 166, baseline);
        graphics.drawString(countdown, 248 - metrics.stringWidth(countdown), baseline);
    }

    private static void drawColoredText(Graphics2D graphics, FontMetrics metrics, String text, int startX,
                                        int baseline, Color defaultColor) {
        StringBuilder segment = new StringBuilder();
        int cursorX = startX;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == ChatColor.COLOR_CHAR && index + 1 < text.length()) {
                ChatColor code = ChatColor.getByChar(Character.toLowerCase(text.charAt(index + 1)));
                if (code != null) {
                    String visibleSegment = segment.toString();
                    graphics.drawString(visibleSegment, cursorX, baseline);
                    cursorX += metrics.stringWidth(visibleSegment);
                    segment.setLength(0);
                    if (code.isColor()) {
                        graphics.setColor(getAwtColor(code));
                    } else if (code == ChatColor.RESET) {
                        graphics.setColor(defaultColor);
                    }
                    index++;
                    continue;
                }
            }
            segment.append(text.charAt(index));
        }
        String visibleSegment = segment.toString();
        graphics.drawString(visibleSegment, cursorX, baseline);
    }

    private static Color getAwtColor(ChatColor code) {
        return switch (code) {
            case BLACK -> new Color(0, 0, 0);
            case DARK_BLUE -> new Color(0, 0, 170);
            case DARK_GREEN -> new Color(0, 170, 0);
            case DARK_AQUA -> new Color(0, 170, 170);
            case DARK_RED -> new Color(170, 0, 0);
            case DARK_PURPLE -> new Color(170, 0, 170);
            case GOLD -> new Color(255, 170, 0);
            case GRAY -> new Color(170, 170, 170);
            case DARK_GRAY -> new Color(85, 85, 85);
            case BLUE -> new Color(85, 85, 255);
            case GREEN -> new Color(85, 255, 85);
            case AQUA -> new Color(85, 255, 255);
            case RED -> new Color(255, 85, 85);
            case LIGHT_PURPLE -> new Color(255, 85, 255);
            case YELLOW -> new Color(255, 255, 85);
            case WHITE -> new Color(255, 255, 255);
            default -> Color.BLACK;
        };
    }

    private static final class DestinationEta {
        private String name;
        private int minutes;
        private boolean arrived;

        private DestinationEta(String name, int minutes, boolean arrived) {
            this.name = name;
            this.minutes = minutes;
            this.arrived = arrived;
        }
    }

    private static final class EtaBoard {
        private final String id;
        private final String line;
        private final int leftMapId;
        private final int rightMapId;
        private final BufferedImage logo;
        private List<DestinationEta> destinations;
        private int destinationPage;
        private String tickerText = "";
        private long scrollOffset;
        private String lastRendered;
        private BufferedImage[] renderedImages;

        private EtaBoard(String id, List<DestinationEta> destinations, String line,
                         int leftMapId, int rightMapId, BufferedImage logo) {
            this.id = id;
            this.destinations = new ArrayList<>(destinations);
            this.line = line;
            this.leftMapId = leftMapId;
            this.rightMapId = rightMapId;
            this.logo = logo;
        }

        private void invalidate() {
            lastRendered = null;
        }

        private BufferedImage[] renderImages() {
            StringBuilder destinationState = new StringBuilder();
            for (DestinationEta destination : destinations) {
            destinationState.append(destination.name).append('\u0001')
                .append(destination.minutes).append('\u0001').append(destination.arrived).append('\u0002');
            }
            String renderKey = destinationState + "\u0000" + destinationPage + "\u0000" + tickerText
                + "\u0000" + scrollOffset;
            if (renderKey.equals(lastRendered)) {
                return renderedImages;
            }

            BufferedImage image = new BufferedImage(256, 128, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(220, 220, 220));
            graphics.fillRect(0, 0, 256, 32);
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 32, 256, 78);

            if (logo != null) {
                double scale = Math.min(28.0 / logo.getWidth(), 28.0 / logo.getHeight());
                int logoWidth = Math.max(1, (int) (logo.getWidth() * scale));
                int logoHeight = Math.max(1, (int) (logo.getHeight() * scale));
                graphics.drawImage(logo, 6, 6, logoWidth, logoHeight, null);
            }

            int firstDestination = destinations.size() > 2 ? destinationPage * 2 : 0;
            int visibleCount = Math.min(2, destinations.size() - firstDestination);
            if (visibleCount == 1) {
                drawEtaRow(graphics, destinations.get(firstDestination), 76);
            } else {
                drawEtaRow(graphics, destinations.get(firstDestination), 60);
                drawEtaRow(graphics, destinations.get(firstDestination + 1), 91);
            }

            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 110, 256, 18);
            if (!tickerText.isBlank()) {
                Font tickerFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
                graphics.setFont(tickerFont);
                FontMetrics tickerMetrics = graphics.getFontMetrics();
                String formattedTicker = ChatColor.translateAlternateColorCodes('&', tickerText);
                String visibleTicker = ChatColor.stripColor(formattedTicker);
                int textWidth = tickerMetrics.stringWidth(visibleTicker);
                int gap = Math.max(24, tickerMetrics.stringWidth("   "));
                int cycleWidth = textWidth + gap;
                Color tickerDefaultColor = Color.BLACK;
                int x = -(int) (scrollOffset % cycleWidth);
                while (x < image.getWidth()) {
                    graphics.setColor(tickerDefaultColor);
                    drawColoredText(graphics, tickerMetrics, formattedTicker, x, 123, tickerDefaultColor);
                    x += cycleWidth;
                }
            }
            graphics.dispose();

            renderedImages = new BufferedImage[] {
                    image.getSubimage(0, 0, 128, 128),
                    image.getSubimage(128, 0, 128, 128)
            };
            lastRendered = renderKey;
            return renderedImages;
        }
    }

    private static final class EtaMapRenderer extends MapRenderer {
        private final EtaBoard board;
        private final int tile;

        private EtaMapRenderer(EtaBoard board, int tile) {
            this.board = board;
            this.tile = tile;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            canvas.drawImage(0, 0, board.renderImages()[tile]);
        }
    }
}