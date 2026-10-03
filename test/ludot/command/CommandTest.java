package ludot.command;

import static ludot.Fixtures.fixedRandom;
import static ludot.Fixtures.place;
import static ludot.Fixtures.placeOn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.effects.SpeedModifier;
import ludot.movement.BlockedAttempt;
import ludot.movement.MoveGenerator;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.random.Coin;
import ludot.ui.GameListener;
import org.junit.jupiter.api.Test;

/** The Command pattern: every move, blocked throw and unusable roll is a command that is run. */
class CommandTest {

    private final Board board = new Board();
    private final MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));
    private final GameListener listener = mock(GameListener.class);
    private final MysteryCell mysteryCell = mock(MysteryCell.class);
    private final MysteryEffectResolver mysteryEffects = mock(MysteryEffectResolver.class);

    private CommandFactory commandsTossing(boolean heads) {
        return new CommandFactory(board, new Coin(fixedRandom(0, heads)), mysteryCell,
                mysteryEffects, listener);
    }

    @Test
    void enteringTheBoardTossesACoinForTheDirection() {
        // T-1: tails means counter-clockwise
        PlannedMove entry = generator.optionsFor(PieceColour.RED, 6).playableMoves().get(0);

        commandsTossing(false).create(entry).execute();

        Piece piece = entry.primaryPiece();
        assertEquals(Square.ring(26), piece.square());
        assertEquals(Direction.COUNTER_CLOCKWISE, piece.direction());
        assertEquals(Direction.COUNTER_CLOCKWISE, piece.initialDirection());
        verify(listener).movesToStartingPoint(piece);
        verify(listener).playerPieceCounts(board.statusOf(PieceColour.RED));
    }

    @Test
    void aCaptureSendsTheVictimHomeWithEverythingReset() {
        // Rules 6, T-2 and T-9
        Piece attacker = place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        Piece victim = place(board, PieceColour.GREEN, 1, 29, Direction.CLOCKWISE, 2);
        victim.effects().applyAlphaAura(SpeedModifier.DOUBLED);
        victim.setApproachPasses(1);
        PlannedMove capture = generator.optionsFor(PieceColour.RED, 3).playableMoves().get(0);

        boolean captured = commandsTossing(true).create(capture).execute();

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

        assertFalse(commandsTossing(true).create(move).execute());
        verify(listener).movesPiece(move.movements().get(0));
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

        commandsTossing(true).create(blockMove).execute();

        assertEquals(1, first.captureCount());
        assertEquals(1, second.captureCount());
        assertEquals(4, board.piecesInBase(PieceColour.BLUE).size());
        verify(listener, times(2)).movesPiece(any());
    }

    @Test
    void landingOnTheMysteryCellTeleportsThePiece() {
        // T-11
        Piece piece = place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        when(mysteryCell.isOn(Square.ring(30))).thenReturn(true);
        PlannedMove move = generator.optionsFor(PieceColour.RED, 4).playableMoves().get(0);

        commandsTossing(true).create(move).execute();

        verify(mysteryEffects).resolveLandingOnMysteryCell(piece);
    }

    @Test
    void aPieceReachesHomeOnTheExactRoll() {
        // Rule 10
        Piece piece = placeOn(board, PieceColour.RED, 1, Square.homeStraight(PieceColour.RED, 4),
                Direction.CLOCKWISE, 1);
        PlannedMove move = generator.optionsFor(PieceColour.RED, 1).playableMoves().get(0);

        commandsTossing(true).create(move).execute();

        assertTrue(piece.isAtHome());
    }

    @Test
    void aBlockedPieceThatCanMoveUpPrintsBothMessagesAndMoves() {
        // T-3 worked example: G1 on 0, red block on 4, roll 6 -> cell 3
        Piece g1 = place(board, PieceColour.GREEN, 1, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);
        BlockedAttempt attempt = generator.optionsFor(PieceColour.GREEN, 6).blockedAttempts().get(0);

        GameCommand command = commandsTossing(true).createForBlocked(attempt);
        command.execute();

        assertEquals(Square.ring(3), g1.square());
        assertTrue(command.playedMove().isPresent());
        verify(listener).pieceIsBlocked(attempt);
        verify(listener).blockedButMovedUpToTheBlock(eq(PieceColour.GREEN), any());
    }

    @Test
    void aBlockedPieceWithNoRoomLosesTheThrow() {
        // T-3 / Rule 7
        Piece g1 = place(board, PieceColour.GREEN, 1, 3, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);
        BlockedAttempt attempt = generator.optionsFor(PieceColour.GREEN, 2).blockedAttempts().get(0);

        GameCommand command = commandsTossing(true).createForBlocked(attempt);

        assertFalse(command.execute());
        assertEquals(Square.ring(3), g1.square());
        assertTrue(command.playedMove().isEmpty());
        verify(listener).blockedWithNothingElseToMove(PieceColour.GREEN);
    }

    @Test
    void aRollNobodyCanUseIsTheSharedDoNothingCommand() {
        // Null Object + Singleton
        GameCommand noMove = commandsTossing(true).noMove();

        assertSame(NoMoveCommand.INSTANCE, noMove);
        assertFalse(noMove.execute());
        assertTrue(noMove.playedMove().isEmpty());
        verifyNoInteractions(listener);
    }

    @Test
    void walkingOntoAlphaWithoutATeleportHasNoEffect() {
        // T-15: cell 7 is Alpha, but the piece simply walks there
        Piece piece = place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        PlannedMove move = generator.optionsFor(PieceColour.RED, 3).playableMoves().get(0);

        commandsTossing(true).create(move).execute();

        assertEquals(Square.ring(7), piece.square());
        assertEquals(4, piece.effects().adjustRoll(4));
        verify(mysteryEffects, never()).resolveLandingOnMysteryCell(any());
    }

    @Test
    void enteringTheBoardOntoALoneOpponentCapturesIt() {
        // Rules 2 and 6: G1 is standing on red's X (cell 26)
        Piece victim = place(board, PieceColour.GREEN, 1, 26, Direction.CLOCKWISE, 0);
        PlannedMove entry = generator.optionsFor(PieceColour.RED, 6).playableMoves().get(0);

        boolean captured = commandsTossing(true).create(entry).execute();

        assertTrue(captured);
        assertTrue(victim.isInBase());
        assertEquals(1, entry.primaryPiece().captureCount());
    }
}
