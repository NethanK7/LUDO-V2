package ludot.ui;

import static ludot.BoardFixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.TravelDirection;
import ludot.effects.MovementModifier;
import ludot.movement.BlockedMoveAttempt;
import ludot.movement.CandidateMove;
import ludot.movement.MoveCategory;
import ludot.movement.PieceTransition;
import ludot.mystery.TeleportTarget;
import org.junit.jupiter.api.Test;

/** Tests that every message matches the brief word for word. */
class ConsoleEventPrinterTest {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final ConsoleEventPrinter log = new ConsoleEventPrinter(new PrintStream(bytes));
    private final GameBoard board = new GameBoard();

    private String readPrinted() {
        return bytes.toString().replace(System.lineSeparator(), "\n");
    }

    @Test
    void beforeTheGameBegins() {
        log.introducePlayer(PlayerColour.RED, board.getPiecesOf(PlayerColour.RED));

        assertEquals("The red player has four (04) pieces named R1, R2, R3, and R4.\n", readPrinted());
    }

    @Test
    void choosingTheFirstPlayer() {
        log.reportOpeningRoll(PlayerColour.RED, 4);
        log.reportFirstPlayer(PlayerColour.RED);
        log.reportRoundOrder(List.of(PlayerColour.RED, PlayerColour.GREEN, PlayerColour.YELLOW,
                PlayerColour.BLUE));

        assertEquals("red rolls 4\n"
                + "red player has the highest roll and will begin the game.\n"
                + "The order of a single round is red, green, yellow, and blue.\n", readPrinted());
    }

    @Test
    void rollingAndEnteringTheBoard() {
        GamePiece piece = place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);

        log.reportDiceRoll(PlayerColour.RED, 6);
        log.reportPieceReleased(piece);
        log.reportPieceCounts(board.createSnapshot(PlayerColour.RED));

        assertEquals("red player rolled 6.\n"
                + "red player moves piece R1 to the starting point.\n"
                + "red player now has 1/4 on pieces on the board and 3/4 pieces on the base.\n",
                readPrinted());
    }

    @Test
    void movingAPiece() {
        GamePiece piece = place(board, PlayerColour.RED, 1, 26, TravelDirection.CLOCKWISE, 0);
        PieceTransition movement = new PieceTransition(piece, BoardSquare.ofRing(26), BoardSquare.ofRing(30),
                TravelDirection.COUNTER_CLOCKWISE, 4, 0);

        log.reportPieceMoved(movement);

        assertEquals("red moves piece R1 from location 26 to 30 by 4 units in counter-clockwise "
                + "direction.\n", readPrinted());
    }

    @Test
    void aBlockedPiece() {
        GamePiece piece = place(board, PlayerColour.GREEN, 1, 0, TravelDirection.CLOCKWISE, 0);
        GamePiece blocker = place(board, PlayerColour.RED, 1, 4, TravelDirection.CLOCKWISE, 0);
        CandidateMove partial = new CandidateMove(MoveCategory.PARTIAL_ADVANCE, List.of(new PieceTransition(
                piece, BoardSquare.ofRing(0), BoardSquare.ofRing(3), TravelDirection.CLOCKWISE, 3, 0)), List.of());

        log.reportPieceBlocked(new BlockedMoveAttempt(piece, BoardSquare.ofRing(0), BoardSquare.ofRing(6), blocker,
                Optional.of(partial)));
        log.reportThrowIgnored(PlayerColour.GREEN);
        log.reportMovedUpToBlock(PlayerColour.GREEN, partial);

        assertEquals("green piece G1 is blocked from moving from 0 to 6 by red piece R1.\n"
                + "green does not have other pieces in the board to move instead of the blocked "
                + "piece. Ignoring the throw and moving on to the next player.\n"
                + "green does not have other pieces in the board to move instead of the blocked "
                + "piece. Moved the piece to square 3 which is the cell before the block.\n",
                readPrinted());
    }

    @Test
    void aCapture() {
        GamePiece attacker = place(board, PlayerColour.RED, 1, 30, TravelDirection.CLOCKWISE, 0);
        GamePiece victim = board.getPiecesOf(PlayerColour.BLUE).get(1);

        log.reportCapture(attacker, victim, "30");

        assertEquals("red piece R1 lands on square 30, captures blue piece B2, and returns it to "
                + "the base.\n", readPrinted());
    }

    @Test
    void theEndOfRoundReport() {
        place(board, PlayerColour.BLUE, 1, 13, TravelDirection.CLOCKWISE, 0);
        board.relocate(board.getPiecesOf(PlayerColour.BLUE).get(1),
                BoardSquare.ofHomeStraight(PlayerColour.BLUE, 2));

        log.reportPieceLocations(board.createSnapshot(PlayerColour.BLUE));

        assertEquals("============================\n"
                + "Location of pieces blue\n"
                + "============================\n"
                + "Piece B1 -> 13.\n"
                + "Piece B2 -> bluehomepath2.\n"
                + "Piece B3 -> Base.\n"
                + "Piece B4 -> Base.\n", readPrinted());
    }

    @Test
    void theMysteryCell() {
        GamePiece piece = place(board, PlayerColour.YELLOW, 2, 9, TravelDirection.CLOCKWISE, 0);

        log.reportMysteryCellSpawn(9);
        log.reportMysteryCellLanding(piece, TeleportTarget.ALPHA);
        log.reportTeleport(piece, TeleportTarget.ALPHA);
        log.reportAlphaAura(piece, MovementModifier.DOUBLED);
        log.reportAlphaAura(piece, MovementModifier.HALVED);
        log.reportBetaBriefing(piece);
        log.reportBriefingEscape(piece);
        log.reportGammaReversal(piece);
        log.reportGammaToBeta(piece);

        assertEquals("A mystery cell has spawned in location 9 and will be at this location for "
                + "the next four rounds.\n"
                + "yellow player lands on a mystery cell and is teleported to 7.\n"
                + "yellow piece Y2 teleported to Alpha.\n"
                + "yellow piece Y2 feels energized, and movement speed doubles.\n"
                + "yellow piece Y2 feels sick, and movement speed halves.\n"
                + "yellow piece Y2 attends briefing and cannot move for four rounds.\n"
                + "yellow piece Y2 is movement-restricted and has rolled three consecutively. "
                + "Teleporting piece Y2 to base.\n"
                + "The yellow piece Y2, which was moving clockwise, has changed to moving "
                + "counterclockwise.\n"
                + "The yellow piece Y2 is moving in a counterclockwise direction. Teleporting to "
                + "Beta from Gamma.\n", readPrinted());
    }

    @Test
    void upOnWinning() {
        log.announceWinner(PlayerColour.GREEN);
        log.announceFinalStandings(List.of(PlayerColour.GREEN, PlayerColour.RED,
                PlayerColour.BLUE, PlayerColour.YELLOW));

        assertEquals("\ngreen player wins!!!\n\n"
                + "============================\n"
                + "Final standings\n"
                + "============================\n"
                + "1st place: green\n"
                + "2nd place: red\n"
                + "3rd place: blue\n"
                + "4th place: yellow\n", readPrinted());
    }

    @Test
    void aGridlockedGame() {
        for (GamePiece piece : board.getPiecesOf(PlayerColour.RED)) {
            board.relocate(piece, BoardSquare.ofHome(PlayerColour.RED));
        }

        log.reportGridlock(812, 50, Arrays.stream(PlayerColour.values()).map(board::createSnapshot).toList());

        assertEquals("\nNo piece has moved for 50 rounds: the blocks on the board leave no legal "
                + "move, so the game ends after round 812.\n"
                + "Unfinished: yellow with 0/4 pieces home\n"
                + "Unfinished: blue with 0/4 pieces home\n"
                + "Unfinished: green with 0/4 pieces home\n", readPrinted());
    }

    @Test
    void aGameStoppedAtTheSafetyLimit() {
        log.reportRoundLimitReached(2000,
                Arrays.stream(PlayerColour.values()).map(board::createSnapshot).toList());

        assertEquals("\nThe simulation reached its safety limit of 2000 rounds and was stopped.\n"
                + "Unfinished: yellow with 0/4 pieces home\n"
                + "Unfinished: blue with 0/4 pieces home\n"
                + "Unfinished: red with 0/4 pieces home\n"
                + "Unfinished: green with 0/4 pieces home\n", readPrinted());
    }
}
