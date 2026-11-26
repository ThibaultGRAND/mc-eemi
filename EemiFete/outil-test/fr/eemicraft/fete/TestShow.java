package fr.eemicraft.fete;

import java.util.List;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Hors jar : les BlockDisplay (transformation au pivot) doivent couvrir exactement les boîtes de Ride.boxes(). */
public final class TestShow {
    public static void main(String[] args) {
        Geo g = new Geo(10);
        double worst = 0;
        int n = 0;
        for (int parcel : new int[]{0, 1}) {
            boolean north = g.north(parcel);
            java.util.Set<String> all = new java.util.HashSet<>();
            for (Defs.Purchase p : Defs.PURCHASES) all.add(p.id());
            for (Ride r : Draw.rides(all)) {
                for (double t : new double[]{0, 3.7, 11.2}) {
                    List<Ride.Box> boxes = r.boxes(t);
                    int k = 0;
                    for (Ride.Part p : r.parts) {
                        Ride.Pose pose = p.motion.at(t);
                        double ex = g.wxd(parcel, p.pu, p.pv), ey = p.py, ez = g.wzd(parcel, p.pu, p.pv);
                        for (Ride.Cube c : p.cubes) {
                            Transformation tr = Show.transform(p, c, pose, north);
                            Matrix4f m = new Matrix4f().translate(tr.getTranslation()).rotate(tr.getLeftRotation())
                                    .scale(tr.getScale()).rotate(tr.getRightRotation());
                            Ride.Box b = boxes.get(k++);
                            // coins du cube unité -> monde ; chacun doit être un coin de la boîte attendue
                            for (int i = 0; i < 8; i++) {
                                Vector3f v = m.transformPosition(new Vector3f((i >> 2) & 1, (i >> 1) & 1, i & 1));
                                double wx = ex + v.x, wy = ey + v.y, wz = ez + v.z;
                                double best = 1e9;
                                for (double[] cc : b.corners()) {
                                    double x = g.wxd(parcel, cc[0], cc[2]), y = cc[1], z = g.wzd(parcel, cc[0], cc[2]);
                                    best = Math.min(best, Math.sqrt((x - wx) * (x - wx) + (y - wy) * (y - wy) + (z - wz) * (z - wz)));
                                }
                                worst = Math.max(worst, best);
                                n++;
                            }
                        }
                    }
                }
            }
        }
        System.out.printf(java.util.Locale.ROOT, "%d coins verifies, ecart max %.5f bloc%n", n, worst);
        System.exit(worst < 1e-3 ? 0 : 1);
    }
}
