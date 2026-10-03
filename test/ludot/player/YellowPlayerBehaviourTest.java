package ludot.player;

import static ludot.BoardFixtures.place;
import static ludot.player.PlayerTestHelper.chooseMoveFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.movement.CandidateMove;
import org.junit.jupiter.api.Test;

/** Tests for yellow's racing behaviour. */
class YellowPlayerBehaviourTest {

    private final GameBoard board = new GameBoard();
    private final GamePlayer yellow = PlayerTestHelper.createPlayer(board, PlayerColour.YELLOW);

    @Test
    void aSixAlwaysEmptiesTheBaseEvenWhenACaptureIsOnOffer() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 1, 16, TravelDirection.CLOCKWISE, 0);

        assertTrue(chooseMoveFor(yellow, board, 6).isEnteringBoard());
    }

    @Test
    void aPieceThatStillNeedsACaptureTakesOne() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 40, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 1, 13, TravelDirection.CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(yellow, board, 3);

        assertTrue(choice.capturesAnything());
        assertEquals("Y1", choice.getPrimaryPiece().getName());
    }

    @Test
    void aPieceThatHasAlreadyCapturedIgnoresTheCapture() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 1);
        place(board, PlayerColour.YELLOW, 2, 40, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 1, 13, TravelDirection.CLOCKWISE, 0);

        CandidateMove choice = chooseMoveFor(yellow, board, 3);

        assertFalse(choice.capturesAnything());
        assertEquals("Y2", choice.getPrimaryPiece().getName());
    }

    @Test
    void otherwiseThePieceClosestToHomeMoves() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 40, TravelDirection.CLOCKWISE, 0);

        assertEquals("Y2", chooseMoveFor(yellow, board, 2).getPrimaryPiece().getName());
    }
}
