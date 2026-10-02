package ludot.player;

import static ludot.Fixtures.place;
import static ludot.player.PlayerTestSupport.choiceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import org.junit.jupiter.api.Test;

/** Section 2.1.2: green wins by blocking. */
class GreenPlayerTest {

    private final Board board = new Board();
    private final GreenPlayer green = new GreenPlayer(board, new PathResolver(board));

    @Test
    void formingABlockWithASixComesBeforeEmptyingTheBase() {
        // "unless moving six cells enables green to create a block"
        place(board, PieceColour.GREEN, 1, 40, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 46, Direction.CLOCKWISE, 0);

        PlannedMove choice = choiceOf(green, board, 6);

        assertEquals("G1", choice.primaryPiece().name());
        assertEquals(Square.ring(46), choice.destination());
    }

    @Test
    void otherwiseASixEmptiesTheBase() {
        place(board, PieceColour.GREEN, 1, 40, Direction.CLOCKWISE, 0);

        assertTrue(choiceOf(green, board, 6).isEnteringBoard());
    }

    @Test
    void movingAnExistingBlockIsNotMistakenForFormingANewOne() {
        // a six with a block on the board still empties the base first
        place(board, PieceColour.GREEN, 1, 40, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 40, Direction.CLOCKWISE, 0);

        assertTrue(choiceOf(green, board, 6).isEnteringBoard());
    }

    @Test
    void greenMovesForwardAsABlockWhenItCan() {
        // T-4
        place(board, PieceColour.GREEN, 1, 40, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 40, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 3, 10, Direction.CLOCKWISE, 0);

        PlannedMove choice = choiceOf(green, board, 4);

        assertTrue(choice.isBlockMove());
        assertEquals(Square.ring(42), choice.destination());
    }

    @Test
    void greenCapturesWithAPieceThatStillNeedsItsCapture() {
        // "will not look to capture ... more than what is required to enter the home straight"
        place(board, PieceColour.GREEN, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 20, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 14, Direction.CLOCKWISE, 0);

        PlannedMove choice = choiceOf(green, board, 4);

        assertTrue(choice.capturesAnything());
        assertEquals("G1", choice.primaryPiece().name());
    }

    @Test
    void greenDoesNotCaptureOnceThePieceHasEarnedItsHomeStraight() {
        place(board, PieceColour.GREEN, 1, 10, Direction.CLOCKWISE, 1);
        place(board, PieceColour.GREEN, 2, 20, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 14, Direction.CLOCKWISE, 0);

        PlannedMove choice = choiceOf(green, board, 4);

        assertFalse(choice.capturesAnything());
        assertEquals("G2", choice.primaryPiece().name());
    }

    @Test
    void greenMovesAnotherPieceRatherThanBreakABlock() {
        // a roll of 1 cannot move a block of two, and G3 can use it instead
        place(board, PieceColour.GREEN, 1, 40, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 40, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 3, 10, Direction.CLOCKWISE, 0);

        assertEquals("G3", choiceOf(green, board, 1).primaryPiece().name());
    }

    @Test
    void greenBreaksABlockOnlyWhenNothingElseCanMove() {
        place(board, PieceColour.GREEN, 1, 40, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 40, Direction.CLOCKWISE, 0);

        PlannedMove choice = choiceOf(green, board, 1);

        assertEquals(Square.ring(41), choice.destination());
    }
}
