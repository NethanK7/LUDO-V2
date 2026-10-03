package ludot.game;

import static ludot.BoardFixtures.createFixedRandom;
import static ludot.BoardFixtures.findPiece;
import static ludot.BoardFixtures.place;
import static ludot.BoardFixtures.placeOn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.command.MoveCommandFactory;
import ludot.movement.MoveOptionFinder;
import ludot.movement.PathNavigator;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportService;
import ludot.player.GamePlayer;
import ludot.player.GamePlayerFactory;
import ludot.random.CoinToss;
import ludot.random.SixSidedDie;
import ludot.ui.GameEventReporter;
import org.junit.jupiter.api.Test;

/** Tests for one turn, with a mocked dice. */
class TurnControllerTest {

    private final GameBoard board = new GameBoard();
    private final PathNavigator pathNavigator = new PathNavigator(board);
    private final SixSidedDie dice = mock(SixSidedDie.class);
    private final GameEventReporter listener = mock(GameEventReporter.class);
    private final MysteryCellScheduler mysteryCell = mock(MysteryCellScheduler.class);
    private final TurnController engine = new TurnController(board, dice,
            new MoveOptionFinder(board, pathNavigator),
            new MoveCommandFactory(board, new CoinToss(createFixedRandom(0, true)), mysteryCell,
                    mock(TeleportService.class), listener),
            pathNavigator, listener);
    private final GamePlayerFactory players = new GamePlayerFactory(board, pathNavigator, mysteryCell);
    private final GamePlayer yellow = players.create(PlayerColour.YELLOW);
    private final GamePlayer blue = players.create(PlayerColour.BLUE);

    private void stubDiceRolls(int first, int... rest) {
        Integer[] others = new Integer[rest.length];
        for (int index = 0; index < rest.length; index++) {
            others[index] = rest[index];
        }
        when(dice.roll()).thenReturn(first, others);
    }

    @Test
    void anUnusableRollEndsTheTurnAfterOneThrow() {
        stubDiceRolls(4);

        engine.playTurn(yellow);

        verify(dice, times(1)).roll();
        assertTrue(findPiece(board, PlayerColour.YELLOW, 1).isInBase());
    }

    @Test
    void aSixBringsAPieceOutAndEarnsAnotherRoll() {
        stubDiceRolls(6, 2);

        engine.playTurn(yellow);

        verify(dice, times(2)).roll();
        assertEquals(BoardSquare.ofRing(2), findPiece(board, PlayerColour.YELLOW, 1).getSquare());
    }

    @Test
    void aThirdSixInARowIsIgnoredAndEndsTheTurn() {
        stubDiceRolls(6, 6, 6, 1);

        engine.playTurn(blue);

        verify(dice, times(3)).roll();
        assertEquals(BoardSquare.ofRing(19), findPiece(board, PlayerColour.BLUE, 1).getSquare());
    }

    @Test
    void aThirdSixBreaksABlockadeOfThreeWithSharesOfFourAndTwo() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 3, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 4, 40, TravelDirection.CLOCKWISE, 0);
        stubDiceRolls(6, 6, 6);

        engine.playTurn(yellow);

        assertEquals(BoardSquare.ofRing(10), findPiece(board, PlayerColour.YELLOW, 1).getSquare());
        assertEquals(BoardSquare.ofRing(14), findPiece(board, PlayerColour.YELLOW, 2).getSquare());
        assertEquals(BoardSquare.ofRing(12), findPiece(board, PlayerColour.YELLOW, 3).getSquare());
        verify(dice, times(3)).roll();
    }

    @Test
    void aThirdSixBreaksABlockadeOfTwoByMovingOnePieceSixCells() {
        place(board, PlayerColour.GREEN, 1, 45, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 2, 45, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 3, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 4, 30, TravelDirection.CLOCKWISE, 0);
        stubDiceRolls(6, 6, 6);

        engine.playTurn(players.create(PlayerColour.GREEN));

        assertEquals(BoardSquare.ofRing(51), findPiece(board, PlayerColour.GREEN, 1).getSquare());
        assertEquals(BoardSquare.ofRing(5), findPiece(board, PlayerColour.GREEN, 2).getSquare());
        assertTrue(board.findBlockSquares(PlayerColour.GREEN).isEmpty());
    }

    @Test
    void aCaptureEarnsAnotherRoll() {
        place(board, PlayerColour.YELLOW, 1, 10, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 13, TravelDirection.CLOCKWISE, 0);
        stubDiceRolls(3, 2);

        engine.playTurn(yellow);

        verify(dice, times(2)).roll();
        assertEquals(BoardSquare.ofRing(15), findPiece(board, PlayerColour.YELLOW, 1).getSquare());
    }

    @Test
    void aBlockedPieceMovesUpToTheCellBeforeTheBlockWhenNothingElseCan() {
        place(board, PlayerColour.YELLOW, 1, 0, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 4, TravelDirection.CLOCKWISE, 0);
        stubDiceRolls(5);

        engine.playTurn(yellow);

        assertEquals(BoardSquare.ofRing(3), findPiece(board, PlayerColour.YELLOW, 1).getSquare());
        verify(listener).reportPieceBlocked(any());
        verify(listener).reportMovedUpToBlock(eq(PlayerColour.YELLOW), any());
    }

    @Test
    void aBlockedPieceWithNoRoomLosesTheThrow() {
        place(board, PlayerColour.YELLOW, 1, 3, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 4, TravelDirection.CLOCKWISE, 0);
        stubDiceRolls(2);

        engine.playTurn(yellow);

        assertEquals(BoardSquare.ofRing(3), findPiece(board, PlayerColour.YELLOW, 1).getSquare());
        verify(listener).reportThrowIgnored(PlayerColour.YELLOW);
    }

    @Test
    void aFullMoveIsAlwaysPreferredToStoppingBeforeABlock() {
        place(board, PlayerColour.YELLOW, 1, 0, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.YELLOW, 2, 20, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 4, TravelDirection.CLOCKWISE, 0);
        stubDiceRolls(5);

        engine.playTurn(yellow);

        assertEquals(BoardSquare.ofRing(0), findPiece(board, PlayerColour.YELLOW, 1).getSquare());
        assertEquals(BoardSquare.ofRing(25), findPiece(board, PlayerColour.YELLOW, 2).getSquare());
        verify(listener, never()).reportPieceBlocked(any());
    }

    @Test
    void twoThreesInARowSendABriefedPieceToItsBase() {
        GamePiece briefed = place(board, PlayerColour.YELLOW, 1, 25, TravelDirection.CLOCKWISE, 0);
        briefed.getEffects().beginBriefing();
        stubDiceRolls(3, 3);

        engine.playTurn(yellow);
        engine.playTurn(yellow);

        assertTrue(briefed.isInBase());
        verify(listener).reportBriefingEscape(briefed);
    }

    @Test
    void aPlayerStopsRollingOnceItsLastPieceIsHome() {
        for (int number = 1; number <= 3; number++) {
            placeOn(board, PlayerColour.YELLOW, number, BoardSquare.ofHome(PlayerColour.YELLOW),
                    TravelDirection.CLOCKWISE, 1);
        }
        GamePiece last = place(board, PlayerColour.YELLOW, 4, 50, TravelDirection.CLOCKWISE, 1);
        last.setApproachPasses(1);
        stubDiceRolls(6, 6);

        engine.playTurn(yellow);

        assertTrue(board.hasAllPiecesHome(PlayerColour.YELLOW));
        verify(dice, times(1)).roll();
    }

    @Test
    void twoSixesAndThenAnotherNumberGiveExactlyThreeRolls() {
        stubDiceRolls(6, 6, 2, 5);

        engine.playTurn(blue);

        verify(dice, times(3)).roll();
        assertEquals(BoardSquare.ofRing(21), findPiece(board, PlayerColour.BLUE, 1).getSquare());
    }
}
