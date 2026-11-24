package fr.eemicraft.sheepwars.gen.build;

/**
 * Destination des blocs. Tout le cœur de la bibliothèque n'écrit que par cette interface :
 * {@link RecordingSink} (mémoire, tests hors serveur) ou {@code BukkitSink} (monde Paper).
 *
 * <p>{@code state} est une chaîne d'état de bloc vanilla, par exemple
 * {@code "minecraft:oak_stairs[facing=north,half=top]"} ; {@code "minecraft:air"} pour vide.
 */
public interface Sink {
    void set(int x, int y, int z, String state);

    /** État du bloc (au minimum l'identifiant, "minecraft:air" si vide). */
    String get(int x, int y, int z);

    /** Raccourci : pose un Mat (palette, bloc fixe…) évalué à cette position. */
    default void set(int x, int y, int z, Mat m) {
        if (m != null) set(x, y, z, m.at(x, y, z));
    }

    default boolean isAir(int x, int y, int z) {
        return B.isAir(get(x, y, z));
    }

    /** Vrai si le bloc est « plein » pour poser dessus (heuristique sur le nom, voir {@link B#isSolid}). */
    default boolean isSolid(int x, int y, int z) {
        return B.isSolid(get(x, y, z));
    }

    /** Sink décalé : les coordonnées données sont relatives à (dx, dy, dz). */
    default Sink offset(int dx, int dy, int dz) {
        Sink self = this;
        return new Sink() {
            public void set(int x, int y, int z, String s) { self.set(x + dx, y + dy, z + dz, s); }
            public String get(int x, int y, int z) { return self.get(x + dx, y + dy, z + dz); }
        };
    }

    /**
     * Adaptateur pour un builder existant qui a déjà une méthode {@code set(x, y, z, String)} :
     * {@code Sink s = Sink.of(this::set, this::get);} (get peut être null : tout est alors vu comme de l'air).
     */
    static Sink of(Setter setter, Getter getter) {
        return new Sink() {
            public void set(int x, int y, int z, String s) { setter.set(x, y, z, s); }
            public String get(int x, int y, int z) { return getter == null ? B.AIR : getter.get(x, y, z); }
        };
    }

    @FunctionalInterface interface Setter { void set(int x, int y, int z, String state); }
    @FunctionalInterface interface Getter { String get(int x, int y, int z); }
}
