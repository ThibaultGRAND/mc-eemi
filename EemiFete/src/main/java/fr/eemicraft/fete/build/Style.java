package fr.eemicraft.fete.build;

/**
 * Réglages d'une façade ({@link Facade}). Tous les champs sont publics et ont une valeur par défaut
 * (maison médiévale pierre + colombage sombre) ; les setters chaînables servent à écrire court :
 * {@code new Style().wall(murs).post("minecraft:stripped_spruce_log").spacing(4)}.
 *
 * <p>Pour les escaliers / dalles / trappes / portes, donner l'identifiant SANS propriétés : la façade
 * pose elle-même facing / half / type. Les Mat (mur, socle, poteaux, angles, sols) peuvent être des
 * palettes ou des dégradés.
 */
public final class Style {
    /** Remplissage du mur (plan en retrait). */
    public Mat wall = Mat.of("minecraft:stone_bricks");
    /** Socle (rang du bas + fondation descendue jusqu'au sol). Plus sombre que le mur. */
    public Mat plinth = Mat.of("minecraft:cobbled_deepslate");
    /** Escalier du socle : marche devant la porte. */
    public String plinthStairs = "minecraft:cobbled_deepslate_stairs";
    /** Poteaux en saillie (bûche : l'axe est posé par la façade). */
    public String post = "minecraft:stripped_dark_oak_log";
    /** Angles (chaînage) ; null = comme les poteaux. */
    public Mat corner = null;
    /** Vitre des fenêtres. */
    public String pane = "minecraft:glass_pane";
    /** Appui de fenêtre (escalier retourné en saillie). */
    public String sillStairs = "minecraft:stone_brick_stairs";
    /** Linteau (dalle du haut dans l'embrasure). */
    public String lintelSlab = "minecraft:stone_brick_slab";
    /** Volets (trappes ouvertes) ; null = aucun. */
    public String shutter = "minecraft:spruce_trapdoor";
    /** Bandeau à chaque plancher (escalier retourné) ; null = aucun. */
    public String bandStairs = "minecraft:stone_brick_stairs";
    /** Corniche sous le toit (escalier retourné). */
    public String corniceStairs = "minecraft:dark_oak_stairs";
    /** Porte (deux moitiés posées par la façade). */
    public String door = "minecraft:spruce_door";
    /** Planchers et plafond (utilisé par {@link Facade#building}). */
    public Mat floor = Mat.of("minecraft:spruce_planks");

    /** Écart entre poteaux (4-6). Les travées sont réparties au plus juste (±1). */
    public int postSpacing = 5;
    /** Hauteur d'étage (4-5) : un plancher + bandeau tous les {@code storey} blocs. */
    public int storey = 4;
    /** Profondeur max de la fondation sous le socle (descend jusqu'à un bloc plein). */
    public int groundDepth = 16;
    /** Part des fenêtres avec volets (décidé par fenêtre, pas par bloc). */
    public double shutterChance = 0.6;
    /** Part des travées sans fenêtre (variation du module). */
    public double blankBay = 0.15;
    /** Pilastres intérieurs au droit des poteaux (cassent les pans intérieurs). */
    public boolean innerPosts = true;
    /** Graine des décisions par fenêtre / travée. */
    public long seed = 1;

    public Style wall(Mat m) { wall = m; return this; }
    public Style plinth(Mat m) { plinth = m; return this; }
    public Style plinthStairs(String id) { plinthStairs = B.id(id); return this; }
    public Style post(String id) { post = B.id(id); return this; }
    public Style corner(Mat m) { corner = m; return this; }
    public Style pane(String id) { pane = B.id(id); return this; }
    public Style sill(String stairsId) { sillStairs = B.id(stairsId); return this; }
    public Style lintel(String slabId) { lintelSlab = B.id(slabId); return this; }
    public Style shutter(String trapdoorId) { shutter = trapdoorId == null ? null : B.id(trapdoorId); return this; }
    public Style band(String stairsId) { bandStairs = stairsId == null ? null : B.id(stairsId); return this; }
    public Style cornice(String stairsId) { corniceStairs = stairsId == null ? null : B.id(stairsId); return this; }
    public Style door(String doorId) { door = B.id(doorId); return this; }
    public Style floor(Mat m) { floor = m; return this; }
    public Style spacing(int n) { postSpacing = Math.max(2, n); return this; }
    public Style storey(int n) { storey = Math.max(3, n); return this; }
    public Style shutters(double chance) { shutterChance = chance; return this; }
    public Style blankBays(double chance) { blankBay = chance; return this; }
    public Style innerPosts(boolean b) { innerPosts = b; return this; }
    public Style seed(long s) { seed = s; return this; }
}
