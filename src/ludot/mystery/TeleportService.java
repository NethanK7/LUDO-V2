package ludot.mystery;

import java.util.List;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.board.TravelDirection;
import ludot.effects.MovementModifier;
import ludot.random.RandomnessProvider;

/** Rules T-11 to T-15: where a piece on the mystery cell is sent, and what happens there. */
public final class TeleportService {

    private static final List<TeleportTarget> DESTINATIONS =
            List.of(TeleportTarget.values());

    private final GameBoard board;
    private final RandomnessProvider randomSource;
    private final TeleportEventReporter log;

    public TeleportService(GameBoard board, RandomnessProvider randomSource, TeleportEventReporter log) {
        this.board = board;
        this.randomSource = randomSource;
        this.log = log;
    }

    public void resolveLandingOnMysteryCell(GamePiece piece) {
        TeleportTarget destination = randomSource.pick(DESTINATIONS);
        log.reportMysteryCellLanding(piece, destination);
        teleport(piece, destination);
        applyDestinationEffect(piece, destination);
    }

    private void teleport(GamePiece piece, TeleportTarget destination) {
        BoardSquare target = destination.resolveSquare(piece.getColour());
        board.relocate(piece, target);
        log.reportTeleport(piece, destination);

        if (destination == TeleportTarget.BASE) {
            piece.resetAfterCapture();
        } else if (target.isApproachCellOf(piece.getColour())) {
            piece.recordApproachPass();
        }
    }

    private void applyDestinationEffect(GamePiece piece, TeleportTarget destination) {
        switch (destination) {
            case ALPHA -> applyAlphaAura(piece);
            case BETA -> applyBetaBriefing(piece);
            case GAMMA -> applyGammaClarification(piece);
            case BASE, START, APPROACH -> {
            }
        }
    }

    private void applyAlphaAura(GamePiece piece) {
        MovementModifier modifier =
                randomSource.generateBoolean() ? MovementModifier.DOUBLED : MovementModifier.HALVED;
        piece.getEffects().applyAlphaAura(modifier);
        log.reportAlphaAura(piece, modifier);
    }

    private void applyBetaBriefing(GamePiece piece) {
        piece.getEffects().beginBriefing();
        log.reportBetaBriefing(piece);
    }

    private void applyGammaClarification(GamePiece piece) {
        if (piece.getDirection() == TravelDirection.CLOCKWISE) {
            piece.setDirection(TravelDirection.COUNTER_CLOCKWISE);
            log.reportGammaReversal(piece);
            return;
        }
        log.reportGammaToBeta(piece);
        teleport(piece, TeleportTarget.BETA);
        applyBetaBriefing(piece);
    }
}
