package ludot.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SquareTest {

    @Test
    void twoSquaresForTheSamePlaceAreEqualSoTheBoardCanLookThemUp() {
        // Board keeps its pieces in a map keyed by Square
        assertEquals(Square.ring(17), Square.ring(17));
        assertEquals(Square.ring(17).hashCode(), Square.ring(17).hashCode());
        assertEquals(Square.base(PieceColour.BLUE), Square.base(PieceColour.BLUE));
        assertNotEquals(Square.ring(17), Square.ring(18));
    }

    @Test
    void labelsFollowTheLegendOfTheSpecification() {
        assertEquals("37", Square.ring(37).label());
        assertEquals("greenhomepath2", Square.homeStraight(PieceColour.GREEN, 2).label());
        assertEquals("Base", Square.base(PieceColour.RED).label());
        assertEquals("Home", Square.home(PieceColour.RED).label());
    }

    @Test
    void eachColourOwnsItsOwnBaseHomeStraightAndHome() {
        assertNotEquals(Square.base(PieceColour.RED), Square.base(PieceColour.BLUE));
        assertNotEquals(Square.homeStraight(PieceColour.RED, 0),
                Square.homeStraight(PieceColour.BLUE, 0));
    }

    @Test
    void cellsOutsideTheBoardAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Square.ring(52));
        assertThrows(IllegalArgumentException.class, () -> Square.ring(-1));
        assertThrows(IllegalArgumentException.class,
                () -> Square.homeStraight(PieceColour.RED, 5));
    }

    @Test
    void recognisesTheApproachCellOfItsOwnColourOnly() {
        assertTrue(Square.ring(24).isApproachCellOf(PieceColour.RED));
        assertFalse(Square.ring(24).isApproachCellOf(PieceColour.GREEN));
        assertFalse(Square.homeStraight(PieceColour.RED, 0).isApproachCellOf(PieceColour.RED));
    }
}
