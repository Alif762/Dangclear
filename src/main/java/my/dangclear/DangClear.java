package my.dangclear;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class DangClear extends JavaPlugin implements CommandExecutor {

    private int interval;
    private int remaining;
    private final Set<Integer> warnings = new HashSet<>();
    private BukkitTask task;

    @Override
    public void onEnable() {
        // Tetapan default, disimpan dalam plugins/DangClear/config.yml
        getConfig().addDefault("interval-seconds", 600);
        getConfig().addDefault("warning-seconds", Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1));
        getConfig().addDefault("messages.prefix", "&8[&fDang&aClear&8] ");
        getConfig().addDefault("messages.warning", "&eDropped items will be cleared in &c%time%&e!");
        getConfig().addDefault("messages.cleared", "&aCleared &f%count% &adropped items!");
        getConfig().addDefault("messages.next", "&7Next clear in &f%time%&7.");
        getConfig().addDefault("messages.reloaded", "&aConfig reloaded.");
        getConfig().addDefault("messages.no-permission", "&cYou don't have permission.");

        // Bunyi
        getConfig().addDefault("sounds.enabled", true);
        getConfig().addDefault("sounds.countdown.sound", "entity.player.levelup");
        getConfig().addDefault("sounds.countdown.volume", 1.0);
        getConfig().addDefault("sounds.countdown.pitch", 1.0);
        getConfig().addDefault("sounds.cleared.sound", "entity.generic.explode");
        getConfig().addDefault("sounds.cleared.volume", 1.0);
        getConfig().addDefault("sounds.cleared.pitch", 1.0);
        // 0 = bunyi letupan setiap kali clear. Set 67 untuk bunyi hanya bila 67 item atau lebih dipadam.
        getConfig().addDefault("sounds.min-items-for-explosion", 0);

        getConfig().options().copyDefaults(true);
        saveConfig();

        loadSettings();
        if (getCommand("dangclear") != null) getCommand("dangclear").setExecutor(this);

        // Tick setiap 1 saat (20 tick)
        task = Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
    }

    private void loadSettings() {
        interval = Math.max(10, getConfig().getInt("interval-seconds", 600));
        warnings.clear();
        warnings.addAll(getConfig().getIntegerList("warning-seconds"));
        remaining = interval;
    }

    private String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "");
        String text = getConfig().getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', prefix + text);
    }

    private String plain(String key) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + key, ""));
    }

    // ---------- Bunyi ----------
    private void playSound(String path) {
        if (!getConfig().getBoolean("sounds.enabled", true)) return;
        String sound = getConfig().getString("sounds." + path + ".sound", "");
        if (sound.isEmpty()) return;
        float volume = (float) getConfig().getDouble("sounds." + path + ".volume", 1.0);
        float pitch = (float) getConfig().getDouble("sounds." + path + ".pitch", 1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.playSound(p.getLocation(), sound, volume, pitch);
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------- Timer ----------
    private void tick() {
        remaining--;
        if (remaining <= 0) {
            clearItems();
            remaining = interval;
            return;
        }
        if (warnings.contains(remaining)) {
            Bukkit.broadcastMessage(msg("warning").replace("%time%", timeText(remaining)));
            playSound("countdown");
        }
    }

    private void clearItems() {
        int count = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Item item : w.getEntitiesByClass(Item.class)) {
                item.remove();
                count++;
            }
        }
        Bukkit.broadcastMessage(msg("cleared").replace("%count%", String.valueOf(count)));

        int min = getConfig().getInt("sounds.min-items-for-explosion", 0);
        if (count >= min) playSound("cleared");
    }

    private String timeText(int s) {
        if (s >= 60 && s % 60 == 0) {
            int m = s / 60;
            return m + (m == 1 ? " minute" : " minutes");
        }
        return s + (s == 1 ? " second" : " seconds");
    }

    // ---------- Command ----------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(msg("next").replace("%time%", timeText(remaining)));
            return true;
        }

        if (!sender.hasPermission("dangclear.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    getConfig().getString("messages.prefix", "") + plain("no-permission")));
            return true;
        }

        if (args[0].equalsIgnoreCase("now")) {
            clearItems();
            remaining = interval;
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadSettings();
            sender.sendMessage(msg("reloaded"));
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /dangclear [now|reload]");
        return true;
    }
        }
