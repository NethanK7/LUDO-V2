package ludot.player;

import static ludot.BoardFixtures.place;
import static ludot.player.PlayerTestHelper.chooseMoveFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.movement.CandidateMove;
import org.junit.jupiter.api.Test;

/** Tests for green's blocking behaviour. */
class GreenPlayerBehaviourTest {

    private final GameBoard board = new GameBoard();
    private final GamePlayer green = PlayerTestHelper.createPlayer(board, PlayerColour.GREEN);

    @Test
    void formingABlockWithASixComesBeforeEmptyingTheBase() {
        place(board, PlayerColour.GREEN, 1, 40, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 46, TravelDirection.CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(green, board, 6);

        assertEquals("G1", choice.getPrimaryPiece().getName());
        assertEquals(BoardSquare.ofRing(46), choice.getDestination());
    }

    @Test
    void otherwiseASixEmptiesTheBase() {
        place(board, PlayerColour.GREEN, 1, 40, TravelDirection.CLOCKWISE, 0);

        assertTrue(chooseMoveFor(green, board, 6).isEnteringBoard());
    }

    @Test
    void movingAnExistingBlockIsNotMistakenForFormingANewOne() {
        place(board, PlayerColour.GREEN, 1, 40, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 40, TravelDirection.CLOCKWISE, 0);

        assertTrue(chooseMoveFor(green, board, 6).isEnteringBoard());
    }

    @Test
    void greenMovesForwardAsABlockWhenItCan() {
        place(board, PlayerColour.GREEN, 1, 40, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 40, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 3, 10, TravelDirection.CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(green, board, 4);

        assertTrue(choice.isBlockMove());
        assertEquals(BoardSquare.ofRing(42), choice.getDestination());
    }

    @Test
    void greenCapturesWithAPieceThatStillNeedsItsCapture() {
        place(board, PlayerColour.GREEN, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 20, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 14, TravelDirection.CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(green, board, 4);

        assertTrue(choice.capturesAnything());
        assertEquals("G1", choice.getPrimaryPiece().getName());
    }

    @Test
    void greenDoesNotCaptureOnceThePieceHasEarnedItsHomeStraight() {
        place(board, PlayerColour.GREEN, 1, 10, TravelDirection.CLOCKWISE, 1);
        place(board, PlayerColour.GREEN, 2, 20, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 14, TravelDirection.CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(green, board, 4);

        assertFalse(choice.capturesAnything());
        assertEquals("G2", choice.getPrimaryPiece().getName());
    }

    @Test
    void greenMovesAnotherPieceRatherThanBreakABlock() {
        place(board, PlayerColour.GREEN, 1, 40, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 40, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 3, 10, TravelDirection.CLOCKWISE, 0);

        assertEquals("G3", chooseMoveFor(green, board, 1).getPrimaryPiece().getName());
    }

    @Test
    void greenBreaksABlockOnlyWhenNothingElseCanMove() {
        place(board, PlayerColour.GREEN, 1, 40, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 40, TravelDirection.CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(green, board, 1);

        assertEquals(BoardSquare.ofRing(41), choice.getDestination());
    }
}
