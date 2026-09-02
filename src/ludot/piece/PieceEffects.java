package ludot.piece;

/**
 * The temporary Alpha / Beta effects carried by a single piece (Rules T-12 and T-13).
 *
 * <p>Both effects are described in the specification as lasting "the next four rounds", so instead
 * of storing absolute round numbers and doing arithmetic at every read, this class stores a simple
 * countdown for each effect. {@link #onRoundCompleted()} is called once per piece at the end of
 * every round and is the only place where time passes - which makes the behaviour easy to follow
 * and impossible to get wrong by forgetting to compare against the current round.
 */
public final class PieceEffects {

    /** Rules T-12 and T-13 both last "the next four rounds". */
    public static final int EFFECT_DURATION_IN_ROUNDS = 4;

    private SpeedModifier speedModifier = SpeedModifier.NORMAL;
    private int speedRoundsRemaining;
    private int briefingRoundsRemaining;

    /** Rule T-12: the piece was energised or made sick by the Alpha aura. */
    public void applyAlphaAura(SpeedModifier modifier) {
        this.speedModifier = modifier;
        this.speedRoundsRemaining = EFFECT_DURATION_IN_ROUNDS;
    }

    /** Rule T-13: the piece is sent to a briefing at Beta and cannot move for four rounds. */
    public void beginBriefing() {
        this.briefingRoundsRemaining = EFFECT_DURATION_IN_ROUNDS;
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

    /** Advances both countdowns by one round. Called once per round for every piece. */
    public void onRoundCompleted() {
        if (speedRoundsRemaining > 0) {
            speedRoundsRemaining--;
            if (speedRoundsRemaining == 0) {
                speedModifier = SpeedModifier.NORMAL;
            }
        }
        if (briefingRoundsRemaining > 0) {
            briefingRoundsRemaining--;
        }
    }

    /** Rule T-9: a captured piece loses every piece of information it carried. */
    public void clear() {
        speedModifier = SpeedModifier.NORMAL;
        speedRoundsRemaining = 0;
        briefingRoundsRemaining = 0;
    }
}
