package fr.eemicraft.casino;

import java.util.List;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * %eemicasino_chips%, %eemicasino_chips_raw%, %eemicasino_jackpot%, %eemicasino_rank%, %eemicasino_daily%,
 * %eemicasino_top_<n>%, %eemicasino_top_<n>_name%, %eemicasino_top_<n>_chips%,
 * %eemicasino_bigwin_<n>%, %eemicasino_bigwin_<n>_name|amount|amount_raw|game% (plus gros gains en un coup).
 */
final class Papi extends PlaceholderExpansion {
    private final EemiCasino pl;

    Papi(EemiCasino pl) {
        this.pl = pl;
    }

    @Override
    public String getIdentifier() {
        return "eemicasino";
    }

    @Override
    public String getAuthor() {
        return "EEMICRAFT";
    }

    @Override
    public String getVersion() {
        return pl.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer p, String id) {
        if (id.equals("jackpot")) {
            return U.fmt(pl.jackpot);
        }
        if (id.startsWith("bigwin_")) {
            // %eemicasino_bigwin_<n>_name|amount|game% (plus gros gains en un coup)
            String[] parts = id.split("_", 3);
            int n;
            try {
                n = Integer.parseInt(parts[1]);
            } catch (RuntimeException e) {
                return "";
            }
            String field = parts.length > 2 ? parts[2] : "";
            BigWins.Entry e = pl.bigTop.get(n);
            switch (field) {
                case "name":
                    return e == null ? "-" : e.name();
                case "amount":
                    return e == null ? "0" : U.fmt(e.amount());
                case "amount_raw":
                    return e == null ? "0" : String.valueOf(e.amount());
                case "game":
                    return e == null ? "-" : BigWins.gameName(e.game());
                case "":
                    return e == null ? "&7" + n + ". -" : "&e" + n + ". &f" + e.name() + " &7- &6" + U.fmt(e.amount());
                default:
                    return null;
            }
        }
        if (id.startsWith("top_")) {
            String[] parts = id.split("_");
            int n;
            try {
                n = Integer.parseInt(parts[1]);
            } catch (RuntimeException e) {
                return "";
            }
            List<PData> top = pl.top();
            if (n < 1 || n > top.size()) {
                return parts.length > 2 ? (parts[2].equals("chips") ? "0" : "-") : "&7" + n + ". -";
            }
            PData d = top.get(n - 1);
            if (parts.length > 2) {
                return parts[2].equals("name") ? d.name : U.fmt(d.chips);
            }
            return "&e" + n + ". &f" + d.name + " &7- &6" + U.fmt(d.chips);
        }
        if (p == null) {
            return "";
        }
        PData d = pl.offline(p.getUniqueId());
        switch (id) {
            case "chips":
                return U.fmt(d == null ? 0 : d.chips);
            case "chips_raw":
                return String.valueOf(d == null ? 0 : d.chips);
            case "rank":
                return d == null ? "-" : String.valueOf(pl.rank(p.getUniqueId()));
            case "daily":
                return d == null || pl.dailyAvailable(d) ? "&aDisponible" : "&7Pris";
            default:
                return null;
        }
    }
}
