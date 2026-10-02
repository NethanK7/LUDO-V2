package ludot.game;

import java.util.List;

/**
 * The numeric rules of the game, and the few places where the specification had to be interpreted.
 *
 * <p>Every constant is named after the rule it comes from, so a marker can check the behaviour of
 * the simulation against the rule book without reading any of the logic. Where the wording is
 * genuinely ambiguous (the blockade shares of Rule T-6), the interpretation is documented next to
 * the constant, so changing it is a one-line edit rather than a hunt through the code. The
 * Rule T-13 interpretation lives with the other briefing rules in {@code PieceEffects}.
 */
public final class GameRules {

    /**
     * Rule 4: "if a six is rolled for the third consecutive time, the roll is ignored, and the dice
     * passes to the next player."
     */
    public static final int MAX_CONSECUTIVE_SIXES = 3;

    /**
     * Rule T-6: a blockade is broken by moving its pieces "in their original direction by six units
     * cumulatively". Cumulatively means the six units are shared out between the pieces that move.
     */
    public static final int BLOCKADE_BREAK_UNITS = 6;

    /**
     * How the {@link #BLOCKADE_BREAK_UNITS} are shared out, indexed by the number of pieces leaving
     * the blockade minus one: one piece takes all 6, two take 4 and 2, three take 3, 2 and 1.
     *
     * <p><b>Interpretation.</b> An equal split would defeat the rule: two pieces moving 3 each from
     * the same cell in the same direction land on the same cell again and simply re-form the
     * blockade one step further on. Every share here is different, so the pieces always separate,
     * and each list still adds up to six.
     */
    public static final List<List<Integer>> BLOCKADE_BREAK_SHARES =
            List.of(List.of(6), List.of(4, 2), List.of(3, 2, 1));

    /**
     * A safety net rather than a rule. Rule&nbsp;T-7 only lets a piece enter its home straight after
     * it has captured an opponent, so an unlucky run of dice can keep a simulation going for a very
     * long time. The limit guarantees the program always terminates and says so when it stops.
     */
    public static final int MAX_ROUNDS = 2000;

    /**
     * Gridlock detection. Rule T-3 lets blocks stop every opponent, so blocks of different colours
     * on neighbouring cells can leave no legal move for anybody, and even a Rule T-6 break-up cannot
     * get past them. When no piece has changed square for this many consecutive rounds the board is
     * treated as gridlocked and the game ends with the places decided so far. Fifty rounds is far
     * longer than any temporary hold-up (a Beta briefing lasts at most five rounds).
     */
    public static final int GRIDLOCK_ROUNDS = 50;

    /**
     * Another safety net. Rules 4 and T-2 both grant extra rolls, and although a chain of captures
     * is naturally limited by the twelve opponent pieces on the board, a hard cap makes it
     * impossible for one turn to run away.
     */
    public static final int MAX_ROLLS_PER_TURN = 24;

    /** Places 1st to 3rd decide the game; the remaining player is last by elimination. */
    public static final int PLACES_TO_DECIDE = 3;

    private GameRules() {
        // Constants only.
    }
}
