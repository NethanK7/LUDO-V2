package ludot.game;

import static ludot.Fixtures.fixedRandom;
import static ludot.Fixtures.piece;
import static ludot.Fixtures.place;
import static ludot.Fixtures.placeOn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.movement.MoveExecutor;
import ludot.movement.MoveGenerator;
import ludot.movement.PathResolver;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.player.BluePlayer;
import ludot.player.GreenPlayer;
import ludot.player.Player;
import ludot.player.YellowPlayer;
import ludot.random.Coin;
import ludot.random.Dice;
import ludot.ui.GameListener;
import org.junit.jupiter.api.Test;

/** One whole turn: Rules 4, 7, T-2, T-3, T-6 and T-13. The dice is a Mockito mock. */
class TurnEngineTest {

    private final Board board = new Board();
    private final PathResolver pathResolver = new PathResolver(board);
    private final Dice dice = mock(Dice.class);
    private final GameListener listener = mock(GameListener.class);
    private final MysteryCell mysteryCell = mock(MysteryCell.class);
    private final TurnEngine engine = new TurnEngine(board, dice,
            new MoveGenerator(board, pathResolver),
            new MoveExecutor(board, new Coin(fixedRandom(0, true)), mysteryCell,
                    mock(MysteryEffectResolver.class), listener),
            pathResolver, listener);
    private final Player yellow = new YellowPlayer(board, pathResolver);
    private final Player blue = new BluePlayer(board, pathResolver, mysteryCell);

    private void diceRolling(int first, int... rest) {
        Integer[] others = new Integer[rest.length];
        for (int index = 0; index < rest.length; index++) {
            others[index] = rest[index];
        }
        when(dice.roll()).thenReturn(first, others);
    }

    @Test
    void anUnusableRollEndsTheTurnAfterOneThrow() {
        // Rule 2: every piece is in the base and the roll is not a six
        diceRolling(4);

        engine.playTurn(yellow);

        verify(dice, times(1)).roll();
        assertTrue(piece(board, PieceColour.YELLOW, 1).isInBase());
    }

    @Test
    void aSixBringsAPieceOutAndEarnsAnotherRoll() {
        // Rules 2 and 4
        diceRolling(6, 2);

        engine.playTurn(yellow);

        verify(dice, times(2)).roll();
        assertEquals(Square.ring(2), piece(board, PieceColour.YELLOW, 1).square());
    }

    @Test
    void aThirdSixInARowIsIgnoredAndEndsTheTurn() {
        // Rule 4: B1 comes out on the first six and walks six cells on the second
        diceRolling(6, 6, 6, 1);

        engine.playTurn(blue);

        verify(dice, times(3)).roll();
        assertEquals(Square.ring(19), piece(board, PieceColour.BLUE, 1).square());
    }

    @Test
    void aThirdSixBreaksABlockadeOfThreeWithSharesOfFourAndTwo() {
        // T-6: Y4 uses the first two sixes; the third breaks the blockade on 10
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 3, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 4, 40, Direction.CLOCKWISE, 0);
        diceRolling(6, 6, 6);

        engine.playTurn(yellow);

        assertEquals(Square.ring(10), piece(board, PieceColour.YELLOW, 1).square());
        assertEquals(Square.ring(14), piece(board, PieceColour.YELLOW, 2).square());
        assertEquals(Square.ring(12), piece(board, PieceColour.YELLOW, 3).square());
        verify(dice, times(3)).roll();
    }

    @Test
    void aThirdSixBreaksABlockadeOfTwoByMovingOnePieceSixCells() {
        // T-6: green walks its block 45 -> 48 -> 51 on two sixes, then must break it
        place(board, PieceColour.GREEN, 1, 45, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 2, 45, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 3, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 4, 30, Direction.CLOCKWISE, 0);
        diceRolling(6, 6, 6);

        engine.playTurn(new GreenPlayer(board, pathResolver));

        assertEquals(Square.ring(51), piece(board, PieceColour.GREEN, 1).square());
        assertEquals(Square.ring(5), piece(board, PieceColour.GREEN, 2).square());
        assertTrue(board.blockSquaresOf(PieceColour.GREEN).isEmpty());
    }

    @Test
    void aCaptureEarnsAnotherRoll() {
        // T-2
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 13, Direction.CLOCKWISE, 0);
        diceRolling(3, 2);

        engine.playTurn(yellow);

        verify(dice, times(2)).roll();
        assertEquals(Square.ring(15), piece(board, PieceColour.YELLOW, 1).square());
    }

    @Test
    void aBlockedPieceMovesUpToTheCellBeforeTheBlockWhenNothingElseCan() {
        // T-3 worked example
        place(board, PieceColour.YELLOW, 1, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);
        diceRolling(5);

        engine.playTurn(yellow);

        assertEquals(Square.ring(3), piece(board, PieceColour.YELLOW, 1).square());
        verify(listener).pieceIsBlocked(any());
        verify(listener).blockedButMovedUpToTheBlock(eq(PieceColour.YELLOW), any());
    }

    @Test
    void aBlockedPieceWithNoRoomLosesTheThrow() {
        // T-3 / Rule 7
        place(board, PieceColour.YELLOW, 1, 3, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);
        diceRolling(2);

        engine.playTurn(yellow);

        assertEquals(Square.ring(3), piece(board, PieceColour.YELLOW, 1).square());
        verify(listener).blockedWithNothingElseToMove(PieceColour.YELLOW);
    }

    @Test
    void aFullMoveIsAlwaysPreferredToStoppingBeforeABlock() {
        // T-3: Y2 can use the whole roll, so Y1 is not shuffled up to the block
        place(board, PieceColour.YELLOW, 1, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 20, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);
        diceRolling(5);

        engine.playTurn(yellow);

        assertEquals(Square.ring(0), piece(board, PieceColour.YELLOW, 1).square());
        assertEquals(Square.ring(25), piece(board, PieceColour.YELLOW, 2).square());
        verify(listener, never()).pieceIsBlocked(any());
    }

    @Test
    void twoThreesInARowSendABriefedPieceToItsBase() {
        // T-13: one three in each of two turns
        Piece briefed = place(board, PieceColour.YELLOW, 1, 25, Direction.CLOCKWISE, 0);
        briefed.effects().beginBriefing();
        diceRolling(3, 3);

        engine.playTurn(yellow);
        engine.playTurn(yellow);

        assertTrue(briefed.isInBase());
        verify(listener).briefingEndedByConsecutiveThrees(briefed);
    }

    @Test
    void aPlayerStopsRollingOnceItsLastPieceIsHome() {
        // Rule 11: Y4 walks 6 from its approach cell straight into home
        for (int number = 1; number <= 3; number++) {
            placeOn(board, PieceColour.YELLOW, number, Square.home(PieceColour.YELLOW),
                    Direction.CLOCKWISE, 1);
        }
        Piece last = place(board, PieceColour.YELLOW, 4, 50, Direction.CLOCKWISE, 1);
        last.setApproachPasses(1);
        diceRolling(6, 6);

        engine.playTurn(yellow);

        assertTrue(board.hasAllPiecesHome(PieceColour.YELLOW));
        verify(dice, times(1)).roll();
    }
}
