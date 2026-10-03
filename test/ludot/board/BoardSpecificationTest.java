package ludot.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for the start, approach, Alpha, Beta and Gamma cells. */
class BoardSpecificationTest {

    @ParameterizedTest(name = "{0} starts on {1} and turns home at {2}")
    @CsvSource({"YELLOW, 0, 50", "BLUE, 13, 11", "RED, 26, 24", "GREEN, 39, 37"})
    void everyColourHasItsStartAndApproachCell(PlayerColour colour, int start, int approach) {
        assertEquals(start, colour.getStartCell());
        assertEquals(approach, colour.getApproachCell());
    }

    @Test
    void alphaBetaAndGammaAreThe9th27thAnd46thCellsFromTheYellowApproach() {
        assertEquals(7, BoardSpecification.ALPHA_CELL);
        assertEquals(25, BoardSpecification.BETA_CELL);
        assertEquals(44, BoardSpecification.GAMMA_CELL);
    }

    @Test
    void theDicePassesToTheLeftSoRedIsFollowedByGreen() {
        assertEquals(PlayerColour.GREEN, PlayerColour.RED.getNextInTurnOrder());
        assertEquals(PlayerColour.YELLOW, PlayerColour.GREEN.getNextInTurnOrder());
        assertEquals(PlayerColour.BLUE, PlayerColour.YELLOW.getNextInTurnOrder());
        assertEquals(PlayerColour.RED, PlayerColour.BLUE.getNextInTurnOrder());
    }

    @Test
    void wrappingKeepsEveryCellOnTheFiftyTwoCellPath() {
        assertEquals(0, BoardSpecification.wrapRing(52));
        assertEquals(51, BoardSpecification.wrapRing(-1));
        assertEquals(3, BoardSpecification.wrapRing(107));
    }

    @Test
    void clockwiseStepsUpAndWrapsFrom51To0() {
        assertEquals(1, TravelDirection.CLOCKWISE.getNextRingCell(0));
        assertEquals(0, TravelDirection.CLOCKWISE.getNextRingCell(51));
    }

    @Test
    void counterClockwiseStepsDownAndWrapsFrom0To51() {
        assertEquals(51, TravelDirection.COUNTER_CLOCKWISE.getNextRingCell(0));
        assertEquals(4, TravelDirection.COUNTER_CLOCKWISE.getNextRingCell(5));
    }
}
