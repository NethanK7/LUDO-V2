package ludot.movement;

import static ludot.Fixtures.fixedRandom;
import static ludot.Fixtures.place;
import static ludot.Fixtures.placeOn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.effects.SpeedModifier;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.random.Coin;
import ludot.ui.GameListener;
import org.junit.jupiter.api.Test;

/** Carrying out a chosen move: Rules 2, 6, T-1, T-2, T-8, T-9 and T-11. */
class MoveExecutorTest {

    private final Board board = new Board();
    private final MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));
    private final GameListener listener = mock(GameListener.class);
    private final MysteryCell mysteryCell = mock(MysteryCell.class);
    private final MysteryEffectResolver mysteryEffects = mock(MysteryEffectResolver.class);

    private MoveExecutor executorTossing(boolean heads) {
        return new MoveExecutor(board, new Coin(fixedRandom(0, heads)), mysteryCell,
                mysteryEffects, listener);
    }

    @Test
    void enteringTheBoardTossesACoinForTheDirection() {
        // T-1: tails means counter-clockwise
        PlannedMove entry = generator.optionsFor(PieceColour.RED, 6).playableMoves().get(0);

        executorTossing(false).execute(entry);

        Piece piece = entry.primaryPiece();
        assertEquals(Square.ring(26), piece.square());
        assertEquals(Direction.COUNTER_CLOCKWISE, piece.direction());
        assertEquals(Direction.COUNTER_CLOCKWISE, piece.initialDirection());
        verify(listener).movesToStartingPoint(piece);
        verify(listener).coinTossed(piece, Coin.Face.TAILS);
        verify(listener).playerPieceCounts(board, PieceColour.RED);
    }

    @Test
    void aCaptureSendsTheVictimHomeWithEverythingReset() {
        // Rules 6, T-2 and T-9
        Piece attacker = place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        Piece victim = place(board, PieceColour.GREEN, 1, 29, Direction.CLOCKWISE, 2);
        victim.effects().applyAlphaAura(SpeedModifier.DOUBLED);
        victim.setApproachPasses(1);
        PlannedMove capture = generator.optionsFor(PieceColour.RED, 3).playableMoves().get(0);

        boolean captured = executorTossing(true).execute(capture);

        assertTrue(captured);
        assertEquals(Square.base(PieceColour.GREEN), victim.square());
        assertEquals(0, victim.captureCount());
        assertEquals(0, victim.approachPasses());
        assertNull(victim.direction());
        assertEquals(5, victim.effects().adjustRoll(5));
        assertEquals(1, attacker.captureCount());
        verify(listener).capture(attacker, victim, "29");
    }

    @Test
    void aPlainMoveCapturesNothing() {
        place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        PlannedMove move = generator.optionsFor(PieceColour.RED, 3).playableMoves().get(0);

        assertFalse(executorTossing(true).execute(move));
        verify(listener).movesPiece(move);
        verify(listener, never()).capture(any(), any(), any());
    }

    @Test
    void everyPieceOfACapturingBlockadeIsCreditedWithOneCapture() {
        // T-8
        Piece first = place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        Piece second = place(board, PieceColour.YELLOW, 2, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 12, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 2, 12, Direction.CLOCKWISE, 0);
        PlannedMove blockMove = generator.optionsFor(PieceColour.YELLOW, 4).playableMoves().stream()
                .filter(PlannedMove::isBlockMove).findFirst().orElseThrow();

        executorTossing(true).execute(blockMove);

        assertEquals(1, first.captureCount());
        assertEquals(1, second.captureCount());
        assertEquals(4, board.piecesInBase(PieceColour.BLUE).size());
        verify(listener).movesBlock(blockMove);
    }

    @Test
    void landingOnTheMysteryCellTeleportsThePiece() {
        // T-11
        Piece piece = place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        when(mysteryCell.isOn(Square.ring(30))).thenReturn(true);
        PlannedMove move = generator.optionsFor(PieceColour.RED, 4).playableMoves().get(0);

        executorTossing(true).execute(move);

        verify(mysteryEffects).resolveLandingOnMysteryCell(piece);
    }

    @Test
    void reachingHomeIsReported() {
        // Rule 10
        Piece piece = placeOn(board, PieceColour.RED, 1, Square.homeStraight(PieceColour.RED, 4),
                Direction.CLOCKWISE, 1);
        PlannedMove move = generator.optionsFor(PieceColour.RED, 1).playableMoves().get(0);

        executorTossing(true).execute(move);

        assertTrue(piece.isAtHome());
        verify(listener).pieceReachedHome(piece, 1);
    }
}
