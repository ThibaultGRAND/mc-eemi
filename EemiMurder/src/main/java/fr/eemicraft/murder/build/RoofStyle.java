package fr.eemicraft.murder.build;

/**
 * Réglages d'un toit ({@link Roof}). Identifiants SANS propriétés.
 * {@code stairs} est une palette d'identifiants d'escaliers ordonnée clair → sombre : le toit la lit en dégradé
 * (clair au faîte, sombre à l'égout, règle D7). Un seul escalier : {@code stairs("minecraft:spruce_stairs")}.
 */
public final class RoofStyle {
    public Palette stairs = Palette.of("minecraft:dark_oak_stairs");
    /** Dalle des toits à 22,5° (mode {@link #slabs}). */
    public String slab = "minecraft:dark_oak_slab";
    /** Faîte (dalle du bas posée sur la dernière rangée). */
    public String ridgeSlab = "minecraft:dark_oak_slab";
    /** Épaisseur sous les escaliers (une couche, combles creux). */
    public Mat body = Mat.of("minecraft:dark_oak_planks");
    /** Remplissage des pignons. */
    public Mat gable = Mat.of("minecraft:spruce_planks");
    /** Poteaux des pignons (pignon en retrait d'un bloc derrière eux) ; null = pignon plein dans le plan du mur. */
    public String gablePost = "minecraft:stripped_dark_oak_log";
    /** Débord (1-2, japonais 2-3). */
    public int overhang = 1;
    /** Écart des poteaux de pignon (mettre le même que la façade). */
    public int postSpacing = 5;
    /** true = pente 22,5° en dalles hautes/basses alternées (au lieu de 45° en escaliers). */
    public boolean slabs = false;

    public RoofStyle stairs(String id) { stairs = Palette.of(B.id(id)); return this; }
    public RoofStyle stairs(Palette p) { stairs = p; return this; }
    public RoofStyle slab(String id) { slab = B.id(id); return this; }
    public RoofStyle ridge(String slabId) { ridgeSlab = B.id(slabId); return this; }
    public RoofStyle body(Mat m) { body = m; return this; }
    public RoofStyle gable(Mat m) { gable = m; return this; }
    public RoofStyle gablePost(String logId) { gablePost = logId == null ? null : B.id(logId); return this; }
    public RoofStyle overhang(int o) { overhang = Math.max(0, o); return this; }
    public RoofStyle spacing(int n) { postSpacing = Math.max(2, n); return this; }
    public RoofStyle slabs(boolean b) { slabs = b; return this; }
}
