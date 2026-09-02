package ludot.mystery;

import java.util.List;
import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.Square;
import ludot.piece.Piece;
import ludot.piece.SpeedModifier;
import ludot.random.RandomSource;
import ludot.ui.GameLog;

/**
 * What happens to a piece that lands on the mystery cell (Rules T-11 to T-15).
 *
 * <p>Rule&nbsp;T-11 picks one of six destinations at random and teleports the piece there.
 * Three of those destinations then have a lasting effect of their own:
 *
 * <ul>
 *   <li><b>Alpha</b> (T-12) - the aura either doubles or halves the piece's rolls for four
 *       rounds;</li>
 *   <li><b>Beta</b> (T-13) - the piece attends a briefing and cannot move for four rounds;</li>
 *   <li><b>Gamma</b> (T-14) - a clockwise piece is turned around, while a counter-clockwise piece is
 *       sent on to Beta instead.</li>
 * </ul>
 *
 * <p>Rule&nbsp;T-15 says these effects exist only when the piece <em>was teleported by a mystery
 * cell</em>. That is guaranteed structurally here: the only public entry point is
 * {@link #resolveLandingOnMysteryCell(Piece)}, so a piece that merely walks onto cell 7, 25 or 44
 * can never trigger them.
 */
public final class MysteryEffectResolver {

    private static final List<TeleportDestination> DESTINATIONS =
            List.of(TeleportDestination.values());

    private final Board board;
    private final RandomSource randomSource;
    private final GameLog log;

    public MysteryEffectResolver(Board board, RandomSource randomSource, GameLog log) {
        this.board = board;
        this.randomSource = randomSource;
        this.log = log;
    }

    /** Rule T-11: pick one of the six destinations at random and send the piece there. */
    public void resolveLandingOnMysteryCell(Piece piece) {
        TeleportDestination destination = randomSource.pick(DESTINATIONS);
        log.landsOnMysteryCell(piece, destination);
        teleport(piece, destination);
        applyDestinationEffect(piece, destination);
    }

    /** Moves the piece to a teleport destination without any of the walking rules applying. */
    private void teleport(Piece piece, TeleportDestination destination) {
        Square target = destination.squareFor(piece.colour());
        board.relocate(piece, target);
        log.teleported(piece, destination);

        if (destination == TeleportDestination.BASE) {
            // A piece in the base carries no direction and no history, exactly as after a capture
            // (Rule T-9); it will be tossed a fresh coin when it next steps out onto "X".
            piece.resetAfterCapture();
        } else if (target.isApproachCellOf(piece.colour())) {
            // Arriving on the approach cell counts as a visit for Rule T-1, however the piece got
            // there, so a teleport to "Approach" is not silently wasted.
            piece.recordApproachPass();
        }
    }

    private void applyDestinationEffect(Piece piece, TeleportDestination destination) {
        switch (destination) {
            case ALPHA -> applyAlphaAura(piece);
            case BETA -> applyBetaBriefing(piece);
            case GAMMA -> applyGammaClarification(piece);
            case BASE, START, APPROACH -> {
                // Rule T-11 destinations 4, 5 and 6 relocate the piece but leave no lasting effect.
            }
        }
    }

    /** Rule T-12: "the piece may get energised by the aura or get sick due to the aura." */
    private void applyAlphaAura(Piece piece) {
        SpeedModifier modifier =
                randomSource.nextBoolean() ? SpeedModifier.DOUBLED : SpeedModifier.HALVED;
        piece.effects().applyAlphaAura(modifier);
        log.alphaAura(piece, modifier);
    }

    /** Rule T-13: the piece has to attend a briefing and cannot move for the next four rounds. */
    private void applyBetaBriefing(Piece piece) {
        piece.effects().beginBriefing();
        log.betaBriefing(piece);
    }

    /**
     * Rule T-14: "if it is moving in a clockwise direction, it will change its direction to
     * counterclockwise. If it were moving in the counterclockwise direction, it would be teleported
     * to Beta."
     */
    private void applyGammaClarification(Piece piece) {
        if (piece.direction() == Direction.CLOCKWISE) {
            piece.setDirection(Direction.COUNTER_CLOCKWISE);
            log.gammaTurnedPieceAround(piece);
            return;
        }
        log.gammaSendsPieceToBeta(piece);
        teleport(piece, TeleportDestination.BETA);
        applyBetaBriefing(piece);
    }
}
