package ludot.ui;

import java.io.PrintStream;
import java.util.List;
import ludot.board.BoardSpecification;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.PlayerStatusSnapshot;
import ludot.effects.MovementModifier;
import ludot.movement.BlockedMoveAttempt;
import ludot.movement.CandidateMove;
import ludot.movement.PieceTransition;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportTarget;

/** Prints every message in the exact wording of Section 3.1 of the brief. */
public final class ConsoleEventPrinter implements GameEventReporter {

    private static final String DIVIDER = "============================";
    private static final String[] PLACES = {"1st", "2nd", "3rd", "4th"};

    private final PrintStream out;

    public ConsoleEventPrinter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void introducePlayer(PlayerColour colour, List<GamePiece> pieces) {
        out.printf("The %s player has four (04) pieces named %s, %s, %s, and %s.%n",
                colour.getDisplayName(), pieces.get(0).getName(), pieces.get(1).getName(),
                pieces.get(2).getName(), pieces.get(3).getName());
    }

    @Override
    public void reportOpeningRoll(PlayerColour colour, int value) {
        out.printf("%s rolls %d%n", colour.getDisplayName(), value);
    }

    @Override
    public void reportFirstPlayer(PlayerColour colour) {
        out.printf("%s player has the highest roll and will begin the game.%n", colour.getDisplayName());
    }

    @Override
    public void reportRoundOrder(List<PlayerColour> order) {
        out.printf("The order of a single round is %s, %s, %s, and %s.%n",
                order.get(0).getDisplayName(), order.get(1).getDisplayName(), order.get(2).getDisplayName(),
                order.get(3).getDisplayName());
    }

    @Override
    public void reportTurnStart(PlayerColour colour) {
        printBlankLine();
    }

    @Override
    public void reportDiceRoll(PlayerColour colour, int value) {
        out.printf("%s player rolled %d.%n", colour.getDisplayName(), value);
    }

    @Override
    public void reportPieceReleased(GamePiece piece) {
        out.printf("%s player moves piece %s to the starting point.%n",
                piece.getColour().getDisplayName(), piece.getName());
    }

    @Override
    public void reportPieceMoved(PieceTransition movement) {
        GamePiece piece = movement.piece();
        out.printf("%s moves piece %s from location %s to %s by %d units in %s direction.%n",
                piece.getColour().getDisplayName(), piece.getName(), movement.from().getLabel(),
                movement.to().getLabel(), movement.stepsTaken(), movement.direction().getDisplayName());
    }

    @Override
    public void reportPieceBlocked(BlockedMoveAttempt attempt) {
        GamePiece piece = attempt.piece();
        GamePiece blocker = attempt.blockingPiece();
        out.printf("%s piece %s is blocked from moving from %s to %s by %s piece %s.%n",
                piece.getColour().getDisplayName(), piece.getName(), attempt.from().getLabel(),
                attempt.intendedDestination().getLabel(), blocker.getColour().getDisplayName(),
                blocker.getName());
    }

    @Override
    public void reportThrowIgnored(PlayerColour colour) {
        out.printf("%s does not have other pieces in the board to move instead of the blocked "
                + "piece. Ignoring the throw and moving on to the next player.%n",
                colour.getDisplayName());
    }

    @Override
    public void reportMovedUpToBlock(PlayerColour colour, CandidateMove partialMove) {
        out.printf("%s does not have other pieces in the board to move instead of the blocked "
                + "piece. Moved the piece to square %s which is the cell before the block.%n",
                colour.getDisplayName(), partialMove.getDestination().getLabel());
    }

    @Override
    public void reportCapture(GamePiece capturer, GamePiece captured, String squareLabel) {
        out.printf("%s piece %s lands on square %s, captures %s piece %s, and returns it to the "
                        + "base.%n", capturer.getColour().getDisplayName(), capturer.getName(), squareLabel,
                captured.getColour().getDisplayName(), captured.getName());
    }

    @Override
    public void reportMysteryCellSpawn(int cell) {
        out.printf("A mystery cell has spawned in location %d and will be at this location for the "
                + "next four rounds.%n", cell);
    }

    @Override
    public void reportMysteryCellStatus(MysteryCellScheduler mysteryCell) {
        if (mysteryCell.isActive()) {
            out.printf("The mystery cell is at %d and will be at that location for the next %d "
                    + "values.%n", mysteryCell.getCell(), mysteryCell.getRoundsRemaining());
        }
    }

    @Override
    public void reportMysteryCellLanding(GamePiece piece, TeleportTarget destination) {
        out.printf("%s player lands on a mystery cell and is teleported to %s.%n",
                piece.getColour().getDisplayName(), destination.resolveSquare(piece.getColour()).getLabel());
    }

    @Override
    public void reportTeleport(GamePiece piece, TeleportTarget destination) {
        out.printf("%s piece %s teleported to %s.%n", piece.getColour().getDisplayName(), piece.getName(),
                destination.getDisplayName());
    }

    @Override
    public void reportAlphaAura(GamePiece piece, MovementModifier modifier) {
        String effect = modifier == MovementModifier.DOUBLED
                ? "feels energized, and movement speed doubles"
                : "feels sick, and movement speed halves";
        out.printf("%s piece %s %s.%n", piece.getColour().getDisplayName(), piece.getName(), effect);
    }

    @Override
    public void reportBetaBriefing(GamePiece piece) {
        out.printf("%s piece %s attends briefing and cannot move for four rounds.%n",
                piece.getColour().getDisplayName(), piece.getName());
    }

    @Override
    public void reportBriefingEscape(GamePiece piece) {
        out.printf("%s piece %s is movement-restricted and has rolled three consecutively. "
                        + "Teleporting piece %s to base.%n", piece.getColour().getDisplayName(),
                piece.getName(), piece.getName());
    }

    @Override
    public void reportGammaReversal(GamePiece piece) {
        out.printf("The %s piece %s, which was moving clockwise, has changed to moving "
                + "counterclockwise.%n", piece.getColour().getDisplayName(), piece.getName());
    }

    @Override
    public void reportGammaToBeta(GamePiece piece) {
        out.printf("The %s piece %s is moving in a counterclockwise direction. Teleporting to Beta "
                + "from Gamma.%n", piece.getColour().getDisplayName(), piece.getName());
    }

    @Override
    public void reportPieceCounts(PlayerStatusSnapshot status) {
        out.printf("%s player now has %d/%d on pieces on the board and %d/%d pieces on the base.%n",
                status.colour().getDisplayName(), status.countOnBoard(),
                BoardSpecification.PIECES_PER_COLOUR, status.countInBase(),
                BoardSpecification.PIECES_PER_COLOUR);
    }

    @Override
    public void reportPieceLocations(PlayerStatusSnapshot status) {
        out.println(DIVIDER);
        out.printf("Location of pieces %s%n", status.colour().getDisplayName());
        out.println(DIVIDER);
        for (PlayerStatusSnapshot.PieceSnapshot piece : status.pieces()) {
            out.printf("Piece %s -> %s.%n", piece.name(), piece.square().getLabel());
        }
    }

    @Override
    public void reportRoundEnd() {
        printBlankLine();
    }

    @Override
    public void announceWinner(PlayerColour colour) {
        printBlankLine();
        out.printf("%s player wins!!!%n", colour.getDisplayName());
    }

    @Override
    public void announceFinalStandings(List<PlayerColour> placings) {
        printBlankLine();
        out.println(DIVIDER);
        out.println("Final standings");
        out.println(DIVIDER);
        for (int index = 0; index < placings.size(); index++) {
            out.printf("%s place: %s%n", PLACES[index], placings.get(index).getDisplayName());
        }
    }

    @Override
    public void reportRoundLimitReached(int roundLimit, List<PlayerStatusSnapshot> statuses) {
        printBlankLine();
        out.printf("The simulation reached its safety limit of %d rounds and was stopped.%n",
                roundLimit);
        listUnfinishedPlayers(statuses);
    }

    @Override
    public void reportGridlock(int round, int stillRounds, List<PlayerStatusSnapshot> statuses) {
        printBlankLine();
        out.printf("No piece has moved for %d rounds: the blocks on the board leave no legal move, "
                + "so the game ends after round %d.%n", stillRounds, round);
        listUnfinishedPlayers(statuses);
    }

    private void listUnfinishedPlayers(List<PlayerStatusSnapshot> statuses) {
        for (PlayerStatusSnapshot status : statuses) {
            if (!status.isFinished()) {
                out.printf("Unfinished: %s with %d/%d pieces home%n", status.colour().getDisplayName(),
                        status.countAtHome(), BoardSpecification.PIECES_PER_COLOUR);
            }
        }
    }

    private void printBlankLine() {
        out.println();
    }
}
