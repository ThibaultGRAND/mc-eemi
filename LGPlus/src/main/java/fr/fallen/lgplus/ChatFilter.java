package fr.fallen.lgplus;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Filter;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Réduction du spam de LoupGarou.
 * <ul>
 * <li>Annonces publiques (Bukkit.broadcastMessage) : filtrées dans LGPlus#onBroadcast via {@link #broadcastVerdict}.</li>
 * <li>Messages envoyés directement à un joueur (Player#sendMessage) et titres : aucun événement Bukkit,
 * donc un petit gestionnaire Netty ajouté au tuyau réseau de chaque joueur (avant « packet_handler »)
 * répare/retire les paquets de chat système et de titre. Tout est en réflexion et tolérant aux erreurs :
 * en cas de problème le paquet passe tel quel.</li>
 * <li>Logs « [DEBUG] » du plugin LoupGarou : filtre java.util.logging.</li>
 * </ul>
 */
final class ChatFilter {
    static final String HANDLER = "lgplus_chat";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** Annonces publiques de LoupGarou sans intérêt (la barre de boss et le scoreboard donnent déjà l'info). */
    private static final List<String> BROADCAST_SPAM = List.of(
            "Distribution des rôles en cours",
            "Le village s'endort",
            "Phase de nuit :",
            "L'aube se lève",
            "Le soleil se lève sur le village",
            "Phase de discussion !",
            "C'est le moment de débattre",
            "Il est temps de voter pour éliminer",
            "/vote <joueur> pour voter",
            "pour voter !",
            "Joueurs vivants :",
            "Loups restants :",
            "Partie réinitialisée !",
            "Le chasseur a 20 secondes pour utiliser",
            "Demarrage auto dans ");

    /** Messages directs répétés chaque nuit / doublons des menus LGPlus. */
    private static final List<String> DIRECT_SPAM = List.of(
            "=== PHASE DES LOUPS-GAROUS ===", "Choisissez votre victime : /lg tuer",
            "=== PHASE DE LA VOYANTE ===", "Découvrez un rôle : /lg voir",
            "=== PHASE DU GARDE ===", "Protégez quelqu'un : /lg proteger", "Vous pouvez vous protéger une fois par partie",
            "=== PHASE DE LA SORCIÈRE ===", "Soigner : /lg soigner", "Empoisonner : /lg empoisonner",
            "Vous veillez en silence cette nuit", "En cas de mort, utilisez /lg vengeance",
            "Votre protection ancestrale est deja consommee", "Votre premiere attaque des loups sera annulee",
            "=== PHASE DE CUPIDON ===", "Vous avez deja forme un couple", "Vous ne pouvez utiliser votre pouvoir que la premiere nuit",
            "Liez deux joueurs : /lg lier",
            "=== PHASE DE LA PETITE FILLE ===", "Espionnez un joueur : /lg espionner",
            "=== PHASE DE ASSASSIN ===", "Vous avez deja utilise votre elimination unique", "Vous pouvez tuer une cible une fois : /lg assassiner",
            "Vous dormez paisiblement", "Observez le chat pour voir ce qui se passe",
            "Chattez avec les autres morts ou observez la suite", "Vous êtes maintenant en mode spectateur.",
            "Les joueurs morts doivent rester en mode spectateur",
            "VOUS ÊTES LE MAIRE", "Vous avez été élu maire du village", "💡 Votre rôle : ",
            "En cas d'égalité, vous choisirez qui éliminer", "VOUS ÊTES LE NOUVEAU MAIRE",
            "DÉCISION DU MAIRE", "Utilisez : /vote <joueur>");

    /** Rôle cité entre parenthèses dans « /lg aide » → nom d'enum LoupGarou. */
    private static final Map<String, String> HELP_ROLES = Map.of(
            "(Loup-Garou)", "WEREWOLF", "(Voyante)", "SEER", "(Garde)", "GUARD", "(Sorcière)", "WITCH",
            "(Cupidon)", "CUPID", "(Petite Fille)", "LITTLE_GIRL", "(Assassin)", "ASSASSIN", "(Chasseur mort)", "HUNTER");

    // État de la partie, mis à jour par le thread principal, lu par les threads réseau
    volatile boolean gameRunning;
    volatile Set<String> presentRoles = Set.of();
    final Set<UUID> concerned = ConcurrentHashMap.newKeySet();
    volatile boolean repairEnabled = true;
    volatile boolean directEnabled = true;

    private final java.util.logging.Logger log;
    private volatile boolean packetBroken;
    private final Map<Class<?>, PacketShape> shapes = new ConcurrentHashMap<>();
    private Method asAdventure;
    private Method asVanilla;

    ChatFilter(Logger log) {
        this.log = log;
    }

    static String plain(String legacy) {
        return legacy.replaceAll("§.", "").trim();
    }

    static String repairLegacy(String legacy) {
        return Mojibake.repair(legacy);
    }

    static Component repair(Component c) {
        String legacy = LEGACY.serialize(c);
        String fixed = Mojibake.repair(legacy);
        return fixed.equals(legacy) ? c : LEGACY.deserialize(fixed);
    }

    private static boolean isSeparator(String plain) {
        return plain.length() >= 20 && plain.chars().allMatch(ch -> ch == '=' || ch == '-' || ch == '─');
    }

    /** true = l'annonce publique de LoupGarou doit être supprimée. Le texte doit déjà être réparé. */
    boolean dropBroadcast(String legacy) {
        String p = plain(legacy);
        // préfixe « [Loup-Garou] » seul = ligne vide
        String body = p.replaceFirst("^\\[[^\\]]*\\]\\s*", "");
        if (body.isEmpty() || isSeparator(body)) return true;
        for (String s : BROADCAST_SPAM) {
            if (p.contains(s)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- messages directs (Netty)

    /** Décision pour un message direct. Retourne null pour le supprimer, sinon le texte (éventuellement réparé). */
    private String direct(UUID player, String legacy, boolean overlay, ChannelState st) {
        String fixed = this.repairEnabled ? Mojibake.repair(legacy) : legacy;
        if (overlay || !this.directEnabled) return fixed;
        String p = plain(fixed);
        boolean inLg = this.concerned.contains(player);
        if (st.listMode) {
            if (p.matches("-\\s+\\S+")) return null;
            st.listMode = false;
        }
        if (!inLg) return fixed;
        if (p.isEmpty() || isSeparator(p)) return null;
        if (p.startsWith("Cibles possibles")) {
            st.listMode = true;
            return null;
        }
        if (p.endsWith("Description :")) return null;
        if (p.matches(".*Joueurs vivants : .*[A-Za-z_].*")) return null;
        for (String s : DIRECT_SPAM) {
            if (p.contains(s)) return null;
        }
        if (this.gameRunning && p.startsWith("/lg ")) {
            for (Map.Entry<String, String> e : HELP_ROLES.entrySet()) {
                if (p.contains(e.getKey()) && !this.presentRoles.contains(e.getValue())) return null;
            }
        }
        return fixed;
    }

    /** Titres : réparés ; ceux de LoupGarou (mort, victoire) retirés pour les joueurs hors du jeu. */
    private String title(UUID player, String legacy) {
        String fixed = this.repairEnabled ? Mojibake.repair(legacy) : legacy;
        if (!this.concerned.contains(player)) {
            String p = plain(fixed);
            if (p.startsWith("💀") || p.contains("VICTOIRE D") || p.startsWith("a été ")
                    || p.contains("loups-garous ont été éliminés") || p.contains("Les loups-garous dominent")
                    || p.contains("C'est votre rôle")) {
                return null;
            }
        }
        return fixed;
    }

    // ---------------------------------------------------------------- logger LoupGarou

    static void muteDebug(Logger logger) {
        Filter previous = logger.getFilter();
        logger.setFilter(rec -> {
            String m = rec.getMessage();
            if (m != null && (m.startsWith("[DEBUG]") || m.contains("] [DEBUG]"))) return false;
            return previous == null || previous.isLoggable(rec);
        });
    }

    // ---------------------------------------------------------------- injection Netty

    void inject(Player player) {
        if (this.packetBroken) return;
        try {
            Channel ch = channelOf(player);
            if (ch == null) return;
            if (ch.pipeline().get(HANDLER) != null) ch.pipeline().remove(HANDLER);
            Handler h = new Handler(player.getUniqueId());
            if (ch.pipeline().get("packet_handler") != null) ch.pipeline().addBefore("packet_handler", HANDLER, h);
            else ch.pipeline().addLast(HANDLER, h);
        } catch (Throwable t) {
            this.disable("injection impossible", t);
        }
    }

    void uninject(Player player) {
        try {
            Channel ch = channelOf(player);
            if (ch != null && ch.pipeline().get(HANDLER) != null) ch.pipeline().remove(HANDLER);
        } catch (Throwable ignored) {
        }
    }

    private void disable(String why, Throwable t) {
        if (!this.packetBroken) {
            this.packetBroken = true;
            this.log.warning("Filtre des messages directs désactivé (" + why + ") : " + t);
        }
    }

    private static Channel channelOf(Player player) throws ReflectiveOperationException {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object listener = fieldByTypeName(handle, "ServerGamePacketListenerImpl");
        if (listener == null) return null;
        Object connection = fieldByTypeName(listener, "net.minecraft.network.Connection");
        if (connection == null) return null;
        for (Class<?> c = connection.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !Channel.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                return (Channel) f.get(connection);
            }
        }
        return null;
    }

    private static Object fieldByTypeName(Object owner, String typeName) throws IllegalAccessException {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String n = f.getType().getName();
                if (!(n.equals(typeName) || n.endsWith("." + typeName))) continue;
                f.setAccessible(true);
                return f.get(owner);
            }
        }
        return null;
    }

    private static final class ChannelState {
        boolean listMode;
    }

    /** Description réflexive d'un paquet record contenant un composant texte. */
    private static final class PacketShape {
        RecordComponent[] comps;
        Constructor<?> ctor;
        int textIndex = -1;
        boolean adventure;
        int overlayIndex = -1;
    }

    private PacketShape shape(Class<?> cls) {
        return this.shapes.computeIfAbsent(cls, k -> {
            PacketShape s = new PacketShape();
            if (!k.isRecord()) return s;
            try {
                s.comps = k.getRecordComponents();
                Class<?>[] types = new Class<?>[s.comps.length];
                for (int i = 0; i < s.comps.length; i++) {
                    types[i] = s.comps[i].getType();
                    String n = types[i].getName();
                    if (s.textIndex < 0 && n.equals("net.minecraft.network.chat.Component")) s.textIndex = i;
                    if (n.equals("net.kyori.adventure.text.Component")) {
                        s.textIndex = i;
                        s.adventure = true;
                    }
                    if (types[i] == boolean.class && s.comps[i].getName().equals("overlay")) s.overlayIndex = i;
                }
                s.ctor = k.getDeclaredConstructor(types);
                s.ctor.setAccessible(true);
                for (RecordComponent rc : s.comps) rc.getAccessor().setAccessible(true);
            } catch (Throwable t) {
                s.textIndex = -1;
            }
            return s;
        });
    }

    private Component toAdventure(Object nms) throws ReflectiveOperationException {
        if (this.asAdventure == null) {
            Class<?> pa = Class.forName("io.papermc.paper.adventure.PaperAdventure", true, nms.getClass().getClassLoader());
            for (Method m : pa.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) continue;
                String param = m.getParameterTypes()[0].getName();
                if (m.getName().equals("asAdventure") && param.equals("net.minecraft.network.chat.Component")) this.asAdventure = m;
                if (m.getName().equals("asVanilla") && param.equals("net.kyori.adventure.text.Component")
                        && m.getReturnType().getName().equals("net.minecraft.network.chat.Component")) this.asVanilla = m;
            }
            if (this.asAdventure == null || this.asVanilla == null) throw new NoSuchMethodException("PaperAdventure.asAdventure/asVanilla");
        }
        return (Component) this.asAdventure.invoke(null, nms);
    }

    /** Traite un paquet sortant : le même objet, un nouveau paquet, ou null pour le supprimer. */
    private Object process(UUID player, Object msg, ChannelState st) throws Throwable {
        String name = msg.getClass().getSimpleName();
        boolean chat = name.equals("ClientboundSystemChatPacket");
        boolean title = name.equals("ClientboundSetTitleTextPacket") || name.equals("ClientboundSetSubtitleTextPacket");
        if (!chat && !title) return msg;
        PacketShape s = this.shape(msg.getClass());
        if (s.textIndex < 0) return msg;
        Object[] values = new Object[s.comps.length];
        for (int i = 0; i < values.length; i++) values[i] = s.comps[i].getAccessor().invoke(msg);
        Object raw = values[s.textIndex];
        if (raw == null) return msg;
        Component comp = s.adventure ? (Component) raw : this.toAdventure(raw);
        String legacy = LEGACY.serialize(comp);
        boolean overlay = s.overlayIndex >= 0 && (Boolean) values[s.overlayIndex];
        String out = chat ? this.direct(player, legacy, overlay, st) : this.title(player, legacy);
        if (out == null) return null;
        if (out.equals(legacy)) return msg;
        Component fixed = LEGACY.deserialize(out);
        values[s.textIndex] = s.adventure ? fixed : this.asVanilla.invoke(null, fixed);
        return s.ctor.newInstance(values);
    }

    private final class Handler extends ChannelOutboundHandlerAdapter {
        private final UUID player;
        private final ChannelState state = new ChannelState();

        Handler(UUID player) {
            this.player = player;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Object out = msg;
            if (!ChatFilter.this.packetBroken) {
                try {
                    out = ChatFilter.this.process(this.player, msg, this.state);
                } catch (Throwable t) {
                    ChatFilter.this.disable("erreur de traitement", t);
                    out = msg;
                }
            }
            if (out == null) {
                promise.trySuccess();
                return;
            }
            super.write(ctx, out, promise);
        }
    }
}
