package ludot.movement;

import static ludot.Fixtures.place;
import static ludot.Fixtures.placeOn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import org.junit.jupiter.api.Test;

/** Walking the board one cell at a time: Rules 1, 5, 9, 10, T-1, T-3, T-7 and T-8. */
class PathResolverTest {

    private final Board board = new Board();
    private final PathResolver resolver = new PathResolver(board);

    @Test
    void aClockwisePieceOnItsStartIs56CellsFromHome() {
        // 50 cells to the approach cell, 5 home-straight cells, then home
        Piece piece = place(board, PieceColour.YELLOW, 1, 0, Direction.CLOCKWISE, 1);

        assertEquals(56, resolver.distanceToHome(piece));
    }

    @Test
    void aCounterClockwisePieceMustPassItsApproachTwiceSoItIs60CellsFromHome() {
        // T-1: 2 cells back to the approach, a full lap of 52 back to it, then 6 more
        Piece piece = place(board, PieceColour.YELLOW, 1, 0, Direction.COUNTER_CLOCKWISE, 1);

        assertEquals(60, resolver.distanceToHome(piece));
    }

    @Test
    void aPieceInItsBaseHasNoDistanceToHome() {
        Piece piece = board.piecesOf(PieceColour.RED).get(0);

        assertEquals(PathResolver.UNREACHABLE, resolver.distanceToHome(piece));
    }

    @Test
    void aPieceWithoutACaptureWalksStraightPastItsApproachCell() {
        // T-7
        Piece piece = place(board, PieceColour.YELLOW, 1, 48, Direction.CLOCKWISE, 0);

        PathResolver.Walk walk = resolver.walk(piece, Direction.CLOCKWISE, 3);

        assertEquals(Square.ring(51), walk.destination().orElseThrow());
    }

    @Test
    void aPieceThatHasCapturedTurnsIntoItsHomeStraight() {
        // Rule 9 + T-7
        Piece piece = place(board, PieceColour.YELLOW, 1, 48, Direction.CLOCKWISE, 1);

        PathResolver.Walk walk = resolver.walk(piece, Direction.CLOCKWISE, 3);

        assertEquals(Square.homeStraight(PieceColour.YELLOW, 0), walk.destination().orElseThrow());
        assertEquals(1, walk.approachArrivals());
    }

    @Test
    void aCounterClockwisePieceIgnoresItsFirstVisitToTheApproachCell() {
        // T-1: from cell 1 counter-clockwise, 4 steps pass 0, 51, 50 and continue to 49
        Piece piece = place(board, PieceColour.YELLOW, 1, 1, Direction.COUNTER_CLOCKWISE, 1);

        PathResolver.Walk walk = resolver.walk(piece, Direction.COUNTER_CLOCKWISE, 4);

        assertEquals(Square.ring(49), walk.destination().orElseThrow());
    }

    @Test
    void aCounterClockwisePieceTurnsHomeOnItsSecondVisit() {
        // T-1
        Piece piece = place(board, PieceColour.YELLOW, 1, 1, Direction.COUNTER_CLOCKWISE, 1);
        piece.setApproachPasses(1);

        PathResolver.Walk walk = resolver.walk(piece, Direction.COUNTER_CLOCKWISE, 4);

        assertEquals(Square.homeStraight(PieceColour.YELLOW, 0), walk.destination().orElseThrow());
    }

    @Test
    void theExactRollIsNeededToReachHome() {
        // Rule 10
        Piece piece = placeOn(board, PieceColour.YELLOW, 1,
                Square.homeStraight(PieceColour.YELLOW, 3), Direction.CLOCKWISE, 1);

        assertEquals(Square.home(PieceColour.YELLOW),
                resolver.walk(piece, Direction.CLOCKWISE, 2).destination().orElseThrow());
        assertEquals(PathResolver.Outcome.IMPOSSIBLE,
                resolver.walk(piece, Direction.CLOCKWISE, 3).outcome());
    }

    @Test
    void aLoneOpponentPieceIsJumpedOver() {
        // Rule 5
        Piece piece = place(board, PieceColour.GREEN, 1, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 3, Direction.CLOCKWISE, 0);

        PathResolver.Walk walk = resolver.walk(piece, Direction.CLOCKWISE, 6);

        assertTrue(walk.isCompleted());
        assertEquals(Square.ring(6), walk.destination().orElseThrow());
    }

    @Test
    void anOpponentBlockStopsThePieceOnTheCellBeforeIt() {
        // T-3 worked example: G1 on 0, red block on 4, roll 6 -> cell 3
        Piece piece = place(board, PieceColour.GREEN, 1, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);

        PathResolver.Walk walk = resolver.walk(piece, Direction.CLOCKWISE, 6);

        assertEquals(PathResolver.Outcome.BLOCKED, walk.outcome());
        assertEquals(Square.ring(3), walk.destination().orElseThrow());
        assertEquals(3, walk.stepsTaken());
        assertEquals("R1", walk.blockingPiece().orElseThrow().name());
    }

    @Test
    void aBlockRightInFrontLeavesNoRoomToMove() {
        // T-3
        Piece piece = place(board, PieceColour.GREEN, 1, 3, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);

        PathResolver.Walk walk = resolver.walk(piece, Direction.CLOCKWISE, 2);

        assertEquals(PathResolver.Outcome.BLOCKED, walk.outcome());
        assertTrue(walk.destination().isEmpty());
    }

    @Test
    void aSinglePieceCannotLandOnAnOpponentBlock() {
        // T-3 / T-8
        Piece piece = place(board, PieceColour.GREEN, 1, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);

        assertEquals(PathResolver.Outcome.BLOCKED,
                resolver.walk(piece, Direction.CLOCKWISE, 4).outcome());
    }

    @Test
    void aBlockadeOfTheSameSizeMayLandOnAnOpponentBlockade() {
        // T-8
        Piece first = place(board, PieceColour.GREEN, 1, 0, Direction.CLOCKWISE, 0);
        Piece second = place(board, PieceColour.GREEN, 2, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 2, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 2, Direction.CLOCKWISE, 0);

        PathResolver.Walk walk = resolver.walk(List.of(first, second), first, Direction.CLOCKWISE, 2);

        assertTrue(walk.isCompleted());
        assertEquals(Square.ring(2), walk.destination().orElseThrow());
    }

    @Test
    void aBlockMayNotCarryAPieceWithoutACaptureIntoTheHomeStraight() {
        // T-7: one piece in the block has not captured, so the block stays on the path
        Piece captured = place(board, PieceColour.YELLOW, 1, 48, Direction.CLOCKWISE, 1);
        Piece notCaptured = place(board, PieceColour.YELLOW, 2, 48, Direction.CLOCKWISE, 0);

        PathResolver.Walk walk =
                resolver.walk(List.of(captured, notCaptured), captured, Direction.CLOCKWISE, 3);

        assertEquals(Square.ring(51), walk.destination().orElseThrow());
    }

    @Test
    void aBlockOfPiecesThatHaveAllCapturedEntersTheHomeStraightTogether() {
        // Rule 9 + T-7
        Piece first = place(board, PieceColour.YELLOW, 1, 48, Direction.CLOCKWISE, 1);
        Piece second = place(board, PieceColour.YELLOW, 2, 48, Direction.CLOCKWISE, 1);

        PathResolver.Walk walk = resolver.walk(List.of(first, second), first, Direction.CLOCKWISE, 3);

        assertEquals(Square.homeStraight(PieceColour.YELLOW, 0), walk.destination().orElseThrow());
    }

    @Test
    void landingCapturesEveryOpponentButNeverAnOwnPiece() {
        // Rule 6
        place(board, PieceColour.RED, 1, 9, Direction.CLOCKWISE, 0);
        place(board, PieceColour.BLUE, 1, 9, Direction.CLOCKWISE, 0);
        place(board, PieceColour.GREEN, 3, 9, Direction.CLOCKWISE, 0);

        List<String> captured = resolver.capturesOnLanding(Square.ring(9), PieceColour.GREEN)
                .stream().map(Piece::name).toList();

        assertEquals(List.of("R1", "B1"), captured);
    }

    @Test
    void nothingCanBeCapturedInAHomeStraight() {
        assertTrue(resolver.capturesOnLanding(Square.homeStraight(PieceColour.RED, 1),
                PieceColour.GREEN).isEmpty());
    }

    @Test
    void theIntendedDestinationIgnoresBlocks() {
        // used for the "blocked from moving from L1 to L2" message
        Piece piece = place(board, PieceColour.GREEN, 1, 0, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        place(board, PieceColour.RED, 2, 4, Direction.CLOCKWISE, 0);

        assertEquals(Square.ring(6), resolver.destinationIgnoringBlocks(piece, Direction.CLOCKWISE, 6));
    }
}
