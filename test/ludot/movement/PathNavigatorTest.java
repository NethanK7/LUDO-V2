package ludot.movement;

import static ludot.BoardFixtures.place;
import static ludot.BoardFixtures.placeOn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import org.junit.jupiter.api.Test;

/** Tests for walking the board: blocks, captures and the home straight. */
class PathNavigatorTest {

    private final GameBoard board = new GameBoard();
    private final PathNavigator pathCalculator = new PathNavigator(board);

    @Test
    void aClockwisePieceOnItsStartIs56CellsFromHome() {
        GamePiece piece = place(board, PlayerColour.YELLOW, 1, 0, TravelDirection.CLOCKWISE, 1);

        assertEquals(56, pathCalculator.calculateDistanceToHome(piece));
    }

    @Test
    void aCounterClockwisePieceMustPassItsApproachTwiceSoItIs60CellsFromHome() {
        GamePiece piece = place(board, PlayerColour.YELLOW, 1, 0, TravelDirection.COUNTER_CLOCKWISE, 1);

        assertEquals(60, pathCalculator.calculateDistanceToHome(piece));
    }

    @Test
    void aPieceInItsBaseHasNoDistanceToHome() {
        GamePiece piece = board.getPiecesOf(PlayerColour.RED).get(0);

        assertEquals(PathNavigator.UNREACHABLE, pathCalculator.calculateDistanceToHome(piece));
    }

    @Test
    void aPieceWithoutACaptureWalksStraightPastItsApproachCell() {
        GamePiece piece = place(board, PlayerColour.YELLOW, 1, 48, TravelDirection.CLOCKWISE, 0);

        PathNavigator.WalkResult walk = pathCalculator.walk(piece, TravelDirection.CLOCKWISE, 3);

        assertEquals(BoardSquare.ofRing(51), walk.getDestination().orElseThrow());
    }

    @Test
    void aPieceThatHasCapturedTurnsIntoItsHomeStraight() {
        GamePiece piece = place(board, PlayerColour.YELLOW, 1, 48, TravelDirection.CLOCKWISE, 1);

        PathNavigator.WalkResult walk = pathCalculator.walk(piece, TravelDirection.CLOCKWISE, 3);

        assertEquals(BoardSquare.ofHomeStraight(PlayerColour.YELLOW, 0), walk.getDestination().orElseThrow());
        assertEquals(1, walk.getApproachArrivals());
    }

    @Test
    void aCounterClockwisePieceIgnoresItsFirstVisitToTheApproachCell() {
        GamePiece piece = place(board, PlayerColour.YELLOW, 1, 1, TravelDirection.COUNTER_CLOCKWISE, 1);

        PathNavigator.WalkResult walk = pathCalculator.walk(piece, TravelDirection.COUNTER_CLOCKWISE, 4);

        assertEquals(BoardSquare.ofRing(49), walk.getDestination().orElseThrow());
    }

    @Test
    void aCounterClockwisePieceTurnsHomeOnItsSecondVisit() {
        GamePiece piece = place(board, PlayerColour.YELLOW, 1, 1, TravelDirection.COUNTER_CLOCKWISE, 1);
        piece.setApproachPasses(1);

        PathNavigator.WalkResult walk = pathCalculator.walk(piece, TravelDirection.COUNTER_CLOCKWISE, 4);

        assertEquals(BoardSquare.ofHomeStraight(PlayerColour.YELLOW, 0), walk.getDestination().orElseThrow());
    }

    @Test
    void theExactRollIsNeededToReachHome() {
        GamePiece piece = placeOn(board, PlayerColour.YELLOW, 1,
                BoardSquare.ofHomeStraight(PlayerColour.YELLOW, 3), TravelDirection.CLOCKWISE, 1);

        assertEquals(BoardSquare.ofHome(PlayerColour.YELLOW),
                pathCalculator.walk(piece, TravelDirection.CLOCKWISE, 2).getDestination().orElseThrow());
        assertEquals(PathNavigator.Outcome.IMPOSSIBLE,
                pathCalculator.walk(piece, TravelDirection.CLOCKWISE, 3).getOutcome());
    }

    @Test
    void aLoneOpponentPieceIsJumpedOver() {
        GamePiece piece = place(board, PlayerColour.GREEN, 1, 0, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 3, TravelDirection.CLOCKWISE, 0);

        PathNavigator.WalkResult walk = pathCalculator.walk(piece, TravelDirection.CLOCKWISE, 6);

        assertTrue(walk.isCompleted());
        assertEquals(BoardSquare.ofRing(6), walk.getDestination().orElseThrow());
    }

    @Test
    void anOpponentBlockStopsThePieceOnTheCellBeforeIt() {
        GamePiece piece = place(board, PlayerColour.GREEN, 1, 0, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 4, TravelDirection.CLOCKWISE, 0);

        PathNavigator.WalkResult walk = pathCalculator.walk(piece, TravelDirection.CLOCKWISE, 6);

        assertEquals(PathNavigator.Outcome.BLOCKED, walk.getOutcome());
        assertEquals(BoardSquare.ofRing(3), walk.getDestination().orElseThrow());
        assertEquals(3, walk.getStepsTaken());
        assertEquals("R1", walk.getBlockingPiece().orElseThrow().getName());
    }

    @Test
    void aBlockRightInFrontLeavesNoRoomToMove() {
        GamePiece piece = place(board, PlayerColour.GREEN, 1, 3, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 4, TravelDirection.CLOCKWISE, 0);

        PathNavigator.WalkResult walk = pathCalculator.walk(piece, TravelDirection.CLOCKWISE, 2);

        assertEquals(PathNavigator.Outcome.BLOCKED, walk.getOutcome());
        assertTrue(walk.getDestination().isEmpty());
    }

    @Test
    void aSinglePieceCannotLandOnAnOpponentBlock() {
        GamePiece piece = place(board, PlayerColour.GREEN, 1, 0, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 4, TravelDirection.CLOCKWISE, 0);

        assertEquals(PathNavigator.Outcome.BLOCKED,
                pathCalculator.walk(piece, TravelDirection.CLOCKWISE, 4).getOutcome());
    }

    @Test
    void aBlockadeOfTheSameSizeMayLandOnAnOpponentBlockade() {
        GamePiece first = place(board, PlayerColour.GREEN, 1, 0, TravelDirection.CLOCKWISE, 0);
        GamePiece second = place(board, PlayerColour.GREEN, 2, 0, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 2, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 2, TravelDirection.CLOCKWISE, 0);

        PathNavigator.WalkResult walk = pathCalculator.walk(List.of(first, second), first, TravelDirection.CLOCKWISE, 2);

        assertTrue(walk.isCompleted());
        assertEquals(BoardSquare.ofRing(2), walk.getDestination().orElseThrow());
    }

    @Test
    void aBlockMayNotCarryAPieceWithoutACaptureIntoTheHomeStraight() {
        GamePiece captured = place(board, PlayerColour.YELLOW, 1, 48, TravelDirection.CLOCKWISE, 1);
        GamePiece notCaptured = place(board, PlayerColour.YELLOW, 2, 48, TravelDirection.CLOCKWISE, 0);

        PathNavigator.WalkResult walk =
                pathCalculator.walk(List.of(captured, notCaptured), captured, TravelDirection.CLOCKWISE, 3);

        assertEquals(BoardSquare.ofRing(51), walk.getDestination().orElseThrow());
    }

    @Test
    void aBlockOfPiecesThatHaveAllCapturedEntersTheHomeStraightTogether() {
        GamePiece first = place(board, PlayerColour.YELLOW, 1, 48, TravelDirection.CLOCKWISE, 1);
        GamePiece second = place(board, PlayerColour.YELLOW, 2, 48, TravelDirection.CLOCKWISE, 1);

        PathNavigator.WalkResult walk = pathCalculator.walk(List.of(first, second), first, TravelDirection.CLOCKWISE, 3);

        assertEquals(BoardSquare.ofHomeStraight(PlayerColour.YELLOW, 0), walk.getDestination().orElseThrow());
    }

    @Test
    void landingCapturesEveryOpponentButNeverAnOwnPiece() {
        place(board, PlayerColour.RED, 1, 9, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.BLUE, 1, 9, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.GREEN, 3, 9, TravelDirection.CLOCKWISE, 0);

        List<String> captured = pathCalculator.findCapturesOnLanding(BoardSquare.ofRing(9), PlayerColour.GREEN)
                .stream().map(GamePiece::getName).toList();

        assertEquals(List.of("R1", "B1"), captured);
    }

    @Test
    void nothingCanBeCapturedInAHomeStraight() {
        assertTrue(pathCalculator.findCapturesOnLanding(BoardSquare.ofHomeStraight(PlayerColour.RED, 1),
                PlayerColour.GREEN).isEmpty());
    }

    @Test
    void theIntendedDestinationIgnoresBlocks() {
        GamePiece piece = place(board, PlayerColour.GREEN, 1, 0, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        place(board, PlayerColour.RED, 2, 4, TravelDirection.CLOCKWISE, 0);

        assertEquals(BoardSquare.ofRing(6), pathCalculator.findDestinationIgnoringBlocks(piece, TravelDirection.CLOCKWISE, 6));
    }

    @Test
    void aPieceMayStopExactlyOnItsApproachCell() {
        GamePiece piece = place(board, PlayerColour.YELLOW, 1, 48, TravelDirection.CLOCKWISE, 1);

        PathNavigator.WalkResult walk = pathCalculator.walk(piece, TravelDirection.CLOCKWISE, 2);

        assertEquals(BoardSquare.ofRing(50), walk.getDestination().orElseThrow());
    }
}
