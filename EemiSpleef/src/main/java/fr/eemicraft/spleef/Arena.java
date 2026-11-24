package fr.eemicraft.spleef;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

/** Une arene de Spleef et sa partie. */
final class Arena {
    enum State { WAITING, COUNTDOWN, GRACE, PLAYING, ENDING }

    final EemiSpleef pl;
    final int index;
    final int cx;
    final int cz = 0;
    State state = State.WAITING;
    /** Inscrits ; en partie : true = encore debout. */
    final Map<UUID, Boolean> players = new LinkedHashMap<>();
    final Map<UUID, String> names = new LinkedHashMap<>();
    final List<UUID> spectators = new ArrayList<>();
    /** Joueurs qui ont bouge la tete pendant la partie : seuls eux restent en file a la fin (anti-AFK). */
    final java.util.Set<UUID> active = new java.util.HashSet<>();
    /** Numero de partie : une tache planifiee d'une autre partie ne fait rien. */
    int gen;
    int countdown;
    int elapsed;
    int grace;
    boolean melting;
    int meltTimer;
    int bonusTimer;
    int startedWith;
    UUID lastOut;
    Scoreboard board;
    /** Etages : y du sol, rayon de depart, rayon courant. */
    int[] ly;
    int[] lr0;
    int[] lr;
    List<Material> mats;

    Arena(EemiSpleef pl, int index) {
        this.pl = pl;
        this.index = index;
        this.cx = index * pl.cfg("instance-spacing", 400, 160, 2000);
    }

    World w() {
        return pl.world;
    }

    boolean waitingPhase() {
        return state == State.WAITING || state == State.COUNTDOWN;
    }

    boolean inGamePhase() {
        return state == State.GRACE || state == State.PLAYING;
    }

    Location waitSpawn() {
        return new Location(w(), cx + 0.5, Builder.WAIT_Y, cz + (Builder.WZ1 + Builder.WZ2) / 2.0 + 0.5, 0f, 15f);
    }

    Location specSpawn() {
        return new Location(w(), cx + 0.5, Builder.TOP + 14, cz + 0.5, 0f, 70f);
    }

    int bottomY() {
        return ly == null ? Builder.TOP - (pl.layers - 1) * pl.gap : ly[ly.length - 1];
    }

    void forceLoad(boolean on) {
        int r = (Builder.AREA >> 4) + 1;
        for (int x = (cx >> 4) - r; x <= (cx >> 4) + r; x++) {
            for (int z = (cz >> 4) - r; z <= (cz >> 4) + r; z++) {
                w().setChunkForceLoaded(x, z, on);
            }
        }
    }

    void tell(String m) {
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                EemiSpleef.msg(p, m);
            }
        }
    }

    List<UUID> all() {
        List<UUID> l = new ArrayList<>(players.keySet());
        l.addAll(spectators);
        return l;
    }

    int alive() {
        int n = 0;
        for (boolean b : players.values()) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    void later(long ticks, Runnable r) {
        int g = gen;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (g == gen) {
                r.run();
            }
        }, ticks);
    }

    void title(Player p, String t, String sub, int stayMs) {
        p.showTitle(Title.title(EemiSpleef.c(t), EemiSpleef.c(sub),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(stayMs), Duration.ofMillis(300))));
    }

    // ================================================================ etages

    /** Rayon de l'etage du haut selon le nombre de joueurs. */
    int radiusFor(int n) {
        // v2 : terrain rectangulaire fixe qui remplit le stade (mur a HX+1 / HZ+1)
        return Builder.HX;
    }

    /**
     * Case (dx, dz) dans le terrain de « rayon » r : rectangle de demi-cotes r (en x) et r - (HX - HZ) (en z).
     * La fonte retire 1 a r : le terrain retrecit d'un anneau rectangulaire.
     */
    static boolean inDisc(int dx, int dz, int r) {
        int rz = r - (Builder.HX - Builder.HZ);
        return r > 0 && rz >= 0 && Math.abs(dx) <= r && Math.abs(dz) <= rz;
    }

    /** Remet les etages a neuf pour n joueurs : on vide la zone des etages puis on pose les disques. */
    void resetLayers(int nPlayers) {
        mats = pl.layerMaterials();
        int n = pl.layers;
        ly = new int[n];
        lr0 = new int[n];
        lr = new int[n];
        int r = radiusFor(nPlayers);
        int shrink = pl.cfg("radius.shrink-per-layer", 2, 0, 6);
        for (int i = 0; i < n; i++) {
            ly[i] = Builder.TOP - i * pl.gap;
            lr0[i] = Math.max(4, r - i * shrink);
            lr[i] = lr0[i];
        }
        // seulement l'interieur du stade : le mur (HX+1 / HZ+1) n'est jamais touche
        for (int i = 0; i < n; i++) {
            Material mat = mats.get(Math.min(i, mats.size() - 1));
            for (int dx = -Builder.HX; dx <= Builder.HX; dx++) {
                for (int dz = -Builder.HZ; dz <= Builder.HZ; dz++) {
                    Block b = w().getBlockAt(cx + dx, ly[i], cz + dz);
                    Material want = inDisc(dx, dz, lr0[i]) ? mat : Material.AIR;
                    if (b.getType() != want) {
                        b.setType(want, false);
                    }
                }
            }
        }
    }

    int layerOf(Block b) {
        if (ly == null || !b.getWorld().equals(w())) {
            return -1;
        }
        for (int i = 0; i < ly.length; i++) {
            if (b.getY() == ly[i] && inDisc(b.getX() - cx, b.getZ() - cz, lr0[i] + 1) && mats.contains(b.getType())) {
                return i;
            }
        }
        return -1;
    }

    /** Casse un bloc d'etage (sans objet). */
    void crack(Block b) {
        Material m = b.getType();
        b.setType(Material.AIR, false);
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        w().spawnParticle(Particle.BLOCK, c, 10, 0.3, 0.3, 0.3, 0, m.createBlockData());
        w().playSound(c, Sound.BLOCK_SNOW_BREAK, 0.8f, 1.1f);
    }

    /** Fonte : retire l'anneau exterieur de chaque etage. */
    void meltStep() {
        boolean any = false;
        for (int i = 0; i < ly.length; i++) {
            int r = lr[i];
            if (r <= 0) {
                continue;
            }
            any = true;
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    if (inDisc(dx, dz, r) && !inDisc(dx, dz, r - 1)) {
                        Block b = w().getBlockAt(cx + dx, ly[i], cz + dz);
                        if (mats.contains(b.getType())) {
                            b.setType(Material.AIR, false);
                            if (((dx * 7 + dz * 13) & 7) == 0) {
                                w().spawnParticle(Particle.FALLING_WATER, b.getLocation().add(0.5, 0.2, 0.5), 2, 0.3, 0.1, 0.3, 0);
                            }
                        }
                    }
                }
            }
            lr[i] = r - 1;
        }
        if (any) {
            w().playSound(new Location(w(), cx + 0.5, Builder.TOP, cz + 0.5), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.8f);
        }
    }

    // ================================================================ deroulement

    void tick() {
        int min = pl.cfg("min-players", 2, 1, 32);
        int max = pl.cfg("max-players", 16, 2, 32);
        switch (state) {
            case WAITING -> {
                if (players.size() >= min) {
                    state = State.COUNTDOWN;
                    countdown = pl.cfg("countdown", 30, 5, 300);
                    tell("&aLa partie commence dans &e" + countdown + " s&a.");
                    pl.sendStatus();
                }
            }
            case COUNTDOWN -> {
                if (players.size() < min) {
                    state = State.WAITING;
                    tell("&cPas assez de joueurs, decompte annule.");
                    pl.sendStatus();
                    return;
                }
                int full = pl.cfg("countdown-full", 10, 3, 300);
                if (players.size() >= max && countdown > full) {
                    countdown = full;
                    tell("&aPartie pleine : depart dans &e" + countdown + " s&a.");
                }
                countdown--;
                if (countdown <= 0) {
                    start();
                } else if (countdown <= 5 || countdown % 10 == 0) {
                    for (UUID id : players.keySet()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            title(p, "&b" + countdown, "&7Sors ta pelle", 900);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
                        }
                    }
                }
            }
            case GRACE -> {
                grace--;
                if (grace <= 0) {
                    state = State.PLAYING;
                    for (UUID id : alivePlayers()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            title(p, "&b&lCREUSE !", "&7Casse la neige sous les autres", 1200);
                            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.4f);
                        }
                    }
                    tell("&b&lC'est parti ! &7Dernier debout gagne.");
                } else {
                    for (UUID id : alivePlayers()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            p.sendActionBar(EemiSpleef.c("&7On creuse dans &e" + grace + " s"));
                        }
                    }
                }
                updateBoard();
            }
            case PLAYING -> {
                elapsed++;
                int maxTime = pl.cfg("max-time", 300, 30, 3600);
                if (!melting && elapsed >= maxTime) {
                    melting = true;
                    meltTimer = 0;
                    tell("&6&lLa neige fond ! &7Les etages retrecissent par le bord.");
                    for (UUID id : all()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            title(p, "&6&lLA NEIGE FOND", "&7Les etages retrecissent", 1500);
                        }
                    }
                }
                if (melting) {
                    meltTimer++;
                    if (meltTimer >= pl.cfg("melt-interval", 3, 1, 30)) {
                        meltTimer = 0;
                        meltStep();
                    }
                }
                if (pl.getConfig().getBoolean("bonus.enabled", true)) {
                    bonusTimer++;
                    if (bonusTimer >= pl.cfg("bonus.interval", 45, 10, 600)) {
                        bonusTimer = 0;
                        giveBonus();
                    }
                }
                updateBoard();
            }
            default -> {
            }
        }
    }

    List<UUID> alivePlayers() {
        List<UUID> l = new ArrayList<>();
        for (Map.Entry<UUID, Boolean> e : players.entrySet()) {
            if (e.getValue()) {
                l.add(e.getKey());
            }
        }
        return l;
    }

    void start() {
        gen++;
        List<UUID> online = new ArrayList<>();
        for (UUID id : new ArrayList<>(players.keySet())) {
            if (Bukkit.getPlayer(id) == null) {
                players.remove(id);
                names.remove(id);
                pl.where.remove(id);
            } else {
                online.add(id);
                players.put(id, true);
            }
        }
        if (online.isEmpty()) {
            state = State.WAITING;
            pl.sendStatus();
            return;
        }
        startedWith = online.size();
        active.clear();
        lastOut = null;
        elapsed = 0;
        melting = false;
        bonusTimer = 0;
        resetLayers(online.size());
        state = State.GRACE;
        grace = pl.cfg("grace", 5, 0, 30);
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        Collections.shuffle(online);
        // places en ellipse dans le rectangle (a 60 % des demi-cotes)
        double ringX = Math.max(1.5, lr0[0] * 0.6);
        double ringZ = Math.max(1.5, (lr0[0] - (Builder.HX - Builder.HZ)) * 0.6);
        for (int i = 0; i < online.size(); i++) {
            Player p = Bukkit.getPlayer(online.get(i));
            double ang = 2 * Math.PI * i / online.size();
            double x = cx + 0.5 + Math.cos(ang) * ringX;
            double z = cz + 0.5 + Math.sin(ang) * ringZ;
            Location l = new Location(w(), x, ly[0] + 1, z);
            l.setDirection(new Vector(cx + 0.5 - x, 0, cz + 0.5 - z));
            l.setPitch(20f);
            p.teleport(l);
            p.setGameMode(GameMode.SURVIVAL);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setFallDistance(0);
            kit(p);
            p.setScoreboard(board);
            pl.eemi("eemi reward " + p.getName() + " " + pl.coreId() + " play");
        }
        for (UUID id : spectators) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setScoreboard(board);
            }
        }
        tell("&7Casse la neige sous les pieds des autres. Tomber dans la fosse = &celimine&7.");
        if (pl.getConfig().getBoolean("snowballs.enabled", true)) {
            tell("&7Chaque bloc casse donne une &fboule de neige &7: elle casse le bloc qu'elle touche.");
        }
        if (grace > 0) {
            tell("&e" + grace + " s &7pour te placer, on ne creuse pas encore.");
        } else {
            state = State.PLAYING;
        }
        updateBoard();
        pl.sendStatus();
    }

    void kit(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ItemStack shovel = pl.tagged(Material.DIAMOND_SHOVEL, "&b&lPelle de Spleef", "shovel");
        ItemMeta m = shovel.getItemMeta();
        m.addEnchant(Enchantment.EFFICIENCY, 5, true);
        m.setUnbreakable(true);
        m.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        shovel.setItemMeta(m);
        inv.setItem(0, shovel);
        inv.setHeldItemSlot(0);
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
    }

    /** Retire les objets du jeu (pelle, boules de neige, objet pour quitter) et les effets. */
    static void clearGameItems(EemiSpleef pl, Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && pl.tag(it) != null) {
                inv.setItem(i, null);
            }
        }
        for (PotionEffect e : p.getActivePotionEffects()) {
            p.removePotionEffect(e.getType());
        }
    }

    boolean canThrow(Player p) {
        return state == State.PLAYING && Boolean.TRUE.equals(players.get(p.getUniqueId()))
                && pl.getConfig().getBoolean("snowballs.enabled", true);
    }

    void giveSnowballs(Player p, int n) {
        if (!pl.getConfig().getBoolean("snowballs.enabled", true) || n <= 0) {
            return;
        }
        int max = pl.cfg("snowballs.max", 16, 0, 64);
        int have = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == Material.SNOWBALL && "ball".equals(pl.tag(it))) {
                have += it.getAmount();
            }
        }
        int add = Math.min(n, max - have);
        if (add > 0) {
            // boules marquees : seules elles sont retirees a la sortie (inventaire partage avec le lobby)
            ItemStack balls = pl.tagged(Material.SNOWBALL, "&fBoule de neige", "ball");
            balls.setAmount(add);
            p.getInventory().addItem(balls);
        }
    }

    void onDig(Player p, Block b) {
        if (state != State.PLAYING || !Boolean.TRUE.equals(players.get(p.getUniqueId()))) {
            return;
        }
        if (layerOf(b) < 0) {
            return;
        }
        crack(b);
        giveSnowballs(p, pl.cfg("snowballs.per-break", 1, 0, 16));
    }

    void onSnowballHit(Player shooter, Block hit, Entity target, Snowball ball) {
        if (state != State.PLAYING || !Boolean.TRUE.equals(players.get(shooter.getUniqueId()))) {
            return;
        }
        if (hit != null && pl.getConfig().getBoolean("snowballs.break-blocks", true) && layerOf(hit) >= 0) {
            crack(hit);
            return;
        }
        double kb = pl.cfgD("snowballs.knockback", 0.35, 0, 2);
        if (kb > 0 && target instanceof Player t && Boolean.TRUE.equals(players.get(t.getUniqueId())) && !t.equals(shooter)) {
            Vector v = ball.getVelocity().setY(0);
            if (v.lengthSquared() > 1.0E-4) {
                v.normalize().multiply(kb).setY(0.25);
                t.setVelocity(t.getVelocity().add(v));
            }
        }
    }

    void giveBonus() {
        List<String> types = pl.getConfig().getStringList("bonus.types");
        if (types.isEmpty()) {
            types = List.of("saut", "vitesse", "boules");
        }
        int dur = pl.cfg("bonus.duration", 8, 2, 60) * 20;
        for (UUID id : alivePlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            String t = types.get((int) (Math.random() * types.size())).toLowerCase();
            switch (t) {
                case "saut" -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur, 1, false, true, true));
                    p.sendActionBar(EemiSpleef.c("&aBonus : &fSaut ameliore"));
                }
                case "vitesse" -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 0, false, true, true));
                    p.sendActionBar(EemiSpleef.c("&aBonus : &fVitesse"));
                }
                case "boules" -> {
                    giveSnowballs(p, pl.cfg("bonus.snowballs", 6, 1, 64));
                    p.sendActionBar(EemiSpleef.c("&aBonus : &fBoules de neige"));
                }
                default -> {
                    continue;
                }
            }
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        }
    }

    void onMove(Player p, Location to) {
        UUID id = p.getUniqueId();
        if (waitingPhase()) {
            // dans le chalet : personne ne tombe
            if (players.containsKey(id) && to.getY() < Builder.WAIT_Y - 3) {
                p.teleport(waitSpawn());
            }
            return;
        }
        if (state == State.ENDING) {
            // le gagnant reste sur la neige ; s'il tombe, il rejoint les spectateurs (sans rien perdre)
            if (players.containsKey(id) && to.getY() < bottomY() - 3) {
                p.setGameMode(GameMode.SPECTATOR);
                p.teleport(specSpawn());
            }
            return;
        }
        if (!Boolean.TRUE.equals(players.get(id))) {
            return;
        }
        double dx = to.getX() - (cx + 0.5);
        double dz = to.getZ() - (cz + 0.5);
        boolean out = Math.abs(dx) > Builder.HX + 3 || Math.abs(dz) > Builder.HZ + 3;
        boolean fell = to.getY() < bottomY() - 3;
        if (!fell && !out) {
            return;
        }
        if (state == State.GRACE) {
            // pendant la grace, on se replace sans perdre
            p.teleport(new Location(w(), cx + 0.5, ly[0] + 1, cz + 0.5, p.getLocation().getYaw(), 20f));
            p.setFallDistance(0);
            return;
        }
        eliminate(p, out && !fell ? "Sorti de l'arene !" : "Tombe !");
    }

    void eliminate(Player p, String why) {
        UUID id = p.getUniqueId();
        if (!Boolean.TRUE.equals(players.get(id)) || !inGamePhase()) {
            return;
        }
        players.put(id, false);
        lastOut = id;
        int left = alive();
        tell("&f" + p.getName() + " &cest elimine ! &8(" + why + ") &7- encore &f" + left + " &7en jeu");
        title(p, "&c&lELIMINE", "&7" + why, 2000);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
        clearGameItems(pl, p);
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(specSpawn());
        updateBoard();
        pl.sendStatus();
        checkEnd();
    }

    void checkEnd() {
        if (!inGamePhase()) {
            return;
        }
        int left = alive();
        if (left == 0 || (left == 1 && startedWith >= 2)) {
            end();
        }
    }

    void end() {
        if (!inGamePhase()) {
            return;
        }
        state = State.ENDING;
        UUID winner = null;
        List<UUID> al = alivePlayers();
        if (al.size() == 1) {
            winner = al.get(0);
        } else if (al.isEmpty() && startedWith >= 2) {
            // tout le monde est tombe en meme temps (fonte) : le dernier sorti gagne
            winner = lastOut;
        }
        String name = winner == null ? null : names.get(winner);
        if (winner != null && name != null) {
            pl.eemi("eemi reward " + name + " " + pl.coreId() + " win");
            tell("&6&lVictoire de &e&l" + name + " &6&l! &7(" + timeText(elapsed) + ")");
            Bukkit.broadcast(EemiSpleef.c(EemiSpleef.P + "&e" + name + " &7gagne une partie de Spleef !"));
        } else {
            tell("&7Personne ne gagne cette fois.");
        }
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            boolean won = id.equals(winner);
            title(p, won ? "&6&lVICTOIRE !" : "&7Fin de partie", name == null ? "" : "&e" + name + " &7gagne", 3000);
            if (won) {
                // reste en survie sur la neige (on ne casse plus en ENDING) ; un gagnant deja tombe reste spectateur
                clearGameItems(pl, p);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                w().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 80, 0.6, 0.8, 0.6, 0.3);
            }
        }
        updateBoard();
        pl.sendStatus();
        later(pl.cfg("end-delay", 6, 1, 30) * 20L, () -> finish(true));
    }

    /**
     * Fin. Fin normale ({@code natural}) : les joueurs de la partie encore la (vivants et elimines) restent inscrits
     * et retournent au chalet d'attente de cette arene (file de la partie suivante) ; les simples spectateurs
     * rentrent au lobby. Arret admin : tout le monde au lobby, comme avant. Etages remis a neuf.
     */
    void finish(boolean natural) {
        gen++;
        List<UUID> stay = new ArrayList<>();
        if (natural) {
            int max = pl.cfg("max-players", 16, 2, 32);
            for (UUID id : players.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && pl.inWorld(p) && pl.where.get(id) == this && stay.size() < max) {
                    // anti-AFK retire (Esteban, 17/11) : tous les joueurs de la partie restent en file
                    stay.add(id);
                }
            }
        }
        active.clear();
        Map<UUID, String> keptNames = new LinkedHashMap<>();
        for (UUID id : stay) {
            keptNames.put(id, names.getOrDefault(id, "?"));
        }
        List<UUID> everyone = all();
        // on vide d'abord : le changement de monde qui suit ne repasse pas par leave()
        players.clear();
        names.clear();
        spectators.clear();
        for (UUID id : everyone) {
            if (!stay.contains(id)) {
                pl.where.remove(id);
            }
        }
        for (UUID id : everyone) {
            if (stay.contains(id)) {
                continue;
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null && pl.inWorld(p)) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                clearGameItems(pl, p);
                pl.toLobby(p);
            }
        }
        board = null;
        state = State.WAITING;
        melting = false;
        // de nouveau inscrits, au chalet (hors des etages) AVANT la remise a neuf du terrain
        for (UUID id : stay) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                pl.where.remove(id);
                continue;
            }
            players.put(id, true);
            names.put(id, keptNames.get(id));
            backToQueue(p);
        }
        resetLayers(pl.cfg("max-players", 16, 2, 32) / 2);
        pl.sendStatus();
    }

    /** Remet un joueur de la partie finie dans la file : chalet, etat d'attente, objet « Retour au lobby ». */
    void backToQueue(Player p) {
        // teleporter AVANT de toucher a l'inventaire
        p.teleport(waitSpawn());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        clearGameItems(pl, p);
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFlying(false);
        org.bukkit.attribute.AttributeInstance hp = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        p.setHealth(hp == null ? 20.0 : hp.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.getInventory().setItem(8, pl.leaveItem());
        EemiSpleef.msg(p, "&7Tu restes dans la file du &bSpleef&7. Clique sur le lit (ou &f/spleef leave&7) pour revenir au lobby.");
    }

    /** Arret du plugin. */
    void shutdown() {
        gen++;
        for (UUID id : all()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                clearGameItems(pl, p);
                if (pl.inWorld(p)) {
                    if (p.getGameMode() != GameMode.CREATIVE) {
                        p.setGameMode(GameMode.ADVENTURE);
                    }
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }
            }
        }
        players.clear();
        spectators.clear();
        forceLoad(false);
    }

    // ================================================================ entree / sortie

    void addPlayer(Player p) {
        UUID id = p.getUniqueId();
        players.put(id, true);
        names.put(id, p.getName());
        pl.where.put(id, this);
        p.teleport(waitSpawn());
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(false);
        p.setFoodLevel(20);
        int g = gen;
        // Multiverse-Inventories range d'abord l'inventaire du lobby, puis on vide (2 ticks apres)
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (g != gen || !p.isOnline() || pl.where.get(id) != this || !players.containsKey(id) || !waitingPhase()) {
                return;
            }
            p.getInventory().clear();
            p.getInventory().setItem(8, pl.leaveItem());
        }, 2L);
        int max = pl.cfg("max-players", 16, 2, 32);
        tell("&f" + p.getName() + " &7rejoint la partie " + (index + 1) + " (&f" + players.size() + "&7/&f" + max + "&7)");
        int min = pl.cfg("min-players", 2, 1, 32);
        if (players.size() < min) {
            EemiSpleef.msg(p, "&7En attente de &f" + (min - players.size()) + " &7joueur(s)...");
        }
        pl.sendStatus();
    }

    void addSpectator(Player p) {
        UUID id = p.getUniqueId();
        spectators.add(id);
        pl.where.put(id, this);
        p.teleport(specSpawn());
        p.setGameMode(GameMode.SPECTATOR);
        if (board != null) {
            p.setScoreboard(board);
        }
        EemiSpleef.msg(p, "&7Tu regardes la partie " + (index + 1) + ". &f/spleef leave &7pour sortir.");
    }

    /** Sortie (commande, objet, deconnexion, changement de monde). */
    void leave(Player p, boolean teleport) {
        UUID id = p.getUniqueId();
        pl.where.remove(id);
        boolean wasSpec = spectators.remove(id);
        Boolean wasAlive = players.get(id);
        if (wasAlive != null) {
            if (inGamePhase()) {
                players.remove(id);
                if (wasAlive) {
                    tell("&f" + p.getName() + " &7abandonne.");
                }
                checkEnd();
                updateBoard();
            } else if (state == State.ENDING) {
                players.remove(id);
            } else {
                players.remove(id);
                names.remove(id);
                tell("&f" + p.getName() + " &7quitte la file.");
            }
        }
        if (wasAlive == null && !wasSpec) {
            return;
        }
        if (board != null && p.getScoreboard() == board) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        clearGameItems(pl, p);
        if (teleport && pl.inWorld(p)) {
            pl.toLobby(p);
        } else if (pl.inWorld(p) && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.SPECTATOR)) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        pl.sendStatus();
    }

    // ================================================================ tableau

    static String timeText(int s) {
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    void updateBoard() {
        if (board == null) {
            return;
        }
        Objective o = board.getObjective("spleef");
        if (o != null) {
            o.unregister();
        }
        o = board.registerNewObjective("spleef", Criteria.DUMMY, EemiSpleef.c("&b&lSPLEEF"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        int line = 15;
        String phase = switch (state) {
            case GRACE -> "&eDepart dans " + grace + " s";
            case PLAYING -> melting ? "&6Fonte en cours !" : "&7Fonte dans &f" + timeText(Math.max(0, pl.cfg("max-time", 300, 30, 3600) - elapsed));
            case ENDING -> "&7Fin de partie";
            default -> "&7Attente";
        };
        o.getScore(legacy("&7Temps &f" + timeText(elapsed))).setScore(line--);
        o.getScore(legacy(phase)).setScore(line--);
        o.getScore(legacy("&7En jeu &f" + alive() + "&7/&f" + startedWith)).setScore(line--);
        o.getScore(legacy(" ")).setScore(line--);
        for (Map.Entry<UUID, Boolean> e : players.entrySet()) {
            if (line <= 1) {
                break;
            }
            String n = names.getOrDefault(e.getKey(), "?");
            o.getScore(legacy(e.getValue() ? "&f" + n : "&8&m" + n)).setScore(line--);
        }
        o.getScore(legacy("&8eemicraft.mine.fun")).setScore(Math.max(0, line));
    }

    static String legacy(String s) {
        return s.replace('&', '§');
    }
}
