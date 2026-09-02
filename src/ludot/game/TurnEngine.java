package ludot.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ludot.board.Board;
import ludot.board.Square;
import ludot.movement.BlockedAttempt;
import ludot.movement.MoveExecutor;
import ludot.movement.MoveGenerator;
import ludot.movement.MoveOptions;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.piece.Piece;
import ludot.player.Player;
import ludot.random.Dice;
import ludot.ui.GameLog;

/**
 * Plays one player's whole turn: the roll, the extra rolls it may earn, and the move it makes.
 *
 * <p>A turn is more than one roll. Rule&nbsp;4 grants a second and third roll after a six,
 * Rule&nbsp;T-2 grants another roll for every capture, and Rule&nbsp;T-6 turns a third consecutive
 * six into a forced break-up of the player's blockade. This class owns that structure and nothing
 * else - deciding <em>which</em> move to play belongs to the {@link Player}, and checking whether a
 * move is legal belongs to {@link MoveGenerator}.
 */
public final class TurnEngine {

    private final Board board;
    private final Dice dice;
    private final MoveGenerator moveGenerator;
    private final MoveExecutor moveExecutor;
    private final PathResolver pathResolver;
    private final GameLog log;

    public TurnEngine(Board board, Dice dice, MoveGenerator moveGenerator,
            MoveExecutor moveExecutor, PathResolver pathResolver, GameLog log) {
        this.board = board;
        this.dice = dice;
        this.moveGenerator = moveGenerator;
        this.moveExecutor = moveExecutor;
        this.pathResolver = pathResolver;
        this.log = log;
    }

    /** Rolls, moves, and keeps rolling for as long as Rules 4 and T-2 allow. */
    public void playTurn(Player player) {
        int consecutiveSixes = 0;

        for (int rollNumber = 1; rollNumber <= GameRules.MAX_ROLLS_PER_TURN; rollNumber++) {
            int value = dice.roll();
            log.diceRolled(player.colour(), value);

            player.recordRoll(value);
            releaseBriefedPiecesOnConsecutiveThrees(player);

            if (value == Dice.SIX) {
                consecutiveSixes++;
                if (consecutiveSixes == GameRules.MAX_CONSECUTIVE_SIXES) {
                    handleThirdConsecutiveSix(player);
                    return;
                }
            } else {
                consecutiveSixes = 0;
            }

            boolean captured = playSingleRoll(player, value);
            if (captured) {
                // Rule T-2: "allowing the capturing player another roll as a bonus for capturing".
                log.captureEarnsAnotherRoll(player.colour());
            }
            boolean earnedAnotherRoll = value == Dice.SIX || captured;
            if (!earnedAnotherRoll) {
                return;
            }
        }
    }

    /**
     * Uses one dice value: generate the legal moves, let the player choose, and carry it out.
     *
     * @return whether the move captured an opponent piece.
     */
    private boolean playSingleRoll(Player player, int value) {
        MoveOptions options = moveGenerator.optionsFor(player.colour(), value);
        PlannedMove chosen = player.chooseMove(options, value);
        if (chosen != null) {
            return applyMove(player, chosen);
        }
        return handleRollThatCannotBePlayed(player, options);
    }

    /**
     * Rules 7 and T-3: with nothing playable, the player either shuffles the blocked piece up to the
     * cell before the block or loses the throw altogether.
     */
    private boolean handleRollThatCannotBePlayed(Player player, MoveOptions options) {
        if (!options.hasBlockedAttempt()) {
            log.rollCannotBeUsed(player.colour());
            return false;
        }

        BlockedAttempt attempt = options.blockedAttempts().get(0);
        log.pieceIsBlocked(attempt);
        if (attempt.hasPartialMove()) {
            log.blockedButMovedUpToTheBlock(player.colour(), attempt.partialMove());
            return applyMove(player, attempt.partialMove());
        }
        log.blockedWithNothingElseToMove(player.colour());
        return false;
    }

    private boolean applyMove(Player player, PlannedMove move) {
        boolean captured = moveExecutor.execute(move);
        player.onMoveExecuted(move);
        return captured;
    }

    /**
     * Rule T-13: "during the next four rounds, the piece will be teleported to the base if the
     * player rolls value three consecutively."
     */
    private void releaseBriefedPiecesOnConsecutiveThrees(Player player) {
        if (player.consecutiveEscapeRolls() < GameRules.CONSECUTIVE_THREES_TO_LEAVE_BRIEFING) {
            return;
        }

        boolean anyReleased = false;
        for (Piece piece : board.piecesOf(player.colour())) {
            if (piece.effects().isAttendingBriefing()) {
                log.briefingEndedByConsecutiveThrees(piece);
                board.relocate(piece, Square.base(piece.colour()));
                piece.resetAfterCapture();
                anyReleased = true;
            }
        }
        if (anyReleased) {
            player.clearConsecutiveEscapeRolls();
        }
    }

    /**
     * Rule 4 says the third consecutive six is simply ignored - unless Rule&nbsp;T-6 applies, in
     * which case the player must first break up every blockade it holds by moving all but one of
     * each blockade's pieces "in their original direction by six units cumulatively".
     */
    private void handleThirdConsecutiveSix(Player player) {
        List<Square> blockades = board.blockSquaresOf(player.colour());
        if (blockades.isEmpty()) {
            log.thirdSixIgnored(player.colour());
            return;
        }

        for (Square blockade : blockades) {
            List<Piece> pieces = board.groupOn(blockade, player.colour());
            if (pieces.size() < Board.MINIMUM_BLOCK_SIZE) {
                // An earlier break-up in this same turn has already dissolved this blockade.
                continue;
            }
            log.blockadeMustBeBroken(player.colour(), blockade.label(), pieces.size());
            breakUpBlockade(player, pieces);
        }
    }

    /** Moves every piece of the blockade except the one closest to home. */
    private void breakUpBlockade(Player player, List<Piece> blockade) {
        List<Piece> leaving = piecesLeavingTheBlockade(blockade);
        int stepsEach = GameRules.BLOCKADE_BREAK_UNITS / leaving.size();

        for (Piece piece : leaving) {
            PlannedMove move = moveGenerator.forcedMove(piece, piece.initialDirection(), stepsEach);
            if (move == null) {
                log.rollCannotBeUsed(player.colour());
                continue;
            }
            applyMove(player, move);
        }
    }

    /**
     * "removing all pieces, baring one" - the piece with the shortest journey left is the one kept
     * in place, because it is the one that gains least from being pushed on.
     */
    private List<Piece> piecesLeavingTheBlockade(List<Piece> blockade) {
        List<Piece> ordered = new ArrayList<>(blockade);
        ordered.sort(Comparator.comparingInt(pathResolver::distanceToHome));
        return ordered.subList(1, ordered.size());
    }
}
