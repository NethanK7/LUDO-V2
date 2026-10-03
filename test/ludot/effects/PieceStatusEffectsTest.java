package ludot.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for the Alpha and Beta effects and how long they last. */
class PieceStatusEffectsTest {

    private final PieceStatusEffects effects = new PieceStatusEffects();

    @ParameterizedTest(name = "{0} turns a roll of {1} into {2}")
    @CsvSource({"NORMAL, 5, 5", "DOUBLED, 5, 10", "HALVED, 5, 2", "HALVED, 1, 0", "DOUBLED, 6, 12"})
    void theAuraScalesTheRoll(MovementModifier modifier, int roll, int expected) {
        assertEquals(expected, modifier.apply(roll));
    }

    @Test
    void anUnaffectedPieceMovesTheFaceValue() {
        assertEquals(4, effects.adjustRoll(4));
        assertFalse(effects.isAttendingBriefing());
    }

    @Test
    void theAuraLastsTheRestOfItsRoundPlusTheNextFourRounds() {
        effects.applyAlphaAura(MovementModifier.DOUBLED);
        effects.finishRound();

        for (int round = 1; round <= 4; round++) {
            assertEquals(10, effects.adjustRoll(5), "round " + round + " after the teleport");
            effects.finishRound();
        }

        assertEquals(5, effects.adjustRoll(5));
    }

    @Test
    void theBriefingLastsTheRestOfItsRoundPlusTheNextFourRounds() {
        effects.beginBriefing();
        effects.finishRound();

        for (int round = 1; round <= 4; round++) {
            assertTrue(effects.isAttendingBriefing(), "round " + round + " after the teleport");
            effects.finishRound();
        }

        assertFalse(effects.isAttendingBriefing());
    }

    @Test
    void twoThreesInARowDuringABriefingSendThePieceToBase() {
        effects.beginBriefing();

        effects.trackRoll(3);
        assertFalse(effects.mustLeaveBriefingForBase());
        effects.trackRoll(3);

        assertTrue(effects.mustLeaveBriefingForBase());
    }

    @Test
    void anyOtherRollBreaksTheRunOfThrees() {
        effects.beginBriefing();

        effects.trackRoll(3);
        effects.trackRoll(5);
        effects.trackRoll(3);

        assertFalse(effects.mustLeaveBriefingForBase());
    }

    @Test
    void threesRolledBeforeTheBriefingBeganDoNotCount() {
        effects.trackRoll(3);
        effects.beginBriefing();
        effects.trackRoll(3);

        assertFalse(effects.mustLeaveBriefingForBase());
    }

    @Test
    void clearingRemovesEveryEffect() {
        effects.applyAlphaAura(MovementModifier.HALVED);
        effects.beginBriefing();

        effects.clear();

        assertEquals(6, effects.adjustRoll(6));
        assertFalse(effects.isAttendingBriefing());
    }
}
