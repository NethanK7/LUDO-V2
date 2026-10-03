package ludot.effects;

/** The Alpha (T-12) and Beta (T-13) effects on one piece. */
public final class PieceStatusEffects {

    public static final int EFFECT_DURATION_IN_ROUNDS = 4;

    public static final int BRIEFING_ESCAPE_ROLL = 3;

    // T-13 says "rolls three consecutively"; we read that as two 3s in a row.
    public static final int CONSECUTIVE_ESCAPE_ROLLS_TO_LEAVE_BRIEFING = 2;

    private MovementModifier speedModifier = MovementModifier.NORMAL;
    private int speedRoundsRemaining;
    private int briefingRoundsRemaining;
    private boolean speedAppliedThisRound;
    private boolean briefingBegunThisRound;
    private int consecutiveEscapeRolls;

    public void applyAlphaAura(MovementModifier modifier) {
        this.speedModifier = modifier;
        this.speedRoundsRemaining = EFFECT_DURATION_IN_ROUNDS;
        this.speedAppliedThisRound = true;
    }

    public void beginBriefing() {
        this.briefingRoundsRemaining = EFFECT_DURATION_IN_ROUNDS;
        this.briefingBegunThisRound = true;
        this.consecutiveEscapeRolls = 0;
    }

    public void observeRoll(int rollValue) {
        if (!isAttendingBriefing()) {
            return;
        }
        consecutiveEscapeRolls = rollValue == BRIEFING_ESCAPE_ROLL ? consecutiveEscapeRolls + 1 : 0;
    }

    public boolean mustLeaveBriefingForBase() {
        return isAttendingBriefing()
                && consecutiveEscapeRolls >= CONSECUTIVE_ESCAPE_ROLLS_TO_LEAVE_BRIEFING;
    }

    public boolean isAttendingBriefing() {
        return briefingRoundsRemaining > 0;
    }

    public int adjustRoll(int rollValue) {
        return getActiveModifier().apply(rollValue);
    }

    private MovementModifier getActiveModifier() {
        return speedRoundsRemaining > 0 ? speedModifier : MovementModifier.NORMAL;
    }

    // The round an effect starts in does not count, so it lasts the next four full rounds.
    public void finishRound() {
        if (speedAppliedThisRound) {
            speedAppliedThisRound = false;
        } else if (speedRoundsRemaining > 0) {
            speedRoundsRemaining--;
            if (speedRoundsRemaining == 0) {
                speedModifier = MovementModifier.NORMAL;
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

    public void clear() {
        speedModifier = MovementModifier.NORMAL;
        speedRoundsRemaining = 0;
        briefingRoundsRemaining = 0;
        speedAppliedThisRound = false;
        briefingBegunThisRound = false;
        consecutiveEscapeRolls = 0;
    }
}
