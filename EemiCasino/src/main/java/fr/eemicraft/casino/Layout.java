package fr.eemicraft.casino;

/** Positions des postes du casino (monde "casino"). */
final class Layout {
    static final int FLOOR = 100;
    /** Version de la construction : changer pour reconstruire au demarrage. */
    static final int BUILD_VERSION = 6;

    static final int SLOT_Z = -29;
    static final int[] SLOT_X = {-21, -18, -15, -12, -9, -6, -3, 0, 3, 6, 9, 12, 15, 18, 21};
    static final int VP_X = 29;
    static final int[] VP_Z = {-24, -21, -18, 12, 15, 18};
    /** Tables de blackjack : x -21..-19, croupier en x -21.5 (bloc -22). */
    static final int BJ_X1 = -21;
    static final int[] BJ_Z = {-14, -2, 10};
    /** Tables de poker : x 17..23, centre x 20. */
    static final int[] PK_Z = {-12, 4};
    static final int COUNTER_Z = 23;

    private Layout() {
    }
}
