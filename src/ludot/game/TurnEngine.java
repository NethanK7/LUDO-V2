package ludot.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ludot.board.Board;
import ludot.board.Piece;
import ludot.board.Square;
import ludot.command.CommandFactory;
import ludot.command.GameCommand;
import ludot.movement.MoveGenerator;
import ludot.movement.MoveOptions;
import ludot.movement.PathResolver;
import ludot.player.Player;
import ludot.random.Dice;
import ludot.ui.GameListener;

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
    private final CommandFactory commands;
    private final PathResolver pathResolver;
    private final GameListener log;

    public TurnEngine(Board board, Dice dice, MoveGenerator moveGenerator,
            CommandFactory commands, PathResolver pathResolver, GameListener log) {
        this.board = board;
        this.dice = dice;
        this.moveGenerator = moveGenerator;
        this.commands = commands;
        this.pathResolver = pathResolver;
        this.log = log;
    }

    /** Rolls, moves, and keeps rolling for as long as Rules 4 and T-2 allow. */
    public void playTurn(Player player) {
        int consecutiveSixes = 0;
        boolean rollAgain = true;

        while (rollAgain) {
            int value = dice.roll();
            log.diceRolled(player.colour(), value);

            releaseBriefedPiecesOnConsecutiveThrees(player, value);

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
            // Rule T-2: "allowing the capturing player another roll as a bonus for capturing"
            // A player whose last piece has just reached home has nothing left to roll for.
            rollAgain = (value == Dice.SIX || captured) && !board.hasAllPiecesHome(player.colour());
        }
    }

    /**
     * Uses one dice value: list the legal moves, let the player choose, and run the command for it.
     *
     * @return whether the move captured an opponent piece.
     */
    private boolean playSingleRoll(Player player, int value) {
        MoveOptions options = moveGenerator.optionsFor(player.colour(), value);
        GameCommand command = player.chooseMove(options)
                .map(commands::create)
                .orElseGet(() -> commandForUnusableRoll(options));
        return run(player, command);
    }

    /**
     * Rules 7 and T-3: with nothing playable, a blocked piece either moves up to the cell before the
     * block or the throw is lost. A blocked piece that can still move up is preferred.
     */
    private GameCommand commandForUnusableRoll(MoveOptions options) {
        return options.blockedAttempts().stream()
                .sorted(Comparator.comparing(attempt -> attempt.partialMove().isEmpty()))
                .findFirst()
                .map(commands::createForBlocked)
                .orElseGet(commands::noMove);
    }

    private boolean run(Player player, GameCommand command) {
        boolean captured = command.execute();
        command.playedMove().ifPresent(player::onMoveExecuted);
        return captured;
    }

    /**
     * Rule T-13: "during the next four rounds, the piece will be teleported to the base if the
     * player rolls value three consecutively." Each briefed piece keeps its own count of the rolls
     * made since its briefing began.
     */
    private void releaseBriefedPiecesOnConsecutiveThrees(Player player, int value) {
        for (Piece piece : board.piecesOf(player.colour())) {
            piece.effects().observeRoll(value);
            if (piece.effects().mustLeaveBriefingForBase()) {
                log.briefingEndedByConsecutiveThrees(piece);
                board.relocate(piece, Square.base(piece.colour()));
                piece.resetAfterCapture();
            }
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
            return;
        }

        for (Square blockade : blockades) {
            List<Piece> pieces = board.groupOn(blockade, player.colour());
            breakUpBlockade(player, pieces);
        }
    }

    /**
     * Moves every piece of the blockade except the one closest to home, sharing the six units out
     * as {@link GameRules#BLOCKADE_BREAK_SHARES} describes so the leaving pieces never land together.
     */
    private void breakUpBlockade(Player player, List<Piece> blockade) {
        List<Piece> leaving = piecesLeavingTheBlockade(blockade);
        List<Integer> shares = GameRules.BLOCKADE_BREAK_SHARES.get(leaving.size() - 1);

        for (int index = 0; index < leaving.size(); index++) {
            Piece piece = leaving.get(index);
            int units = shares.get(index);
            moveGenerator.forcedMove(piece, piece.initialDirection(), units)
                    .ifPresent(forcedMove -> run(player, commands.create(forcedMove)));
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
