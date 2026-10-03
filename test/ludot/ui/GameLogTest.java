package ludot.ui;

import static ludot.Fixtures.place;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.effects.SpeedModifier;
import ludot.movement.BlockedAttempt;
import ludot.movement.MoveKind;
import ludot.movement.PieceMovement;
import ludot.movement.PlannedMove;
import ludot.mystery.TeleportDestination;
import org.junit.jupiter.api.Test;

/** Every status message of Section 3.1, word for word. */
class GameLogTest {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final GameLog log = new GameLog(new PrintStream(bytes));
    private final Board board = new Board();

    private String printed() {
        return bytes.toString().replace(System.lineSeparator(), "\n");
    }

    @Test
    void beforeTheGameBegins() {
        log.introducePlayer(PieceColour.RED, board.piecesOf(PieceColour.RED));

        assertEquals("The red player has four (04) pieces named R1, R2, R3, and R4.\n", printed());
    }

    @Test
    void choosingTheFirstPlayer() {
        log.openingRoll(PieceColour.RED, 4);
        log.firstPlayerChosen(PieceColour.RED);
        log.roundOrder(List.of(PieceColour.RED, PieceColour.GREEN, PieceColour.YELLOW,
                PieceColour.BLUE));

        assertEquals("red rolls 4\n"
                + "red player has the highest roll and will begin the game.\n"
                + "The order of a single round is red, green, yellow, and blue.\n", printed());
    }

    @Test
    void rollingAndEnteringTheBoard() {
        Piece piece = place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);

        log.diceRolled(PieceColour.RED, 6);
        log.movesToStartingPoint(piece);
        log.playerPieceCounts(board, PieceColour.RED);

        assertEquals("red player rolled 6.\n"
                + "red player moves piece R1 to the starting point.\n"
                + "red player now has 1/4 on pieces on the board and 3/4 pieces on the base.\n",
                printed());
    }

    @Test
    void movingAPiece() {
        Piece piece = place(board, PieceColour.RED, 1, 26, Direction.CLOCKWISE, 0);
        PieceMovement movement = new PieceMovement(piece, Square.ring(26), Square.ring(30),
                Direction.COUNTER_CLOCKWISE, 4, 0);

        log.movesPiece(movement);

        assertEquals("red moves piece R1 from location 26 to 30 by 4 units in counter-clockwise "
                + "direction.\n", printed());
    }

    @Test
    void aBlockedPiece() {
        Piece piece = place(board, PieceColour.GREEN, 1, 0, Direction.CLOCKWISE, 0);
        Piece blocker = place(board, PieceColour.RED, 1, 4, Direction.CLOCKWISE, 0);
        PlannedMove partial = new PlannedMove(MoveKind.PARTIAL_ADVANCE, List.of(new PieceMovement(
                piece, Square.ring(0), Square.ring(3), Direction.CLOCKWISE, 3, 0)), List.of());

        log.pieceIsBlocked(new BlockedAttempt(piece, Square.ring(0), Square.ring(6), blocker,
                Optional.of(partial)));
        log.blockedWithNothingElseToMove(PieceColour.GREEN);
        log.blockedButMovedUpToTheBlock(PieceColour.GREEN, partial);

        assertEquals("green piece G1 is blocked from moving from 0 to 6 by red piece R1.\n"
                + "green does not have other pieces in the board to move instead of the blocked "
                + "piece. Ignoring the throw and moving on to the next player.\n"
                + "green does not have other pieces in the board to move instead of the blocked "
                + "piece. Moved the piece to square 3 which is the cell before the block.\n",
                printed());
    }

    @Test
    void aCapture() {
        Piece attacker = place(board, PieceColour.RED, 1, 30, Direction.CLOCKWISE, 0);
        Piece victim = board.piecesOf(PieceColour.BLUE).get(1);

        log.capture(attacker, victim, "30");

        assertEquals("red piece R1 lands on square 30, captures blue piece B2, and returns it to "
                + "the base.\n", printed());
    }

    @Test
    void theEndOfRoundReport() {
        place(board, PieceColour.BLUE, 1, 13, Direction.CLOCKWISE, 0);
        board.relocate(board.piecesOf(PieceColour.BLUE).get(1),
                Square.homeStraight(PieceColour.BLUE, 2));

        log.pieceLocations(board, PieceColour.BLUE);

        assertEquals("============================\n"
                + "Location of pieces blue\n"
                + "============================\n"
                + "Piece B1 -> 13.\n"
                + "Piece B2 -> bluehomepath2.\n"
                + "Piece B3 -> Base.\n"
                + "Piece B4 -> Base.\n", printed());
    }

    @Test
    void theMysteryCell() {
        Piece piece = place(board, PieceColour.YELLOW, 2, 9, Direction.CLOCKWISE, 0);

        log.mysteryCellSpawned(9);
        log.landsOnMysteryCell(piece, TeleportDestination.ALPHA);
        log.teleported(piece, TeleportDestination.ALPHA);
        log.alphaAura(piece, SpeedModifier.DOUBLED);
        log.alphaAura(piece, SpeedModifier.HALVED);
        log.betaBriefing(piece);
        log.briefingEndedByConsecutiveThrees(piece);
        log.gammaTurnedPieceAround(piece);
        log.gammaSendsPieceToBeta(piece);

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
                + "Beta from Gamma.\n", printed());
    }

    @Test
    void upOnWinning() {
        log.announceWinner(PieceColour.GREEN);
        log.announceFinalStandings(List.of(PieceColour.GREEN, PieceColour.RED,
                PieceColour.BLUE, PieceColour.YELLOW));

        assertEquals("\ngreen player wins!!!\n\n"
                + "============================\n"
                + "Final standings\n"
                + "============================\n"
                + "1st place: green\n"
                + "2nd place: red\n"
                + "3rd place: blue\n"
                + "4th place: yellow\n", printed());
    }

    @Test
    void aGridlockedGame() {
        for (Piece piece : board.piecesOf(PieceColour.RED)) {
            board.relocate(piece, Square.home(PieceColour.RED));
        }

        log.gameGridlocked(812, 50, board);

        assertEquals("\nNo piece has moved for 50 rounds: the blocks on the board leave no legal "
                + "move, so the game ends after round 812.\n"
                + "Unfinished: yellow with 0/4 pieces home\n"
                + "Unfinished: blue with 0/4 pieces home\n"
                + "Unfinished: green with 0/4 pieces home\n", printed());
    }
}
