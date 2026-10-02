package ludot.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The fixed layout of the board, read off Figure 1 and the Legend of the specification. */
class GeometryTest {

    @ParameterizedTest(name = "{0} starts on {1} and turns home at {2}")
    @CsvSource({"YELLOW, 0, 50", "BLUE, 13, 11", "RED, 26, 24", "GREEN, 39, 37"})
    void everyColourHasItsStartAndApproachCell(PieceColour colour, int start, int approach) {
        assertEquals(start, colour.startCell());
        assertEquals(approach, colour.approachCell());
    }

    @Test
    void alphaBetaAndGammaAreThe9th27thAnd46thCellsFromTheYellowApproach() {
        // T-11: the yellow approach cell (50) counts as zero
        assertEquals(7, BoardGeometry.ALPHA_CELL);
        assertEquals(25, BoardGeometry.BETA_CELL);
        assertEquals(44, BoardGeometry.GAMMA_CELL);
    }

    @Test
    void theDicePassesToTheLeftSoRedIsFollowedByGreen() {
        // "if R rolled the dice, the next player to roll would be G"
        assertEquals(PieceColour.GREEN, PieceColour.RED.nextInTurnOrder());
        assertEquals(PieceColour.YELLOW, PieceColour.GREEN.nextInTurnOrder());
        assertEquals(PieceColour.BLUE, PieceColour.YELLOW.nextInTurnOrder());
        assertEquals(PieceColour.RED, PieceColour.BLUE.nextInTurnOrder());
    }

    @Test
    void coloursAreNamedInLowerCaseWithAnUpperCaseInitial() {
        assertEquals("red", PieceColour.RED.displayName());
        assertEquals('R', PieceColour.RED.initial());
    }

    @Test
    void wrappingKeepsEveryCellOnTheFiftyTwoCellPath() {
        assertEquals(0, BoardGeometry.wrapRing(52));
        assertEquals(51, BoardGeometry.wrapRing(-1));
        assertEquals(3, BoardGeometry.wrapRing(107));
    }

    @Test
    void clockwiseStepsUpAndWrapsFrom51To0() {
        assertEquals(1, Direction.CLOCKWISE.nextRingCell(0));
        assertEquals(0, Direction.CLOCKWISE.nextRingCell(51));
    }

    @Test
    void counterClockwiseStepsDownAndWrapsFrom0To51() {
        assertEquals(51, Direction.COUNTER_CLOCKWISE.nextRingCell(0));
        assertEquals(4, Direction.COUNTER_CLOCKWISE.nextRingCell(5));
    }

    @Test
    void aCounterClockwisePieceMustReachItsApproachCellTwice() {
        // T-1
        assertEquals(1, Direction.CLOCKWISE.requiredApproachPasses());
        assertEquals(2, Direction.COUNTER_CLOCKWISE.requiredApproachPasses());
    }
}
