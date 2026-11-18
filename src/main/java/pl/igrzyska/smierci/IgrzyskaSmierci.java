package pl.igrzyska.smierci;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IgrzyskaSmierci extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§cIgrzyska Śmierci§8] §7";
    private static final String ZNAKI = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Set<UUID> niezalogowani = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> proby = new HashMap<>();
    private final SecureRandom rng = new SecureRandom();
    private File dataFile;
    private FileConfiguration data;
    private int tik = 0;

    // ------------------------------------------------------------ start/stop

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);

        // Co 3 sekundy: tytuł na ekranie, co ok. 15 s: instrukcja na czacie
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tik++;
            for (UUID id : niezalogowani) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                pokazTytul(p);
                if (tik % 5 == 0) wyslijInstrukcjeNaCzat(p);
            }
        }, 20L, 60L);

        getLogger().info("Plugin Igrzyska śmierci Minecraft 10k włączony.");
    }

    @Override
    public void onDisable() {
        for (UUID id : niezalogowani) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) zdejmijBlokady(p);
        }
        zapisz();
    }

    // ------------------------------------------------------------ dane

    private synchronized void zapisz() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Nie udało się zapisać data.yml: " + e.getMessage());
        }
    }

    private String klucz(String nick) {
        return "gracze." + nick.toLowerCase(Locale.ROOT);
    }

    private synchronized boolean naLiscie(String nick) {
        return data.contains(klucz(nick));
    }

    private synchronized boolean juzZalogowany(String nick) {
        return data.getBoolean(klucz(nick) + ".zalogowany", false);
    }

    private String losuj(int dlugosc, String znaki) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dlugosc; i++) sb.append(znaki.charAt(rng.nextInt(znaki.length())));
        return sb.toString();
    }

    private Location spawn(String nazwa) {
        Object o;
        synchronized (this) {
            o = data.get("spawn." + nazwa);
        }
        if (o instanceof Location) return ((Location) o).clone();
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private boolean maSpawn(String nazwa) {
        synchronized (this) {
            return data.get("spawn." + nazwa) instanceof Location;
        }
    }

    // ------------------------------------------------------------ komendy

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String c = cmd.getName().toLowerCase(Locale.ROOT);
        switch (c) {
            case "zaloguj":
                return komendaZaloguj(sender, args);
            case "playeradd":
                return komendaPlayerAdd(sender, args);
            case "playerremove":
                return komendaPlayerRemove(sender, args);
            case "spawndlanowychgraczy":
                return ustawSpawn(sender, "nowi", "dla nowych graczy");
            case "spawndlagraczy":
                return ustawSpawn(sender, "gracze", "dla zalogowanych graczy");
            default:
                return false;
        }
    }

    private boolean ustawSpawn(CommandSender sender, String nazwa, String opis) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "§cTę komendę może użyć tylko gracz.");
            return true;
        }
        if (!sender.hasPermission("igrzyska.admin")) {
            sender.sendMessage(PREFIX + "§cBrak uprawnień.");
            return true;
        }
        Player p = (Player) sender;
        synchronized (this) {
            data.set("spawn." + nazwa, p.getLocation());
        }
        zapisz();
        p.sendMessage(PREFIX + "§aUstawiono spawn " + opis + " w Twojej obecnej pozycji.");
        return true;
    }

    private boolean komendaPlayerAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("igrzyska.admin")) {
            sender.sendMessage(PREFIX + "§cBrak uprawnień.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(PREFIX + "§cUżycie: /playeradd <nick>");
            return true;
        }
        String nick = args[0];
        String k = klucz(nick);
        boolean istnial;
        synchronized (this) {
            istnial = data.contains(k);
            if (!istnial) {
                data.set(k + ".nick", nick);
                data.set(k + ".zaproszenie", "INV-" + losuj(8, ZNAKI));
                data.set(k + ".gracz", "PL-" + losuj(6, "0123456789"));
                data.set(k + ".sesja", "SES-" + losuj(10, ZNAKI));
                data.set(k + ".zalogowany", false);
            }
        }
        zapisz();

        String zap, id, ses;
        synchronized (this) {
            zap = data.getString(k + ".zaproszenie");
            id = data.getString(k + ".gracz");
            ses = data.getString(k + ".sesja");
        }
        sender.sendMessage(PREFIX + (istnial
                ? "§eGracz §f" + nick + " §ejuż jest na whiteliście. Jego ID:"
                : "§aDodano §f" + nick + " §ado whitelisty. Wygenerowane ID:"));
        sender.sendMessage("§7ID zaproszenia: §f" + zap);
        sender.sendMessage("§7ID gracza: §f" + id);
        sender.sendMessage("§7ID sesji: §f" + ses);
        sender.sendMessage("§7Gracz po wejściu wpisuje: §e/zaloguj " + zap + " " + id + " " + ses);
        return true;
    }

    private boolean komendaPlayerRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("igrzyska.admin")) {
            sender.sendMessage(PREFIX + "§cBrak uprawnień.");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(PREFIX + "§cUżycie: /playerremove <nick>");
            return true;
        }
        if (!naLiscie(args[0])) {
            sender.sendMessage(PREFIX + "§cTakiego gracza nie ma na whiteliście.");
            return true;
        }
        synchronized (this) {
            data.set(klucz(args[0]), null);
        }
        zapisz();
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null && !online.isOp()) online.kickPlayer("§cZostałeś usunięty z whitelisty.");
        sender.sendMessage(PREFIX + "§aUsunięto gracza §f" + args[0] + " §az whitelisty.");
        return true;
    }

    private boolean komendaZaloguj(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }
        Player p = (Player) sender;
        if (!niezalogowani.contains(p.getUniqueId())) {
            p.sendMessage(PREFIX + "§eJesteś już zalogowany.");
            return true;
        }
        if (args.length != 3) {
            p.sendMessage(PREFIX + "§cUżycie: §e/zaloguj <id zaproszenia> <id gracza> <id sesji>");
            return true;
        }

        String k = klucz(p.getName());
        String zap, id, ses;
        synchronized (this) {
            zap = data.getString(k + ".zaproszenie", "");
            id = data.getString(k + ".gracz", "");
            ses = data.getString(k + ".sesja", "");
        }

        boolean ok = !zap.isEmpty()
                && zap.equalsIgnoreCase(args[0])
                && id.equalsIgnoreCase(args[1])
                && ses.equalsIgnoreCase(args[2]);

        if (!ok) {
            int max = getConfig().getInt("max-prob-logowania", 5);
            int n = proby.merge(p.getUniqueId(), 1, Integer::sum);
            if (n >= max) {
                p.kickPlayer("§cZbyt wiele błędnych prób logowania.");
                return true;
            }
            p.sendMessage(PREFIX + "§cBłędne dane logowania! Próba " + n + "/" + max + ".");
            return true;
        }

        proby.remove(p.getUniqueId());
        synchronized (this) {
            data.set(k + ".zalogowany", true);
        }
        zapisz();
        zdejmijBlokady(p);
        niezalogowani.remove(p.getUniqueId());

        p.teleport(spawn("gracze"));
        p.sendTitle("§6Witaj w Igrzyskach Śmierci!", "§7Niech los zawsze Ci sprzyja...", 10, 80, 20);
        p.sendMessage(PREFIX + "§aZalogowano pomyślnie! Witaj w §cIgrzyskach Śmierci Minecraft 10k§a, §f" + p.getName() + "§a!");
        p.sendMessage(PREFIX + "§7Zostałeś przeniesiony na miejsce startu. Powodzenia!");
        return true;
    }

    // ------------------------------------------------------------ blokada

    private void zablokuj(Player p) {
        niezalogowani.add(p.getUniqueId());
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        PotionEffectType slow = PotionEffectType.getByName("SLOWNESS");
        if (slow == null) slow = PotionEffectType.getByName("SLOW");
        if (slow != null) {
            p.addPotionEffect(new PotionEffect(slow, Integer.MAX_VALUE, 255, false, false, false));
        }
        p.setInvulnerable(true);
        p.teleport(spawn("nowi"));
        pokazTytul(p);
        wyslijInstrukcjeNaCzat(p);
    }

    private void zdejmijBlokady(Player p) {
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        PotionEffectType slow = PotionEffectType.getByName("SLOWNESS");
        if (slow == null) slow = PotionEffectType.getByName("SLOW");
        if (slow != null) p.removePotionEffect(slow);
        p.setInvulnerable(false);
    }

    private void pokazTytul(Player p) {
        p.sendTitle("§cZaloguj się!",
                "§e/zaloguj <id zaproszenia> <id gracza> <id sesji>", 0, 70, 10);
    }

    private void wyslijInstrukcjeNaCzat(Player p) {
        p.sendMessage("§8§m--------------------------------------");
        p.sendMessage(PREFIX + "§cJesteś na liście graczy, ale musisz się zalogować!");
        p.sendMessage("§7Wpisz: §e/zaloguj <id zaproszenia> <id gracza> <id sesji>");
        p.sendMessage("§7Wszystkie trzy ID dostaniesz od operatora serwera.");
        p.sendMessage("§7Do czasu zalogowania nic nie widzisz, nie możesz się ruszać ani używać innych komend.");
        p.sendMessage("§8§m--------------------------------------");
    }

    private boolean zablokowany(Player p) {
        return niezalogowani.contains(p.getUniqueId());
    }

    // ------------------------------------------------------------ zdarzenia

    // Własna whitelista
    @EventHandler
    public void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent e) {
        if (!getConfig().getBoolean("whitelist", true)) return;
        if (Bukkit.getOfflinePlayer(e.getUniqueId()).isOp()) return;
        if (!naLiscie(e.getName())) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                    "§cNie jesteś na whiteliście serwera Igrzyska Śmierci Minecraft 10k.\n§7Skontaktuj się z operatorem.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.isOp() && getConfig().getBoolean("ops-pomijaja-logowanie", true)) return;

        boolean wymaga = !juzZalogowany(p.getName())
                || getConfig().getBoolean("wymagaj-logowania-przy-kazdym-wejsciu", false);
        if (!wymaga) return;

        e.setJoinMessage(null);
        zablokuj(p);

        if (!maSpawn("nowi")) {
            for (Player op : Bukkit.getOnlinePlayers()) {
                if (op.hasPermission("igrzyska.admin"))
                    op.sendMessage(PREFIX + "§eUwaga: nie ustawiono /spawndlanowychgraczy - używam spawnu świata.");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (niezalogowani.remove(p.getUniqueId())) {
            e.setQuitMessage(null);
            zdejmijBlokady(p);
        }
        proby.remove(p.getUniqueId());
    }

    // Każda próba ruchu = powrót na spawn dla nowych graczy
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!zablokowany(p)) return;
        Location f = e.getFrom();
        Location t = e.getTo();
        if (t == null) return;
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
            Location s = spawn("nowi");
            s.setYaw(t.getYaw());
            s.setPitch(t.getPitch());
            p.teleport(s);
        }
    }

    // Niezalogowany nie widzi czatu i nikt nie widzi jego wiadomości
    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onChat(AsyncPlayerChatEvent e) {
        if (zablokowany(e.getPlayer())) {
            e.setCancelled(true);
            return;
        }
        e.getRecipients().removeIf(this::zablokowany);
    }

    // Jedyna dozwolona komenda: /zaloguj
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!zablokowany(p)) return;
        String msg = e.getMessage().trim();
        if (msg.startsWith("/")) msg = msg.substring(1);
        String pierwsza = msg.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (pierwsza.contains(":")) pierwsza = pierwsza.substring(pierwsza.indexOf(':') + 1);
        if (!pierwsza.equals("zaloguj")) {
            e.setCancelled(true);
            p.sendMessage(PREFIX + "§cPrzed zalogowaniem działa tylko §e/zaloguj <id zaproszenia> <id gracza> <id sesji>§c.");
        }
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) { if (zablokowany(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (zablokowany(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player && zablokowany((Player) e.getEntity())) e.setCancelled(true);
    }
    @EventHandler public void onBreak(BlockBreakEvent e) { if (zablokowany(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (zablokowany(e.getPlayer())) e.setCancelled(true); }
    @EventHandler public void onInvClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player && zablokowany((Player) e.getWhoClicked())) e.setCancelled(true);
    }
    @EventHandler public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && zablokowany((Player) e.getEntity())) e.setCancelled(true);
    }
}
