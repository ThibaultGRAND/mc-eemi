package fr.eemicraft.paintball;

import fr.eemicraft.paintball.build.bukkit.BukkitSink;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Terrain de speedball : constantes partagees avec le jeu et pose dans le monde.
 * La construction elle-meme est dans {@link Speedball} (sans Bukkit, testable hors serveur : tools/SpeedballPreview).
 * Axe long = x (Rouge a l'ouest, x negatif ; Bleu a l'est), largeur = z.
 */
final class MapBuilder {
    /** Sol de reference (on marche en FY + 1 sur les bases ; le relief monte au-dessus, jamais en dessous). */
    static final int FY = Speedball.FY;
    /** Demi-longueur et demi-largeur historiques du terrain (1.x). */
    static final int HX = Speedball.HX;
    static final int HZ = Speedball.HZ;
    /** Ligne du filet : rien n'est peint au-dela. */
    static final int NX = Speedball.NX;
    static final int NZ = Speedball.NZ;
    /** Sol de la salle d'attente. */
    static final int WY = Speedball.WY;

    /**
     * Blocs que la peinture peut recouvrir : sol (herbe, terres, boue, chemins), couverts (foin, caisses, pneus,
     * sacs de sable, conteneurs, bobines, pieux, planches). Jamais les filets, haies, bases (couleurs d'equipe),
     * gradins ni la salle d'attente. Chaque bloc peint garde son etat d'origine et est remis en fin de partie.
     */
    static final Set<Material> PAINTABLE = Set.of(
        // speedball
        Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.PODZOL, Material.DIRT, Material.DIRT_PATH,
        Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.MUD, Material.PACKED_MUD, Material.MUD_BRICKS,
        Material.HAY_BLOCK, Material.SPRUCE_PLANKS, Material.SPRUCE_LOG, Material.STRIPPED_OAK_LOG,
        Material.BLACK_CONCRETE, Material.ORANGE_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.POLISHED_ANDESITE,
        Material.WHITE_CONCRETE, Material.GRAY_CONCRETE, Material.BLACK_WOOL, Material.WHITE_WOOL,
        // chantier (1.4.0)
        Material.GRAVEL, Material.SAND, Material.ANDESITE, Material.SMOOTH_STONE, Material.LIGHT_GRAY_CONCRETE,
        Material.BRICKS, Material.LIGHT_GRAY_WOOL, Material.YELLOW_TERRACOTTA, Material.GREEN_TERRACOTTA,
        Material.STRIPPED_SPRUCE_LOG, Material.YELLOW_CONCRETE,
        // western (1.4.0)
        Material.RED_SAND, Material.TERRACOTTA, Material.SMOOTH_RED_SANDSTONE, Material.COBBLESTONE,
        // poteaux des barricades du chantier, enseigne du western
        Material.STRIPPED_BIRCH_LOG, Material.BIRCH_PLANKS,
        // terrain de tournoi (1.6.0) : gazon synthetique raye (GREEN_TERRACOTTA est deja dans la liste du chantier :
        // Set.of refuse les doublons et ferait planter le chargement de la classe)
        Material.GREEN_CONCRETE, Material.LIME_TERRACOTTA);

    /**
     * Gonflables du terrain de tournoi (laine : la peinture la passe en laine de l'equipe qui tire). Peignables
     * SEULEMENT sur le tournoi : sur le speedball et le western, la laine rouge / bleue est la bande d'equipe des
     * barricades de base, qui ne doit pas changer de couleur.
     */
    static final Set<Material> PAINTABLE_TOURNOI = Set.of(
        Material.RED_WOOL, Material.BLUE_WOOL, Material.PINK_WOOL, Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL);

    /** Bloc peignable sur la petite map {@code smallMap}. */
    static boolean paintable(String smallMap, Material m) {
        return PAINTABLE.contains(m) || ("tournoi".equals(smallMap) && PAINTABLE_TOURNOI.contains(m));
    }

    private final EemiPaintball pl;
    private final World w;
    int ops;

    MapBuilder(EemiPaintball pl, World w) {
        this.pl = pl;
        this.w = w;
    }

    /**
     * Pose la petite map {@code id} (speedball, chantier, western, tournoi) au centre du monde. Le speedball pose aussi
     * la salle d'attente ; pour une map a theme, {@code waiting} la (re)construit en plus (inutile a un simple
     * changement de map au lancement d'une partie : la salle ne bouge pas).
     */
    void build(String id, boolean waiting) {
        BukkitSink s = new BukkitSink(w, pl.getLogger());
        SmallMaps.buildField(id, s);
        if (waiting && !id.equals("speedball")) {
            new Speedball(s).waitingOnly();
        }
        ops = (int) s.operations();
        if (!s.invalidStates().isEmpty()) {
            pl.getLogger().warning("Map " + id + " : " + s.invalidStates().size() + " etat(s) de bloc refuse(s) : "
                + s.invalidStates());
        }
    }
}
