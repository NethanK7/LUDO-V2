package ludot.ui;

import java.io.PrintStream;
import java.util.List;
import ludot.board.Board;
import ludot.board.BoardGeometry;
import ludot.board.PieceColour;
import ludot.movement.BlockedAttempt;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.mystery.TeleportDestination;
import ludot.piece.Piece;
import ludot.piece.SpeedModifier;
import ludot.random.Coin;

/**
 * Every line the simulation prints.
 *
 * <p>All of the status messages demanded by Section&nbsp;3 of the specification are collected here,
 * one method per message, so the wording lives in exactly one place. The rest of the program never
 * calls {@code System.out} - it describes <em>what happened</em> and lets this class decide how to
 * say it. Swapping the console for a file, or for a test that captures the output, is then a matter
 * of passing a different {@link PrintStream} to the constructor.
 */
public final class GameLog {

    private static final String SEPARATOR = "============================";

    private final PrintStream out;

    public GameLog(PrintStream out) {
        this.out = out;
    }

    // ---------------------------------------------------------------- before the game begins

    /**
     * "The red player has four (04) pieces named R1, R2, R3, and R4."
     *
     * <p>The behaviour line underneath is not required by Section 3, but it makes the transcript
     * self-explanatory: a reader can see why red keeps hunting captures without reading any code.
     */
    public void introducePlayer(PieceColour colour, List<Piece> pieces, String behaviourSummary) {
        out.printf("The %s player has four (04) pieces named %s, %s, %s, and %s.%n",
                colour.displayName(), pieces.get(0).name(), pieces.get(1).name(),
                pieces.get(2).name(), pieces.get(3).name());
        out.printf("  Behaviour: %s.%n", behaviourSummary);
    }

    public void announceBoardLayout() {
        blankLine();
        out.println(SEPARATOR);
        out.println("Board layout");
        out.println(SEPARATOR);
        out.println("The standard path has 52 cells numbered 0 to 51, starting at the yellow");
        out.println("starting square and running clockwise.");
        for (PieceColour colour : PieceColour.values()) {
            out.printf("  %-6s starting square X = %2d, approach cell = %2d, home straight = %shomepath0..%d%n",
                    colour.displayName(), colour.startCell(), colour.approachCell(),
                    colour.displayName(), BoardGeometry.HOME_STRAIGHT_LENGTH - 1);
        }
        out.printf("  Alpha = cell %d, Beta = cell %d, Gamma = cell %d%n",
                BoardGeometry.ALPHA_CELL, BoardGeometry.BETA_CELL, BoardGeometry.GAMMA_CELL);
    }

    // ---------------------------------------------------------------- choosing the first player

    /** "[colour] rolls <value>" */
    public void openingRoll(PieceColour colour, int value) {
        out.printf("%s rolls %d%n", colour.displayName(), value);
    }

    public void openingRollTie() {
        out.println("The highest roll is shared, so the tied players roll again.");
    }

    /** "[colour] player has the highest roll and will begin the game." */
    public void firstPlayerChosen(PieceColour colour) {
        out.printf("%s player has the highest roll and will begin the game.%n",
                colour.displayName());
    }

    /** "The order of a single round is [c1], [c2], [c3], and [c4]." */
    public void roundOrder(List<PieceColour> order) {
        out.printf("The order of a single round is %s, %s, %s, and %s.%n",
                order.get(0).displayName(), order.get(1).displayName(), order.get(2).displayName(),
                order.get(3).displayName());
    }

    // ---------------------------------------------------------------- rolling and moving

    public void roundHeader(int roundNumber) {
        blankLine();
        out.println(SEPARATOR);
        out.printf("Round %d%n", roundNumber);
        out.println(SEPARATOR);
    }

    /** "[Color X] player rolled [value]." */
    public void diceRolled(PieceColour colour, int value) {
        out.printf("%s player rolled %d.%n", colour.displayName(), value);
    }

    /** "[Color X] player moves piece X[Name] to the starting point." */
    public void movesToStartingPoint(Piece piece) {
        out.printf("%s player moves piece %s to the starting point.%n",
                piece.colour().displayName(), piece.name());
    }

    /** Rule T-1: the coin toss that fixes the direction of a piece leaving the base. */
    public void coinTossed(Piece piece, Coin.Face face) {
        out.printf("The coin toss for %s piece %s is %s, so it will move in a %s direction.%n",
                piece.colour().displayName(), piece.name(), face.displayName(),
                face.awardedDirection().displayName());
    }

    /**
     * "[Color X] moves piece X from location L1 to L2 by [value] units in
     * [clockwise/counter-clockwise] direction."
     */
    public void movesPiece(PlannedMove move) {
        Piece piece = move.primaryPiece();
        out.printf("%s moves piece %s from location %s to %s by %d units in %s direction.%n",
                piece.colour().displayName(), piece.name(), move.from().label(),
                move.destination().label(), move.stepsTaken(), move.direction().displayName());
    }

    /** Rule T-4: the whole block travels together, so all of its pieces are named. */
    public void movesBlock(PlannedMove move) {
        Piece piece = move.primaryPiece();
        out.printf("%s moves its block of %d pieces (%s) from location %s to %s by %d units "
                        + "in %s direction.%n",
                piece.colour().displayName(), move.groupSize(), names(move.movedPieces()),
                move.from().label(), move.destination().label(), move.stepsTaken(),
                move.direction().displayName());
    }

    public void pieceReachedHome(Piece piece, int piecesHome) {
        out.printf("%s piece %s has reached Home. %s now has %d/%d pieces home.%n",
                piece.colour().displayName(), piece.name(), piece.colour().displayName(),
                piecesHome, BoardGeometry.PIECES_PER_PLAYER);
    }

    // ---------------------------------------------------------------- blocks

    /** "[Color X] piece [Name] is blocked from moving from L1 to L2 by [Color X/Y] piece [Name]." */
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
    public void blockedWithNothingElseToMove(PieceColour colour) {
        out.printf("%s does not have other pieces in the board to move instead of the blocked "
                + "piece. Ignoring the throw and moving on to the next player.%n",
                colour.displayName());
    }

    /**
     * "[Color X] does not have other pieces in the board to move instead of the blocked piece.
     * Moved the piece to square L3 which is the cell before the block."
     */
    public void blockedButMovedUpToTheBlock(PieceColour colour, PlannedMove partialMove) {
        out.printf("%s does not have other pieces in the board to move instead of the blocked "
                + "piece. Moved the piece to square %s which is the cell before the block.%n",
                colour.displayName(), partialMove.destination().label());
    }

    /** Rule T-6: a third consecutive six forces the player to break its blockade. */
    public void blockadeMustBeBroken(PieceColour colour, String blockSquareLabel, int pieceCount) {
        out.printf("%s rolled a six three times in a row and holds a blockade of %d pieces on "
                        + "square %s, which must now be broken (Rule T-6).%n",
                colour.displayName(), pieceCount, blockSquareLabel);
    }

    /** Rule T-6: the forced break-up cannot be played because the way is blocked or too short. */
    public void blockadePieceCannotBeMoved(Piece piece, int units) {
        out.printf("%s piece %s cannot be moved %d units out of the blockade, so it stays where it "
                + "is.%n", piece.colour().displayName(), piece.name(), units);
    }

    public void thirdSixIgnored(PieceColour colour) {
        out.printf("%s rolled a six for the third consecutive time, so the roll is ignored and the "
                + "dice passes to the next player.%n", colour.displayName());
    }

    public void rollCannotBeUsed(PieceColour colour) {
        out.printf("%s has no piece that can use this roll. Ignoring the throw and moving on to "
                + "the next player.%n", colour.displayName());
    }

    // ---------------------------------------------------------------- captures

    /**
     * "[Color X] piece [Name] lands on square L1, captures [Color Y] piece [Name], and returns it to
     * the base."
     */
    public void capture(Piece capturer, Piece captured, String squareLabel) {
        out.printf("%s piece %s lands on square %s, captures %s piece %s, and returns it to the "
                        + "base.%n", capturer.colour().displayName(), capturer.name(), squareLabel,
                captured.colour().displayName(), captured.name());
    }

    public void captureEarnsAnotherRoll(PieceColour colour) {
        out.printf("%s captured an opponent piece and receives another roll (Rule T-2).%n",
                colour.displayName());
    }

    // ---------------------------------------------------------------- mystery cell

    /**
     * "A mystery cell has spawned in location L1 and will be at this location for the next four
     * rounds."
     */
    public void mysteryCellSpawned(int cell) {
        out.printf("A mystery cell has spawned in location %d and will be at this location for the "
                + "next four rounds.%n", cell);
    }

    /** "The mystery cell is at L1 and will be at that location for the next <N> values." */
    public void mysteryCellStatus(MysteryCell mysteryCell) {
        if (mysteryCell.isActive()) {
            out.printf("The mystery cell is at %d and will be at that location for the next %d "
                    + "values.%n", mysteryCell.cell(), mysteryCell.roundsRemaining());
        } else {
            out.println("There is no mystery cell on the board yet.");
        }
    }

    /** "[Color X] player lands on a mystery cell and is teleported to <location>." */
    public void landsOnMysteryCell(Piece piece, TeleportDestination destination) {
        out.printf("%s player lands on a mystery cell and is teleported to %s.%n",
                piece.colour().displayName(), destination.displayName());
    }

    /** "[Color X] piece [name] teleported to Alpha." (and the five other destinations) */
    public void teleported(Piece piece, TeleportDestination destination) {
        out.printf("%s piece %s teleported to %s.%n", piece.colour().displayName(), piece.name(),
                destination.displayName());
    }

    /**
     * "[Color X] piece [name] feels energized, and movement speed doubles." /
     * "[Color X] piece [name] feels sick, and movement speed halves."
     */
    public void alphaAura(Piece piece, SpeedModifier modifier) {
        String effect = modifier == SpeedModifier.DOUBLED
                ? "feels energized, and movement speed doubles"
                : "feels sick, and movement speed halves";
        out.printf("%s piece %s %s.%n", piece.colour().displayName(), piece.name(), effect);
    }

    /** "[Color X] piece [name] attends briefing and cannot move for four rounds." */
    public void betaBriefing(Piece piece) {
        out.printf("%s piece %s attends briefing and cannot move for four rounds.%n",
                piece.colour().displayName(), piece.name());
    }

    /**
     * "[Color X] piece [name] is movement-restricted and has rolled three consecutively.
     * Teleporting piece [name] to base."
     */
    public void briefingEndedByConsecutiveThrees(Piece piece) {
        out.printf("%s piece %s is movement-restricted and has rolled three consecutively. "
                        + "Teleporting piece %s to base.%n", piece.colour().displayName(),
                piece.name(), piece.name());
    }

    /**
     * "The [Color X] piece [name], which was moving clockwise, has changed to moving
     * counterclockwise."
     */
    public void gammaTurnedPieceAround(Piece piece) {
        out.printf("The %s piece %s, which was moving clockwise, has changed to moving "
                + "counterclockwise.%n", piece.colour().displayName(), piece.name());
    }

    /**
     * "The [Color X] piece [name] is moving in a counterclockwise direction. Teleporting to Beta
     * from Gamma."
     */
    public void gammaSendsPieceToBeta(Piece piece) {
        out.printf("The %s piece %s is moving in a counterclockwise direction. Teleporting to Beta "
                + "from Gamma.%n", piece.colour().displayName(), piece.name());
    }

    // ---------------------------------------------------------------- status reports

    /**
     * "[Color X] player now has [Number]/4 on pieces on the board and [Number]/4 pieces on the
     * base."
     */
    public void playerPieceCounts(Board board, PieceColour colour) {
        out.printf("%s player now has %d/%d on pieces on the board and %d/%d pieces on the base.%n",
                colour.displayName(), board.piecesInPlay(colour).size(),
                BoardGeometry.PIECES_PER_PLAYER, board.piecesInBase(colour).size(),
                BoardGeometry.PIECES_PER_PLAYER);
    }

    /** The end-of-round listing of one player's pieces. */
    public void pieceLocations(Board board, PieceColour colour) {
        out.println(SEPARATOR);
        out.printf("Location of pieces %s%n", colour.displayName());
        out.println(SEPARATOR);
        for (Piece piece : board.piecesOf(colour)) {
            out.printf("Piece %s -> %s.%n", piece.name(), piece.square().label());
        }
    }

    // ---------------------------------------------------------------- end of the game

    /** "[Color X] player wins!!!" */
    public void announceWinner(PieceColour colour) {
        blankLine();
        out.printf("%s player wins!!!%n", colour.displayName());
    }

    /** Rule 11: "The game may continue to find second, third, and fourth places." */
    public void announceFinalStandings(List<PieceColour> finishingOrder, Board board) {
        blankLine();
        out.println(SEPARATOR);
        out.println("Final standings");
        out.println(SEPARATOR);
        String[] places = {"1st", "2nd", "3rd", "4th"};
        for (int index = 0; index < finishingOrder.size(); index++) {
            PieceColour colour = finishingOrder.get(index);
            out.printf("%s place: %s (all %d pieces home)%n", places[index], colour.displayName(),
                    BoardGeometry.PIECES_PER_PLAYER);
        }
        for (PieceColour colour : PieceColour.values()) {
            if (!finishingOrder.contains(colour)) {
                out.printf("Unfinished: %s with %d/%d pieces home%n", colour.displayName(),
                        board.piecesAtHome(colour).size(), BoardGeometry.PIECES_PER_PLAYER);
            }
        }
    }

    public void gameStoppedAtRoundLimit(int roundLimit) {
        blankLine();
        out.printf("The simulation reached its safety limit of %d rounds and was stopped.%n",
                roundLimit);
    }

    public void blankLine() {
        out.println();
    }

    private String names(List<Piece> pieces) {
        return String.join(", ", pieces.stream().map(Piece::name).toList());
    }
}
