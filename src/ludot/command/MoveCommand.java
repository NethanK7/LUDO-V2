package ludot.command;

import java.util.ArrayList;
import java.util.Optional;
import ludot.board.Board;
import ludot.board.Piece;
import ludot.board.Square;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.mystery.MysteryEffectResolver;
import ludot.ui.GameListener;

/**
 * A command that really moves pieces (Template Method pattern).
 *
 * <p>{@link #execute()} fixes the order that every move follows, as the rules imply:
 *
 * <ol>
 *   <li>the pieces arrive - the one step that differs, written by each subclass;</li>
 *   <li>opponent pieces on the destination are captured and reset (Rules 6, T-8 and T-9);</li>
 *   <li>a piece that landed on the mystery cell is teleported (Rules T-10 and T-11).</li>
 * </ol>
 *
 * <p>Because {@code execute} is {@code final}, no kind of move can skip the capture or the teleport.
 */
public abstract class MoveCommand implements GameCommand {

    private final PlannedMove move;
    private final Board board;
    private final MysteryCell mysteryCell;
    private final MysteryEffectResolver mysteryEffects;
    private final GameListener log;

    protected MoveCommand(PlannedMove move, Board board, MysteryCell mysteryCell,
            MysteryEffectResolver mysteryEffects, GameListener log) {
        this.move = move;
        this.board = board;
        this.mysteryCell = mysteryCell;
        this.mysteryEffects = mysteryEffects;
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
    public Optional<PlannedMove> playedMove() {
        return Optional.of(move);
    }

    /** Moves the pieces to the destination and prints the matching message. */
    protected abstract void arrive();

    protected PlannedMove move() {
        return move;
    }

    protected Board board() {
        return board;
    }

    protected GameListener log() {
        return log;
    }

    /**
     * Rules 6, T-8 and T-9: opponents on the destination go back to their base with all of their
     * information reset, and the arriving pieces record the capture that Rule T-7 needs.
     */
    private boolean captureOpponents() {
        if (!move.capturesAnything()) {
            return false;
        }
        Piece capturer = move.primaryPiece();
        for (Piece captured : new ArrayList<>(move.capturedPieces())) {
            board.relocate(captured, Square.base(captured.colour()));
            captured.resetAfterCapture();
            log.capture(capturer, captured, move.destination().label());
        }
        if (move.isBlockMove()) {
            // Rule T-8: every piece of the capturing blockade gets one more capture.
            move.movedPieces().forEach(Piece::recordCapture);
        } else {
            // Rule 6: the single arriving piece is credited with every piece it removed.
            move.capturedPieces().forEach(victim -> capturer.recordCapture());
        }
        log.playerPieceCounts(board.statusOf(capturer.colour()));
        return true;
    }

    /** Rule T-11: the teleport comes last, so a piece can capture on the mystery cell first. */
    private void teleportPiecesOnTheMysteryCell() {
        Square destination = move.destination();
        if (!mysteryCell.isOn(destination)) {
            return;
        }
        move.movedPieces().forEach(mysteryEffects::resolveLandingOnMysteryCell);
    }
}
