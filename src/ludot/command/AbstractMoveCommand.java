package ludot.command;

import java.util.ArrayList;
import java.util.Optional;
import ludot.board.BoardSquare;
import ludot.board.GamePiece;
import ludot.movement.CandidateMove;

/** Template Method: every move arrives, captures, then checks the mystery cell, in that order. */
public abstract class AbstractMoveCommand implements TurnCommand {

    private final CandidateMove move;
    private final CommandContext context;

    AbstractMoveCommand(CandidateMove move, CommandContext context) {
        this.move = move;
        this.context = context;
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

    CommandContext getContext() {
        return context;
    }

    private boolean captureOpponents() {
        if (!move.capturesAnything()) {
            return false;
        }
        GamePiece capturer = move.getPrimaryPiece();
        for (GamePiece captured : new ArrayList<>(move.capturedPieces())) {
            context.board().relocate(captured, BoardSquare.ofBase(captured.getColour()));
            captured.resetAfterCapture();
            context.log().reportCapture(capturer, captured, move.getDestination().getLabel());
        }
        if (move.isBlockMove()) {
            // T-8: every piece of a capturing blockade gets one capture.
            move.getMovedPieces().forEach(GamePiece::recordCapture);
        } else {
            move.capturedPieces().forEach(victim -> capturer.recordCapture());
        }
        context.log().reportPieceCounts(context.board().createSnapshot(capturer.getColour()));
        return true;
    }

    private void teleportPiecesOnTheMysteryCell() {
        if (!context.mysteryCell().isOn(move.getDestination())) {
            return;
        }
        move.getMovedPieces().forEach(context.mysteryTeleporter()::resolveLandingOnMysteryCell);
    }
}
