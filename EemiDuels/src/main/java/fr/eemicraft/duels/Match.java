package fr.eemicraft.duels;

import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Un duel 1v1 en cours dans une arène. */
final class Match {

    enum State { COUNTDOWN, FIGHT, ENDED }

    final Kit kit;
    final boolean ranked;
    final Arena arena;
    final UUID[] players;
    final int[] hits = new int[2];
    State state = State.COUNTDOWN;
    int timer;
    /** Blocs posés ou créés pendant le duel (BuildUHC) : retirés à la fin. */
    final Set<Block> placed = new LinkedHashSet<>();
    final Set<UUID> spectators = new HashSet<>();

    Match(Kit kit, boolean ranked, Arena arena, UUID a, UUID b) {
        this.kit = kit;
        this.ranked = ranked;
        this.arena = arena;
        this.players = new UUID[]{a, b};
    }

    int side(UUID id) {
        return players[0].equals(id) ? 0 : players[1].equals(id) ? 1 : -1;
    }

    UUID opponent(UUID id) {
        return players[0].equals(id) ? players[1] : players[0];
    }
}
