package ludot.game;

/**
 * The numeric rules of the game, and the few places where the specification had to be interpreted.
 *
 * <p>Every constant is named after the rule it comes from, so a marker can check the behaviour of
 * the simulation against the rule book without reading any of the logic. The last two constants are
 * the honest, documented answers to genuinely ambiguous wording; both are collected here so that
 * changing an interpretation is a one-line edit rather than a hunt through the code.
 */
public final class GameRules {

    /**
     * Rule 4: "if a six is rolled for the third consecutive time, the roll is ignored, and the dice
     * passes to the next player."
     */
    public static final int MAX_CONSECUTIVE_SIXES = 3;

    /**
     * Rule T-6: a blockade is broken by moving its pieces "in their original direction by six units
     * cumulatively". Cumulatively means the six units are shared out between the pieces that move,
     * in the same way that Rule&nbsp;T-4 divides a roll between the pieces of a block.
     */
    public static final int BLOCKADE_BREAK_UNITS = 6;

    /**
     * Rule T-13: a piece at a Beta briefing escapes to its base if "the player rolls value three
     * consecutively".
     *
     * <p><b>Interpretation.</b> The rule names the <em>value</em> three but not how many times in a
     * row it must appear; "consecutively" needs at least two rolls to mean anything, so two
     * successive threes are used here.
     */
    public static final int CONSECUTIVE_THREES_TO_LEAVE_BRIEFING = 2;

    /**
     * A safety net rather than a rule. Rule&nbsp;T-7 only lets a piece enter its home straight after
     * it has captured an opponent, so an unlucky run of dice can keep a simulation going for a very
     * long time. The limit guarantees the program always terminates and says so when it stops.
     */
    public static final int MAX_ROUNDS = 2000;

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
