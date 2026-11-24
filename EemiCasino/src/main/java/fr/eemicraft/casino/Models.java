package fr.eemicraft.casino;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Modeles 3D du pack de textures (namespace eemicraft, dossier casino/).
 * Les entites ItemDisplay du jeu tournent le modele de 180 degres autour de Y avant de l'afficher :
 * avec modeles-flip (vrai par defaut) on annule ce demi-tour par la rotation de droite, si bien que
 * tous les calculs se font dans le repere du modele (nord = -z, haut de l'image de la face du dessus = nord,
 * face avant d'un modele debout = sud).
 */
final class Models {
    static final String[] SUIT_FILES = {"pique", "coeur", "carreau", "trefle"};
    static final String[] RANK_FILES = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "v", "d", "r", "a"};

    private Models() {
    }

    static boolean on(EemiCasino pl) {
        return pl.getConfig().getBoolean("modeles-3d", false);
    }

    static String card(int c) {
        return "carte/" + SUIT_FILES[Cards.suit(c)] + "_" + RANK_FILES[Cards.rank(c)];
    }

    static ItemStack item(String path) {
        return apply(new ItemStack(Material.PAPER), path);
    }

    static ItemStack apply(ItemStack it, String path) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("eemicraft", "casino/" + path));
            it.setItemMeta(meta);
        }
        return it;
    }

    static Quaternionf right(EemiCasino pl) {
        return pl.getConfig().getBoolean("modeles-flip", true) ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf();
    }

    static Quaternionf rotY(double deg) {
        return new Quaternionf().rotateY((float) Math.toRadians(deg));
    }

    static void pose(EemiCasino pl, ItemDisplay d, Quaternionf left, float scale, int interpolation) {
        if (d == null || !d.isValid()) {
            return;
        }
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(interpolation);
        d.setTransformation(new Transformation(new Vector3f(), left, new Vector3f(scale, scale, scale), right(pl)));
    }

    /** Entite d'affichage d'un modele (non sauvegardee, taguee pour etre retiree avec les autres entites du casino). */
    static ItemDisplay spawn(EemiCasino pl, Location l, String model, Quaternionf left, float scale) {
        return pl.spawnDisplay(l, d -> {
            d.setItemStack(item(model));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), left, new Vector3f(scale, scale, scale), right(pl)));
            d.setViewRange(1.0f);
            d.setBrightness(new Display.Brightness(15, 15));
        });
    }
}
