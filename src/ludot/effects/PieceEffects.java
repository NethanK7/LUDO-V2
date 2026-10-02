package ludot.effects;

/**
 * The temporary Alpha / Beta effects carried by a single piece (Rules T-12 and T-13).
 *
 * <p>Both effects are described in the specification as lasting "the next four rounds", so instead
 * of storing absolute round numbers and doing arithmetic at every read, this class stores a simple
 * countdown for each effect. {@link #onRoundCompleted()} is called once per piece at the end of
 * every round and is the only place where time passes - which makes the behaviour easy to follow
 * and impossible to get wrong by forgetting to compare against the current round.
 *
 * <p>An effect always begins part-way through a round, when the piece is teleported. That partial
 * round does not count: the effect then lasts the <em>next</em> four full rounds, as the rules say.
 */
public final class PieceEffects {

    /** Rules T-12 and T-13 both last "the next four rounds". */
    public static final int EFFECT_DURATION_IN_ROUNDS = 4;

    /** Rule T-13: the value whose repetition sends a briefed piece back to its base. */
    public static final int BRIEFING_ESCAPE_ROLL = 3;

    /**
     * Rule T-13: a piece at a Beta briefing is sent to its base if "the player rolls value three
     * consecutively".
     *
     * <p><b>Interpretation.</b> The rule names the <em>value</em> three but not how many times in a
     * row it must appear; "consecutively" needs at least two rolls to mean anything, so two
     * successive threes are used. Only rolls made while the piece is at the briefing count.
     */
    public static final int CONSECUTIVE_ESCAPE_ROLLS_TO_LEAVE_BRIEFING = 2;

    private SpeedModifier speedModifier = SpeedModifier.NORMAL;
    private int speedRoundsRemaining;
    private int briefingRoundsRemaining;
    private boolean speedAppliedThisRound;
    private boolean briefingBegunThisRound;
    private int consecutiveEscapeRolls;

    /** Rule T-12: the piece was energised or made sick by the Alpha aura. */
    public void applyAlphaAura(SpeedModifier modifier) {
        this.speedModifier = modifier;
        this.speedRoundsRemaining = EFFECT_DURATION_IN_ROUNDS;
        this.speedAppliedThisRound = true;
    }

    /** Rule T-13: the piece is sent to a briefing at Beta and cannot move for four rounds. */
    public void beginBriefing() {
        this.briefingRoundsRemaining = EFFECT_DURATION_IN_ROUNDS;
        this.briefingBegunThisRound = true;
        this.consecutiveEscapeRolls = 0;
    }

    /** Rule T-13: every roll of the owning player is shown to the piece while it is briefed. */
    public void observeRoll(int rollValue) {
        if (!isAttendingBriefing()) {
            return;
        }
        consecutiveEscapeRolls = rollValue == BRIEFING_ESCAPE_ROLL ? consecutiveEscapeRolls + 1 : 0;
    }

    /** True once the player has rolled enough consecutive threes to send this piece to base. */
    public boolean mustLeaveBriefingForBase() {
        return isAttendingBriefing()
                && consecutiveEscapeRolls >= CONSECUTIVE_ESCAPE_ROLLS_TO_LEAVE_BRIEFING;
    }

    /** True while Rule T-13 forbids this piece from moving. */
    public boolean isAttendingBriefing() {
        return briefingRoundsRemaining > 0;
    }

    /** Turns a dice face value into the distance this particular piece travels. */
    public int adjustRoll(int rollValue) {
        return activeSpeedModifier().apply(rollValue);
    }

    /** The aura currently in force, which is NORMAL again once its four rounds have run out. */
    private SpeedModifier activeSpeedModifier() {
        return speedRoundsRemaining > 0 ? speedModifier : SpeedModifier.NORMAL;
    }

    /**
     * Advances both countdowns by one round. Called once per round for every piece.
     *
     * <p>The round in which an effect was applied is skipped, so it is never cut short by one.
     */
    public void onRoundCompleted() {
        if (speedAppliedThisRound) {
            speedAppliedThisRound = false;
        } else if (speedRoundsRemaining > 0) {
            speedRoundsRemaining--;
            if (speedRoundsRemaining == 0) {
                speedModifier = SpeedModifier.NORMAL;
            }
        }
        if (briefingBegunThisRound) {
            briefingBegunThisRound = false;
        } else if (briefingRoundsRemaining > 0) {
            briefingRoundsRemaining--;
        }
        if (!isAttendingBriefing()) {
            consecutiveEscapeRolls = 0;
        }
    }

    /** Rule T-9: a captured piece loses every piece of information it carried. */
    public void clear() {
        speedModifier = SpeedModifier.NORMAL;
        speedRoundsRemaining = 0;
        briefingRoundsRemaining = 0;
        speedAppliedThisRound = false;
        briefingBegunThisRound = false;
        consecutiveEscapeRolls = 0;
    }
}
