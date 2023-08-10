package org.plugintest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class Bussola extends JavaPlugin implements CommandExecutor {

    private List<String> commands;
    private List<String> executedCommands;
    private Connection connection;

    private Map<String, Long> commandCooldowns;

    @Override
    public void onEnable() {
        this.getCommand("bussola").setExecutor(this);
        executedCommands = new ArrayList<>();
        commandCooldowns = new HashMap<>();
        loadCommands();
        connectToDatabase();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bussolareload")) {
            reloadCommands();
            sender.sendMessage("Comandi ricaricati dalla configurazione.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Questo comando può essere eseguito solo da un giocatore.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Opzioni disponibili: attiva, disattiva, esci");
            return true;
        }

        String option = args[0].toLowerCase();

        switch (option) {
            case "attiva":
                if (countdownTasks.containsKey(player)) {
                    countdownTasks.get(player).cancel();
                }
                executeNextCommand(player, true);
                break;
            case "disattiva":
                executedCommands.remove(player.getName());
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Hai disattivato la bussola.");
                executeNextCommand(player, false); // Set activateCountdown to false
                break;
            case "esci":
                player.sendMessage(ChatColor.YELLOW + "Sessione terminata.");
                break;
            default:
                player.sendMessage(ChatColor.RED + "Opzione non valida. Opzioni disponibili: attiva, disattiva, esci");
                break;
        }


        String playerName = player.getName();
        if (commandCooldowns.containsKey(playerName)) {
            long lastUsage = commandCooldowns.get(playerName);
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - lastUsage;
            long remainingCooldown = 5000 - elapsedTime;

            if (remainingCooldown > 0) {
                int seconds = (int) (remainingCooldown / 1000);
                player.sendMessage("Devi aspettare ancora " + seconds + " secondi prima di poter usare nuovamente il comando.");
                return true;
            }
        }

        updateLastCommandTime(playerName, System.currentTimeMillis());
        commandCooldowns.put(playerName, System.currentTimeMillis());

        return true;
    }
    private void reloadCommands() {
        reloadConfig();
        loadCommands();
    }

    private void loadCommands() {
        FileConfiguration config = getConfig();
        if (!config.contains("commands")) {
            config.set("commands", Arrays.asList("/bussola attiva", "/bussola disattiva"));
            saveConfig();
        }

        commands = config.getStringList("commands");
    }

    private void executeNextCommand(Player player, boolean activateCountdown) {
        if (commands.isEmpty()) {
            player.sendMessage("Nessun comando trovato.");
            return;
        }

        if (!executedCommands.contains(player.getName())) {
            // Esegui il primo comando
            String firstCommand = commands.get(0);
            player.performCommand(firstCommand);
            if (activateCountdown) {
                executeCountdown(player, true);
            }
        } else {
            // Esegui il comando successivo
            int currentIndex = executedCommands.indexOf(player.getName());
            int nextIndex = (currentIndex + 1) % commands.size();
            String nextCommand = commands.get(nextIndex);
            player.performCommand(nextCommand);
            if (activateCountdown) {
                executeCountdown(player, true);
            }
        }
    }

    private void executeCountdown(Player player, boolean activate) {
        int seconds = 4;

        if (!activate) {
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Countdown annullato.");
            return;
        }

        new BukkitRunnable() {
            int secondsLeft = seconds;

            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Puoi rieseguire il comando");
                    this.cancel();
                    return;
                }

                ChatColor color = ChatColor.GREEN;
                if (secondsLeft >= 4) {
                    color = ChatColor.RED;
                } else if (secondsLeft >= 2) {
                    color = ChatColor.YELLOW;
                }

                player.sendMessage(color + "" + ChatColor.BOLD + "Hai " + color + "" + ChatColor.BOLD + secondsLeft + ChatColor.RESET + "" + ChatColor.BOLD + " secondi prima del prossimo comando.");
                secondsLeft--;
            }
        }.runTaskTimer(this, 1, 20); // Run every second
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
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}