package fr.eemicraft.fete;

import java.util.List;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Hors jar : les entités d'affichage (transformation au pivot) doivent couvrir exactement les boîtes de Ride.boxes(),
 * sans le pack (BlockDisplay, cube unité 0..1) et avec le pack (ItemDisplay : le client tourne le modèle de 180°
 * autour de Y juste après la transformation, puis le décale de -0,5 ; élément par élément de Models).
 */
public final class TestShow {
    public static void main(String[] args) {
        Geo g = new Geo(10);
        double worst = 0;
        int n = 0;
        java.util.Set<String> all = new java.util.HashSet<>();
        for (Defs.Purchase p : Defs.PURCHASES) all.add(p.id());
        Matrix4f clientRot = new Matrix4f().rotateY((float) Math.PI);
        for (boolean pack : new boolean[]{false, true}) {
            for (int parcel : new int[]{0, 1}) {
                boolean north = g.north(parcel);
                for (Ride r : Draw.rides(all)) {
                    for (double t : new double[]{0, 3.7, 11.2}) {
                        List<Ride.Box> boxes = r.boxes(t, pack);
                        int k = 0;
                        for (Ride.Part p : r.parts) {
                            Ride.Pose pose = p.motion.at(t);
                            double ex = g.wxd(parcel, p.pu, p.pv), ey = p.py, ez = g.wzd(parcel, p.pu, p.pv);
                            for (Ride.Cube c : p.cubes) {
                                if (!c.shown(pack)) continue;
                                Transformation tr = Show.transform(p, c, pose, north);
                                Matrix4f m = new Matrix4f().translate(tr.getTranslation()).rotate(tr.getLeftRotation())
                                        .scale(tr.getScale()).rotate(tr.getRightRotation());
                                if (c.model() == null) {
                                    worst = Math.max(worst, check(m, null, boxes.get(k++), g, parcel, ex, ey, ez));
                                    n += 8;
                                } else {
                                    for (Models.El e : Models.MODELS.get(c.model())) {
                                        Matrix4f mm = new Matrix4f(m).mul(clientRot).translate(-0.5f, -0.5f, -0.5f).scale(1 / 16f);
                                        worst = Math.max(worst, check(mm, e, boxes.get(k++), g, parcel, ex, ey, ez));
                                        n += 8;
                                    }
                                }
                            }
                        }
                        if (k != boxes.size()) throw new IllegalStateException(r.id + " : " + k + " entités pour " + boxes.size() + " boîtes");
                    }
                }
            }
        }
        System.out.printf(java.util.Locale.ROOT, "%d coins verifies, ecart max %.5f bloc%n", n, worst);
        System.exit(worst < 1e-3 ? 0 : 1);
    }

    /** Écart max entre les coins de l'entité (cube unité, ou élément de modèle sans rotation propre) et la boîte attendue. */
    static double check(Matrix4f m, Models.El e, Ride.Box b, Geo g, int parcel, double ex, double ey, double ez) {
        double worst = 0;
        for (int i = 0; i < 8; i++) {
            int bx = (i >> 2) & 1, by = (i >> 1) & 1, bz = i & 1;
            Vector3f v;
            if (e == null) v = new Vector3f(bx, by, bz);
            else {
                if (e.axis() != 'n') return 0; // éléments tournés : rotation propre du modèle, non vérifiée ici
                v = new Vector3f((float) (bx == 0 ? e.x1() : e.x2()), (float) (by == 0 ? e.y1() : e.y2()), (float) (bz == 0 ? e.z1() : e.z2()));
            }
            m.transformPosition(v);
            double wx = ex + v.x, wy = ey + v.y, wz = ez + v.z;
            double best = 1e9;
            for (double[] cc : b.corners()) {
                double x = g.wxd(parcel, cc[0], cc[2]), y = cc[1], z = g.wzd(parcel, cc[0], cc[2]);
                best = Math.min(best, Math.sqrt((x - wx) * (x - wx) + (y - wy) * (y - wy) + (z - wz) * (z - wz)));
            }
            worst = Math.max(worst, best);
        }
        return worst;
    }
}
