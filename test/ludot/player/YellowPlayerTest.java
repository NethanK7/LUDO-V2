package ludot.player;

import static ludot.Fixtures.place;
import static ludot.player.PlayerTestSupport.choiceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import org.junit.jupiter.api.Test;

/** Section 2.1.3: yellow always plays to win. */
class YellowPlayerTest {

    private final Board board = new Board();
    private final YellowPlayer yellow = new YellowPlayer(board, new PathResolver(board));

    @Test
    void aSixAlwaysEmptiesTheBaseEvenWhenACaptureIsOnOffer() {
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 16, Direction.CLOCKWISE, 0);

        assertTrue(choiceOf(yellow, board, 6).isEnteringBoard());
    }

    @Test
    void aPieceThatStillNeedsACaptureTakesOne() {
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 40, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 13, Direction.CLOCKWISE, 0);

        PlannedMove choice = choiceOf(yellow, board, 3);

        assertTrue(choice.capturesAnything());
        assertEquals("Y1", choice.primaryPiece().name());
    }

    @Test
    void aPieceThatHasAlreadyCapturedIgnoresTheCapture() {
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 1);
        place(board, PieceColour.YELLOW, 2, 40, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 13, Direction.CLOCKWISE, 0);

        PlannedMove choice = choiceOf(yellow, board, 3);

        assertFalse(choice.capturesAnything());
        assertEquals("Y2", choice.primaryPiece().name());
    }

    @Test
    void otherwiseThePieceClosestToHomeMoves() {
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 40, Direction.CLOCKWISE, 0);

        assertEquals("Y2", choiceOf(yellow, board, 2).primaryPiece().name());
    }
}
