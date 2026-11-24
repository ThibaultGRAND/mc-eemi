package fr.eemicraft.casino.build;

/**
 * Tampon en mémoire sur une boîte : le builder écrit et relit ici (rapide, sans toucher au monde),
 * puis {@link #flush()} pose l'état final de chaque case une seule fois dans la cible.
 * Hors de la boîte : écrit et lu directement dans la cible.
 */
public final class MemSink implements Sink {
    private final Sink target;
    private final int x0, y0, z0, nx, ny, nz;
    private final String[] a;

    public MemSink(Sink target, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.target = target;
        x0 = Math.min(x1, x2);
        y0 = Math.min(y1, y2);
        z0 = Math.min(z1, z2);
        nx = Math.abs(x2 - x1) + 1;
        ny = Math.abs(y2 - y1) + 1;
        nz = Math.abs(z2 - z1) + 1;
        a = new String[nx * ny * nz];
    }

    private int idx(int x, int y, int z) {
        int i = x - x0, j = y - y0, k = z - z0;
        if (i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz) return -1;
        return (j * nz + k) * nx + i;
    }

    @Override
    public void set(int x, int y, int z, String state) {
        int i = idx(x, y, z);
        if (i < 0) target.set(x, y, z, state);
        else a[i] = state == null ? B.AIR : state;
    }

    @Override
    public String get(int x, int y, int z) {
        int i = idx(x, y, z);
        if (i < 0 || a[i] == null) return target.get(x, y, z);
        return a[i];
    }

    /** Pose dans la cible toutes les cases écrites ; renvoie leur nombre. */
    public int flush() {
        int n = 0;
        for (int j = 0; j < ny; j++)
            for (int k = 0; k < nz; k++)
                for (int i = 0; i < nx; i++) {
                    String s = a[(j * nz + k) * nx + i];
                    if (s != null) {
                        target.set(x0 + i, y0 + j, z0 + k, s);
                        n++;
                    }
                }
        return n;
    }
}
