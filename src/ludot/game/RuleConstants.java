package ludot.game;

import java.util.List;

/** The numbers from the rule book, in one place. */
public final class RuleConstants {

    public static final int SIXES_THAT_END_A_TURN = 3;

    // T-6: unequal shares (4+2, 3+2+1), so the leaving pieces never land together again.
    public static final List<List<Integer>> BLOCKADE_BREAK_SHARES =
            List.of(List.of(6), List.of(4, 2), List.of(3, 2, 1));

    public static final int MAX_ROUNDS = 2000;

    // Not a rule: blocks can freeze the board for good, so stop after 50 rounds with no move.
    public static final int GRIDLOCK_ROUNDS = 50;

    public static final int PLACES_TO_DECIDE = 3;

    private RuleConstants() {
    }
}
