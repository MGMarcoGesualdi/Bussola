package org.plugintest;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class Bussola extends JavaPlugin implements CommandExecutor, Listener , TabCompleter {

    private List<String> commands;
    private List<String> executedCommands;
    private Connection connection;
    private Map<Player, Boolean> countdownActive = new HashMap<>();
    private Map<Player, Long> cooldownMap = new HashMap<>();



    private static final String PLUGIN_ASCII_ART =
            "                                                                                                     \n"+
                    " ______            _______  _______  _______  _        _______                              \n" +
                    "(  ___ \\ |\\     /|(  ____ \\(  ____ \\(  ___  )( \\      (  ___  )                             \n" +
                    "| (   ) )| )   ( || (    \\/| (    \\/| (   ) || (      | (   ) |                             \n" +
                    "| (__/ / | |   | || (_____ | (_____ | |   | || |      | (___) |                             \n" +
                    "|  __ (  | |   | |(_____  )(_____  )| |   | || |      |  ___  |                             \n" +
                    "| (  \\ \\ | |   | |      ) |      ) || |   | || |      | (   ) |                             \n" +
                    "| )___) )| (___) |/\\____) |/\\____) || (___) || (____/\\| )   ( |                             \n" +
                    "|______/ (_______)\\_________________________)(________|/ _______  _______  _______          \n" +
                    "(  ___ \\ |\\     /|  (  ____ )(  ___  )( (    /|(  __  \\ (  ___  )(  ____ \\(  ____ \\|\\     /|\n" +
                    "| (   ) )( \\   / )  | (    )|| (   ) ||  \\  ( || (  \\  )| (   ) || (    \\/| (    \\/ ( \\   / )\n" +
                    "| (__/ /  \\ (_) /   | (____)|| (___) ||   \\ | || |   ) || (___) || |      | (__     \\ (_) / \n" +
                    "|  __ (    \\   /    |  _____)|  ___  || (\\ \\) || |   | ||  ___  || | ____ |  __)     ) _ (  \n" +
                    "| (  \\ \\    ) (     | (      | (   ) || | \\   || |   ) || (   ) || | \\_  )| (       / ( ) \\ \n" +
                    "| )___) )   | |     | )      | )   ( || )  \\  || (__/  )| )   ( || (___) || (____/\\( /   \\ )\n" +
                    "|/ \\___/    \\_/     |/       |/     \\||/    )_)(______/ |/     \\|(_______)(_______/|/     \\|\n" +
                    "                                                                                             \n";


    @Override
    public void onEnable() {
        getLogger().info(PLUGIN_ASCII_ART);
        getLogger().info(ChatColor.GOLD + "Plugin attivo");
        setupPlugin();
    }

    private void setupPlugin() {
        getCommand("bussola").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        executedCommands = new ArrayList<>();
        loadCommands();
        connectToDatabase();
        FileConfiguration config = getConfig();
        // Carica i comandi dalla configurazione
        if (!config.contains("commands")) {
            config.set("commands", Arrays.asList("/bussola attiva", "/bussola disattiva"));
            saveConfig();
        }
        commands = config.getStringList("commands");
        String bussolaDescription = config.getString("commands.bussola.description");
        String bussolaUsage = config.getString("commands.bussola.usage");
        String cooldownMessage = config.getString("messages.cooldown");
        String noPermissionMessage = config.getString("messages.noPermission");
        String invalidOptionMessage = config.getString("messages.invalidOption");
        String activatedMessage = config.getString("messages.activated");
        String deactivatedMessage = config.getString("messages.deactivated");
        String commandsReloadedMessage = config.getString("messages.commandsReloaded");

        // Carica le impostazioni generali
        int countdownDurationSeconds = config.getInt("settings.countdownDurationSeconds");
        int ticksPerSecond = config.getInt("settings.ticksPerSecond");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bussolareload")) {
            reloadCommands();
            sender.sendMessage(":server:" + "Comandi ricaricati dalla configurazione.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("bussola")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Questo comando può essere eseguito solo da un giocatore.");
                return true;
            }

            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Opzioni disponibili: attiva e disattiva");
                return true;
            }

            String option = args[0].toLowerCase();
            String playerName = player.getName();

            switch (option) {
                case "attiva":
                    handleActivateCommand(player, playerName);
                    break;
                case "disattiva":
                    handleDeactivateCommand(player, playerName);
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "Opzione non valida. Opzioni disponibili: attiva, disattiva");
                    break;
            }

            return true;
        }
        if (command.getName().equalsIgnoreCase("help")) {
            if (args.length == 0) {
                sendBussolaHelp(sender);
                return true;
            }
        }

        return false;
    }
    private void sendBussolaHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Comandi disponibili per il plugin Bussola:");
        sender.sendMessage(ChatColor.GOLD + "/bussola attiva" + ChatColor.WHITE + " - Attiva la bussola.");
        sender.sendMessage(ChatColor.GOLD + "/bussola disattiva" + ChatColor.WHITE + " - Disattiva la bussola.");
        sender.sendMessage(ChatColor.GOLD + "/bussolareload" + ChatColor.WHITE + " - Ricarica i comandi dalla configurazione.");
    }


    private void handleActivateCommand(Player player, String playerName) {
        try {
            long currentTime = System.currentTimeMillis();
            if (cooldownMap.containsKey(player) && currentTime - cooldownMap.get(player) < 5000) {
                long remainingCooldown = (cooldownMap.get(player) + 5000 - currentTime) / 1000;

                if (remainingCooldown <= 1) {
                    player.sendMessage(ChatColor.RED + "Devi aspettare ancora 1 secondo prima di eseguire nuovamente questo comando.");
                } else {
                    player.sendMessage(ChatColor.RED + "Devi aspettare ancora " + remainingCooldown + " secondi prima di eseguire nuovamente questo comando.");
                    return;
                }
            }

            cooldownMap.put(player, currentTime);

            if (executedCommands.contains(playerName)) {
                executeCountdown(player, true);
            } else {
                executeNextCommand(player, false);
            }

            player.sendMessage(":info:" + ChatColor.YELLOW + "Hai attivato la bussola.");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " permission set BussolaJoin true");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Si è verificato un errore durante l'esecuzione del comando.");
            e.printStackTrace(); // Stampa l'errore nella console del server per ulteriori informazioni
        }
    }
    private void handleDeactivateCommand(Player player, String playerName) {
        long currentTime = System.currentTimeMillis();
        if (cooldownMap.containsKey(player) && currentTime - cooldownMap.get(player) < 5000) { // 5000 milliseconds = 5 seconds
            long remainingCooldown = (cooldownMap.get(player) + 5000 - currentTime + 999) / 1000; // Convert to seconds and round up

            if (remainingCooldown <= 1) {
                player.sendMessage(ChatColor.RED + "Devi aspettare ancora 1 secondo prima di eseguire nuovamente questo comando.");
                return;
            } else {
                player.sendMessage(ChatColor.RED + "Devi aspettare ancora " + remainingCooldown + " secondi prima di eseguire nuovamente questo comando.");
                return;
            }
        }

        cooldownMap.put(player, currentTime);

        if (executedCommands.contains(playerName)) {
            executeCountdown(player, true);
        } else {
            executedCommands.remove(playerName);
            executeNextCommand(player, false); // Set activateCountdown to false
        }
        player.sendMessage(":info:" + ChatColor.YELLOW + "" + "Hai disattivato la bussola.");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " permission set BussolaJoin false");
    }

    private void reloadCommands() {
        reloadConfig();
        loadCommands();
        saveConfig();
    }

    private void loadCommands() {
        FileConfiguration config = getConfig();
        List<String> defaultCommands = Arrays.asList("/bussola attiva", "/bussola disattiva");

        if (!config.contains("commands")) {
            config.set("commands", defaultCommands);
            saveConfig();
        }

        commands = config.getStringList("commands");

        // Stampa i comandi nel log
        getLogger().info("Comandi caricati dalla configurazione: " + commands);
    }



    private static final int COUNTDOWN_DURATION_SECONDS = 5;
    private static final int TICKS_PER_SECOND = 20;


    private void executeNextCommand(Player player, boolean activateCountdown) {
        if (commands.isEmpty()) {
            player.sendMessage("Nessun comando trovato.");
            return;
        }

        if (!executedCommands.contains(player.getName())) {
            // Esegui il primo comando
            String firstCommand = commands.get(0);
            player.performCommand(firstCommand);
            executedCommands.add(player.getName()); // Mark as executed
        } else {
            // Esegui il comando successivo only if countdown is not active
            if (!countdownActive.getOrDefault(player, false)) {
                int currentIndex = executedCommands.indexOf(player.getName());
                int nextIndex = (currentIndex + 1) % commands.size();
                String nextCommand = commands.get(nextIndex);
                player.performCommand(nextCommand);
                if (activateCountdown) {
                    executeCountdown(player, true);
                }
            } else {
                // Reset countdown state when the command is retyped
                countdownActive.put(player, false);
            }
        }
    }

    private void executeCountdown(Player player, boolean b) {
        int countdownDurationTicks = COUNTDOWN_DURATION_SECONDS * TICKS_PER_SECOND;

        new BukkitRunnable() {
            int ticksLeft = countdownDurationTicks;

            @Override
            public void run() {
                if (ticksLeft <= 0) {
                    this.cancel();
                    return;
                }

                // Update countdown messages
                if (ticksLeft % TICKS_PER_SECOND == 0) {
                    int secondsLeft = (int) Math.ceil((double) ticksLeft / TICKS_PER_SECOND);
                }

                ticksLeft--;
            }
        }.runTaskTimer(this, 0, 1); // Run every tick
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("bussola")) {
            if (args.length == 1) {
                String partialCommand = args[0].toLowerCase();
                if ("attiva".startsWith(partialCommand)) {
                    completions.add("attiva");
                }
                if ("disattiva".startsWith(partialCommand)) {
                    completions.add("disattiva");
                }
            }
        }

        return completions;
    }

    private long getLastCommandTime(String playerName) {
        if (commands.isEmpty()) {
            return 0; // Nessun comando da eseguire
        }

        String selectQuery = "SELECT timestamp FROM player_commands WHERE player_name = ? AND command = ?;";

        try (PreparedStatement statement = connection.prepareStatement(selectQuery)) {
            statement.setString(1, playerName);
            statement.setString(2, commands.get(0));
            return statement.executeQuery().getLong("timestamp");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    private void updateLastCommandTime(String playerName, long timestamp) {
        String updateQuery = "UPDATE player_commands SET timestamp = ? WHERE player_name = ? AND command = ?;";

        try (PreparedStatement statement = connection.prepareStatement(updateQuery)) {
            statement.setLong(1, timestamp);
            statement.setString(2, playerName);
            statement.setString(3, commands.get(0));
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void connectToDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:plugin_database.db");
            createTable();
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTable() throws SQLException {
        String createTableQuery = "CREATE TABLE IF NOT EXISTS player_commands (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_name TEXT NOT NULL," +
                "command TEXT NOT NULL," +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";

        try (PreparedStatement statement = connection.prepareStatement(createTableQuery)) {
            statement.executeUpdate();
        }
    }

    private void saveCommandToDatabase(Player player, String command) {
        String insertQuery = "INSERT INTO player_commands (player_name, command) VALUES (?, ?);";

        try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
            statement.setString(1, player.getName());
            statement.setString(2, command);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.YELLOW + "Plugin disattivo");
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
