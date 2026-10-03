package ludot.command;

import java.util.ArrayList;
import java.util.Optional;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.movement.CandidateMove;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportService;
import ludot.ui.GameEventReporter;

/** Template Method: every move arrives, captures, then checks the mystery cell, in that order. */
public abstract class AbstractMoveCommand implements TurnCommand {

    private final CandidateMove move;
    private final GameBoard board;
    private final MysteryCellScheduler mysteryCell;
    private final TeleportService teleporter;
    private final GameEventReporter log;

    protected AbstractMoveCommand(CandidateMove move, GameBoard board, MysteryCellScheduler mysteryCell,
            TeleportService teleporter, GameEventReporter log) {
        this.move = move;
        this.board = board;
        this.mysteryCell = mysteryCell;
        this.teleporter = teleporter;
        this.log = log;
    }

    @Override
    public final boolean execute() {
        arrive();
        boolean captured = captureOpponents();
        teleportPiecesOnTheMysteryCell();
        return captured;
    }

    @Override
    public Optional<CandidateMove> getPlayedMove() {
        return Optional.of(move);
    }

    protected abstract void arrive();

    protected CandidateMove getMove() {
        return move;
    }

    protected GameBoard getBoard() {
        return board;
    }

    protected GameEventReporter getLog() {
        return log;
    }

    private boolean captureOpponents() {
        if (!move.capturesAnything()) {
            return false;
        }
        GamePiece capturer = move.getPrimaryPiece();
        for (GamePiece captured : new ArrayList<>(move.capturedPieces())) {
            board.relocate(captured, BoardSquare.ofBase(captured.getColour()));
            captured.resetAfterCapture();
            log.reportCapture(capturer, captured, move.getDestination().getLabel());
        }
        if (move.isBlockMove()) {
            // T-8: every piece of a capturing blockade gets one capture.
            move.getMovedPieces().forEach(GamePiece::recordCapture);
        } else {
            move.capturedPieces().forEach(victim -> capturer.recordCapture());
        }
        log.reportPieceCounts(board.createSnapshot(capturer.getColour()));
        return true;
    }

    private void teleportPiecesOnTheMysteryCell() {
        BoardSquare destination = move.getDestination();
        if (!mysteryCell.isOn(destination)) {
            return;
        }
        move.getMovedPieces().forEach(teleporter::resolveLandingOnMysteryCell);
    }
}
