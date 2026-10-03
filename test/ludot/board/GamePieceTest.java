package ludot.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ludot.effects.MovementModifier;
import org.junit.jupiter.api.Test;

/** Tests for captures and resetting a captured piece. */
class GamePieceTest {

    private final GameBoard board = new GameBoard();
    private final GamePiece piece = board.getPiecesOf(PlayerColour.GREEN).get(1);

    @Test
    void aPieceEarnsItsHomeStraightWithItsFirstCapture() {
        assertFalse(piece.hasEarnedHomeStraightEntry());

        piece.recordCapture();

        assertTrue(piece.hasEarnedHomeStraightEntry());
        assertEquals(1, piece.getCaptureCount());
    }

    @Test
    void aCapturedPieceLosesEverythingItCarried() {
        board.relocate(piece, BoardSquare.ofRing(40));
        piece.assignStartingDirection(TravelDirection.CLOCKWISE);
        piece.recordCapture();
        piece.recordApproachPass();
        piece.getEffects().applyAlphaAura(MovementModifier.DOUBLED);

        piece.resetAfterCapture();

        assertNull(piece.getDirection());
        assertNull(piece.getInitialDirection());
        assertEquals(0, piece.getCaptureCount());
        assertEquals(0, piece.getApproachPasses());
        assertEquals(3, piece.getEffects().adjustRoll(3));
    }
}
