package ludot.ui;

import java.io.PrintStream;
import java.util.List;
import ludot.board.Board;
import ludot.board.BoardGeometry;
import ludot.board.Piece;
import ludot.board.PieceColour;
import ludot.effects.SpeedModifier;
import ludot.movement.BlockedAttempt;
import ludot.movement.PieceMovement;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.mystery.TeleportDestination;

/**
 * Every line the simulation prints.
 *
 * <p>All of the status messages demanded by Section&nbsp;3 of the specification are collected here,
 * one method per message, so the wording lives in exactly one place. The rest of the program never
 * calls {@code System.out} - it raises a {@link GameListener} event describing <em>what happened</em>
 * and lets this class decide how to say it. Swapping the console for a file, or for a test that
 * captures the output, is then a matter of passing a different {@link PrintStream} to the
 * constructor.
 *
 * <p>Colours are always printed in lower case, even at the start of a line, because the Legend of
 * the specification defines {@code Color X} as "red, yellow, blue, or green".
 */
public final class GameLog implements GameListener {

    private static final String SEPARATOR = "============================";
    private static final String[] PLACES = {"1st", "2nd", "3rd", "4th"};

    private final PrintStream out;

    public GameLog(PrintStream out) {
        this.out = out;
    }

    // ---------------------------------------------------------------- before the game begins

    /** "The red player has four (04) pieces named R1, R2, R3, and R4." */
    @Override
    public void introducePlayer(PieceColour colour, List<Piece> pieces) {
        out.printf("The %s player has four (04) pieces named %s, %s, %s, and %s.%n",
                colour.displayName(), pieces.get(0).name(), pieces.get(1).name(),
                pieces.get(2).name(), pieces.get(3).name());
    }

    // ---------------------------------------------------------------- choosing the first player

    /** "[colour] rolls <value>" */
    @Override
    public void openingRoll(PieceColour colour, int value) {
        out.printf("%s rolls %d%n", colour.displayName(), value);
    }

    /** "[colour] player has the highest roll and will begin the game." */
    @Override
    public void firstPlayerChosen(PieceColour colour) {
        out.printf("%s player has the highest roll and will begin the game.%n", colour.displayName());
    }

    /** "The order of a single round is [c1], [c2], [c3], and [c4]." */
    @Override
    public void roundOrder(List<PieceColour> order) {
        out.printf("The order of a single round is %s, %s, %s, and %s.%n",
                order.get(0).displayName(), order.get(1).displayName(), order.get(2).displayName(),
                order.get(3).displayName());
    }

    // ---------------------------------------------------------------- rounds and turns

    /** Each turn is separated from the previous one by a blank line. */
    @Override
    public void turnStarted(PieceColour colour) {
        blankLine();
    }

    /** "[Color X] player rolled [value]." */
    @Override
    public void diceRolled(PieceColour colour, int value) {
        out.printf("%s player rolled %d.%n", colour.displayName(), value);
    }

    // ---------------------------------------------------------------- moving

    /** "[Color X] player moves piece X[Name] to the starting point." */
    @Override
    public void movesToStartingPoint(Piece piece) {
        out.printf("%s player moves piece %s to the starting point.%n",
                piece.colour().displayName(), piece.name());
    }

    /**
     * "[Color X] moves piece X from location L1 to L2 by [value] units in
     * [clockwise/counter-clockwise] direction."
     */
    @Override
    public void movesPiece(PieceMovement movement) {
        Piece piece = movement.piece();
        out.printf("%s moves piece %s from location %s to %s by %d units in %s direction.%n",
                piece.colour().displayName(), piece.name(), movement.from().label(),
                movement.to().label(), movement.stepsTaken(), movement.direction().displayName());
    }

    // ---------------------------------------------------------------- blocks

    /** "[Color X] piece [Name] is blocked from moving from L1 to L2 by [Color X/Y] piece [Name]." */
    @Override
    public void pieceIsBlocked(BlockedAttempt attempt) {
        Piece piece = attempt.piece();
        Piece blocker = attempt.blockingPiece();
        out.printf("%s piece %s is blocked from moving from %s to %s by %s piece %s.%n",
                piece.colour().displayName(), piece.name(), attempt.from().label(),
                attempt.intendedDestination().label(), blocker.colour().displayName(),
                blocker.name());
    }

    /**
     * "[Color X] does not have other pieces in the board to move instead of the blocked piece.
     * Ignoring the throw and moving on to the next player."
     */
    @Override
    public void blockedWithNothingElseToMove(PieceColour colour) {
        out.printf("%s does not have other pieces in the board to move instead of the blocked "
                + "piece. Ignoring the throw and moving on to the next player.%n",
                colour.displayName());
    }

    /**
     * "[Color X] does not have other pieces in the board to move instead of the blocked piece.
     * Moved the piece to square L3 which is the cell before the block."
     */
    @Override
    public void blockedButMovedUpToTheBlock(PieceColour colour, PlannedMove partialMove) {
        out.printf("%s does not have other pieces in the board to move instead of the blocked "
                + "piece. Moved the piece to square %s which is the cell before the block.%n",
                colour.displayName(), partialMove.destination().label());
    }

    // ---------------------------------------------------------------- captures

    /**
     * "[Color X] piece [Name] lands on square L1, captures [Color Y] piece [Name], and returns it to
     * the base."
     */
    @Override
    public void capture(Piece capturer, Piece captured, String squareLabel) {
        out.printf("%s piece %s lands on square %s, captures %s piece %s, and returns it to the "
                        + "base.%n", capturer.colour().displayName(), capturer.name(), squareLabel,
                captured.colour().displayName(), captured.name());
    }

    // ---------------------------------------------------------------- mystery cell

    /**
     * "A mystery cell has spawned in location L1 and will be at this location for the next four
     * rounds."
     */
    @Override
    public void mysteryCellSpawned(int cell) {
        out.printf("A mystery cell has spawned in location %d and will be at this location for the "
                + "next four rounds.%n", cell);
    }

    /** "The mystery cell is at L1 and will be at that location for the next <N> values." */
    @Override
    public void mysteryCellStatus(MysteryCell mysteryCell) {
        if (mysteryCell.isActive()) {
            out.printf("The mystery cell is at %d and will be at that location for the next %d "
                    + "values.%n", mysteryCell.cell(), mysteryCell.roundsRemaining());
        }
    }

    /** "[Color X] player lands on a mystery cell and is teleported to <location>." */
    @Override
    public void landsOnMysteryCell(Piece piece, TeleportDestination destination) {
        out.printf("%s player lands on a mystery cell and is teleported to %s.%n",
                piece.colour().displayName(), destination.squareFor(piece.colour()).label());
    }

    /** "[Color X] piece [name] teleported to Alpha." (and the five other destinations) */
    @Override
    public void teleported(Piece piece, TeleportDestination destination) {
        out.printf("%s piece %s teleported to %s.%n", piece.colour().displayName(), piece.name(),
                destination.displayName());
    }

    /**
     * "[Color X] piece [name] feels energized, and movement speed doubles." /
     * "[Color X] piece [name] feels sick, and movement speed halves."
     */
    @Override
    public void alphaAura(Piece piece, SpeedModifier modifier) {
        String effect = modifier == SpeedModifier.DOUBLED
                ? "feels energized, and movement speed doubles"
                : "feels sick, and movement speed halves";
        out.printf("%s piece %s %s.%n", piece.colour().displayName(), piece.name(), effect);
    }

    /** "[Color X] piece [name] attends briefing and cannot move for four rounds." */
    @Override
    public void betaBriefing(Piece piece) {
        out.printf("%s piece %s attends briefing and cannot move for four rounds.%n",
                piece.colour().displayName(), piece.name());
    }

    /**
     * "[Color X] piece [name] is movement-restricted and has rolled three consecutively.
     * Teleporting piece [name] to base."
     */
    @Override
    public void briefingEndedByConsecutiveThrees(Piece piece) {
        out.printf("%s piece %s is movement-restricted and has rolled three consecutively. "
                        + "Teleporting piece %s to base.%n", piece.colour().displayName(),
                piece.name(), piece.name());
    }

    /**
     * "The [Color X] piece [name], which was moving clockwise, has changed to moving
     * counterclockwise."
     */
    @Override
    public void gammaTurnedPieceAround(Piece piece) {
        out.printf("The %s piece %s, which was moving clockwise, has changed to moving "
                + "counterclockwise.%n", piece.colour().displayName(), piece.name());
    }

    /**
     * "The [Color X] piece [name] is moving in a counterclockwise direction. Teleporting to Beta
     * from Gamma."
     */
    @Override
    public void gammaSendsPieceToBeta(Piece piece) {
        out.printf("The %s piece %s is moving in a counterclockwise direction. Teleporting to Beta "
                + "from Gamma.%n", piece.colour().displayName(), piece.name());
    }

    // ---------------------------------------------------------------- status reports

    /**
     * "[Color X] player now has [Number]/4 on pieces on the board and [Number]/4 pieces on the
     * base."
     */
    @Override
    public void playerPieceCounts(Board board, PieceColour colour) {
        out.printf("%s player now has %d/%d on pieces on the board and %d/%d pieces on the base.%n",
                colour.displayName(), board.piecesInPlay(colour).size(),
                BoardGeometry.PIECES_PER_PLAYER, board.piecesInBase(colour).size(),
                BoardGeometry.PIECES_PER_PLAYER);
    }

    /** The end-of-round listing of one player's pieces. */
    @Override
    public void pieceLocations(Board board, PieceColour colour) {
        out.println(SEPARATOR);
        out.printf("Location of pieces %s%n", colour.displayName());
        out.println(SEPARATOR);
        for (Piece piece : board.piecesOf(colour)) {
            out.printf("Piece %s -> %s.%n", piece.name(), piece.square().label());
        }
    }

    /** The end-of-round report is set apart from the last turn by a blank line. */
    @Override
    public void roundEnded() {
        blankLine();
    }

    // ---------------------------------------------------------------- end of the game

    /** "[Color X] player wins!!!" */
    @Override
    public void announceWinner(PieceColour colour) {
        blankLine();
        out.printf("%s player wins!!!%n", colour.displayName());
    }

    /**
     * Rule 11: "The game may continue to find second, third, and fourth places." Once three players
     * are home the fourth is last by elimination, so all four places are listed.
     */
    @Override
    public void announceFinalStandings(List<PieceColour> placings) {
        blankLine();
        out.println(SEPARATOR);
        out.println("Final standings");
        out.println(SEPARATOR);
        for (int index = 0; index < placings.size(); index++) {
            out.printf("%s place: %s%n", PLACES[index], placings.get(index).displayName());
        }
    }

    /** The safety net was hit; the players still on the board are listed with their progress. */
    @Override
    public void gameStoppedAtRoundLimit(int roundLimit, Board board) {
        blankLine();
        out.printf("The simulation reached its safety limit of %d rounds and was stopped.%n",
                roundLimit);
        listUnfinishedPlayers(board);
    }

    /** The board is gridlocked by blocks, so no further move is possible. */
    @Override
    public void gameGridlocked(int round, int stillRounds, Board board) {
        blankLine();
        out.printf("No piece has moved for %d rounds: the blocks on the board leave no legal move, "
                + "so the game ends after round %d.%n", stillRounds, round);
        listUnfinishedPlayers(board);
    }

    private void listUnfinishedPlayers(Board board) {
        for (PieceColour colour : PieceColour.values()) {
            if (!board.hasAllPiecesHome(colour)) {
                out.printf("Unfinished: %s with %d/%d pieces home%n", colour.displayName(),
                        board.piecesAtHome(colour).size(), BoardGeometry.PIECES_PER_PLAYER);
            }
        }
    }

    private void blankLine() {
        out.println();
    }

    private String names(List<Piece> pieces) {
        return String.join(", ", pieces.stream().map(Piece::name).toList());
    }
}
