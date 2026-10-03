package ludot.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for square labels, equality and range checks. */
class BoardSquareTest {

    @Test
    void twoSquaresForTheSamePlaceAreEqualSoTheBoardCanLookThemUp() {
        assertEquals(BoardSquare.ofRing(17), BoardSquare.ofRing(17));
        assertEquals(BoardSquare.ofRing(17).hashCode(), BoardSquare.ofRing(17).hashCode());
        assertEquals(BoardSquare.ofBase(PlayerColour.BLUE), BoardSquare.ofBase(PlayerColour.BLUE));
        assertNotEquals(BoardSquare.ofRing(17), BoardSquare.ofRing(18));
        assertNotEquals(BoardSquare.ofRing(17), "17");
    }

    @Test
    void labelsFollowTheLegendOfTheSpecification() {
        assertEquals("37", BoardSquare.ofRing(37).getLabel());
        assertEquals("greenhomepath2", BoardSquare.ofHomeStraight(PlayerColour.GREEN, 2).getLabel());
        assertEquals("Base", BoardSquare.ofBase(PlayerColour.RED).getLabel());
        assertEquals("Home", BoardSquare.ofHome(PlayerColour.RED).getLabel());
    }

    @Test
    void eachColourOwnsItsOwnBaseHomeStraightAndHome() {
        assertNotEquals(BoardSquare.ofBase(PlayerColour.RED), BoardSquare.ofBase(PlayerColour.BLUE));
        assertNotEquals(BoardSquare.ofHomeStraight(PlayerColour.RED, 0),
                BoardSquare.ofHomeStraight(PlayerColour.BLUE, 0));
    }

    @Test
    void cellsOutsideTheBoardAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> BoardSquare.ofRing(52));
        assertThrows(IllegalArgumentException.class, () -> BoardSquare.ofRing(-1));
        assertThrows(IllegalArgumentException.class,
                () -> BoardSquare.ofHomeStraight(PlayerColour.RED, 5));
        assertThrows(IllegalArgumentException.class,
                () -> BoardSquare.ofHomeStraight(PlayerColour.RED, -1));
    }

    @Test
    void recognisesTheApproachCellOfItsOwnColourOnly() {
        assertTrue(BoardSquare.ofRing(24).isApproachCellOf(PlayerColour.RED));
        assertFalse(BoardSquare.ofRing(24).isApproachCellOf(PlayerColour.GREEN));
        assertFalse(BoardSquare.ofHomeStraight(PlayerColour.RED, 0).isApproachCellOf(PlayerColour.RED));
    }
}
