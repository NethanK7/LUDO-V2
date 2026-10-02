package ludot.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Rules T-12 and T-13: the Alpha aura and the Beta briefing. */
class PieceEffectsTest {

    private final PieceEffects effects = new PieceEffects();

    @ParameterizedTest(name = "{0} turns a roll of {1} into {2}")
    @CsvSource({"NORMAL, 5, 5", "DOUBLED, 5, 10", "HALVED, 5, 2", "HALVED, 1, 0", "DOUBLED, 6, 12"})
    void theAuraScalesTheRoll(SpeedModifier modifier, int roll, int expected) {
        // T-12: halving rounds down, so a sick piece cannot use a 1
        assertEquals(expected, modifier.apply(roll));
    }

    @Test
    void anUnaffectedPieceMovesTheFaceValue() {
        assertEquals(4, effects.adjustRoll(4));
        assertFalse(effects.isAttendingBriefing());
    }

    @Test
    void theAuraLastsTheRestOfItsRoundPlusTheNextFourRounds() {
        // T-12: "within the next four rounds"
        effects.applyAlphaAura(SpeedModifier.DOUBLED);
        effects.onRoundCompleted(); // the round in which the piece was teleported

        for (int round = 1; round <= 4; round++) {
            assertEquals(10, effects.adjustRoll(5), "round " + round + " after the teleport");
            effects.onRoundCompleted();
        }

        assertEquals(5, effects.adjustRoll(5));
    }

    @Test
    void theBriefingLastsTheRestOfItsRoundPlusTheNextFourRounds() {
        // T-13: "cannot move for the next four rounds"
        effects.beginBriefing();
        effects.onRoundCompleted();

        for (int round = 1; round <= 4; round++) {
            assertTrue(effects.isAttendingBriefing(), "round " + round + " after the teleport");
            effects.onRoundCompleted();
        }

        assertFalse(effects.isAttendingBriefing());
    }

    @Test
    void twoThreesInARowDuringABriefingSendThePieceToBase() {
        // T-13 + interpretation: two consecutive threes
        effects.beginBriefing();

        effects.observeRoll(3);
        assertFalse(effects.mustLeaveBriefingForBase());
        effects.observeRoll(3);

        assertTrue(effects.mustLeaveBriefingForBase());
    }

    @Test
    void anyOtherRollBreaksTheRunOfThrees() {
        effects.beginBriefing();

        effects.observeRoll(3);
        effects.observeRoll(5);
        effects.observeRoll(3);

        assertFalse(effects.mustLeaveBriefingForBase());
    }

    @Test
    void threesRolledBeforeTheBriefingBeganDoNotCount() {
        effects.observeRoll(3);
        effects.beginBriefing();
        effects.observeRoll(3);

        assertFalse(effects.mustLeaveBriefingForBase());
    }

    @Test
    void clearingRemovesEveryEffect() {
        // T-9
        effects.applyAlphaAura(SpeedModifier.HALVED);
        effects.beginBriefing();

        effects.clear();

        assertEquals(6, effects.adjustRoll(6));
        assertFalse(effects.isAttendingBriefing());
    }
}
