package ludot.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ludot.effects.SpeedModifier;
import org.junit.jupiter.api.Test;

class PieceTest {

    private final Board board = new Board();
    private final Piece piece = board.piecesOf(PieceColour.GREEN).get(1);

    @Test
    void aNewPieceIsInItsBaseWithNoHistory() {
        assertEquals("G2", piece.name());
        assertTrue(piece.isInBase());
        assertFalse(piece.isInPlay());
        assertNull(piece.direction());
        assertEquals(0, piece.captureCount());
        assertEquals(0, piece.approachPasses());
    }

    @Test
    void theCoinTossSetsBothTheCurrentAndTheOriginalDirection() {
        // T-1 and T-5
        piece.assignStartingDirection(Direction.COUNTER_CLOCKWISE);

        assertEquals(Direction.COUNTER_CLOCKWISE, piece.direction());
        assertEquals(Direction.COUNTER_CLOCKWISE, piece.initialDirection());
    }

    @Test
    void changingDirectionKeepsTheOriginalDirection() {
        // T-14 then T-5
        piece.assignStartingDirection(Direction.CLOCKWISE);

        piece.setDirection(Direction.COUNTER_CLOCKWISE);

        assertEquals(Direction.COUNTER_CLOCKWISE, piece.direction());
        assertEquals(Direction.CLOCKWISE, piece.initialDirection());
    }

    @Test
    void aPieceEarnsItsHomeStraightWithItsFirstCapture() {
        // T-7
        assertFalse(piece.hasEarnedHomeStraightEntry());

        piece.recordCapture();

        assertTrue(piece.hasEarnedHomeStraightEntry());
        assertEquals(1, piece.captureCount());
    }

    @Test
    void aCapturedPieceLosesEverythingItCarried() {
        // T-9
        board.relocate(piece, Square.ring(40));
        piece.assignStartingDirection(Direction.CLOCKWISE);
        piece.recordCapture();
        piece.recordApproachPass();
        piece.effects().applyAlphaAura(SpeedModifier.DOUBLED);

        piece.resetAfterCapture();

        assertNull(piece.direction());
        assertNull(piece.initialDirection());
        assertEquals(0, piece.captureCount());
        assertEquals(0, piece.approachPasses());
        assertEquals(3, piece.effects().adjustRoll(3));
    }
}
