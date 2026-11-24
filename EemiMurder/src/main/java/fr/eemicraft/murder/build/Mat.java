package fr.eemicraft.murder.build;

/**
 * « Matériau » : donne l'état de bloc à poser à une position. Un bloc fixe ({@link #of}),
 * une {@link Palette} (variantes en grappes) ou un dégradé ({@link Palette#vertical}).
 */
@FunctionalInterface
public interface Mat {
    String at(int x, int y, int z);

    static Mat of(String state) {
        String s = B.id(state);
        return (x, y, z) -> s;
    }

    Mat AIR = (x, y, z) -> B.AIR;
}
