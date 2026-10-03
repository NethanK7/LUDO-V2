package ludot.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.board.GamePiece;
import ludot.command.MoveCommandFactory;
import ludot.command.TurnCommand;
import ludot.movement.AvailableMoves;
import ludot.movement.MoveOptionFinder;
import ludot.movement.PathNavigator;
import ludot.player.GamePlayer;
import ludot.random.SixSidedDie;
import ludot.ui.GameEventReporter;

/** Plays one player's turn, including the extra rolls for a six or a capture. */
public final class TurnController {

    private final GameBoard board;
    private final SixSidedDie dice;
    private final MoveOptionFinder moveFinder;
    private final MoveCommandFactory commands;
    private final PathNavigator pathCalculator;
    private final GameEventReporter log;

    public TurnController(GameBoard board, SixSidedDie dice, MoveOptionFinder moveFinder,
            MoveCommandFactory commands, PathNavigator pathCalculator, GameEventReporter log) {
        this.board = board;
        this.dice = dice;
        this.moveFinder = moveFinder;
        this.commands = commands;
        this.pathCalculator = pathCalculator;
        this.log = log;
    }

    public void playTurn(GamePlayer player) {
        int consecutiveSixes = 0;
        boolean rollAgain = true;

        while (rollAgain) {
            int value = dice.roll();
            log.reportDiceRoll(player.getColour(), value);

            releaseBriefedPiecesOnConsecutiveThrees(player, value);

            if (value == SixSidedDie.SIX) {
                consecutiveSixes++;
                if (consecutiveSixes == RuleConstants.MAX_CONSECUTIVE_SIXES) {
                    handleThirdConsecutiveSix(player);
                    return;
                }
            } else {
                consecutiveSixes = 0;
            }

            boolean captured = playSingleRoll(player, value);
            // Rules 4 and T-2: a six or a capture earns another roll.
            rollAgain = (value == SixSidedDie.SIX || captured) && !board.hasAllPiecesHome(player.getColour());
        }
    }

    private boolean playSingleRoll(GamePlayer player, int value) {
        AvailableMoves options = moveFinder.findOptions(player.getColour(), value);
        TurnCommand command = player.chooseMove(options)
                .map(commands::create)
                .orElseGet(() -> createCommandForUnusableRoll(options));
        return run(player, command);
    }

    private TurnCommand createCommandForUnusableRoll(AvailableMoves options) {
        return options.blockedMoves().stream()
                .sorted(Comparator.comparing(attempt -> attempt.partialMove().isEmpty()))
                .findFirst()
                .map(commands::createForBlocked)
                .orElseGet(commands::createNoAction);
    }

    private boolean run(GamePlayer player, TurnCommand command) {
        boolean captured = command.execute();
        command.getPlayedMove().ifPresent(player::rememberMove);
        return captured;
    }

    private void releaseBriefedPiecesOnConsecutiveThrees(GamePlayer player, int value) {
        for (GamePiece piece : board.getPiecesOf(player.getColour())) {
            piece.getEffects().observeRoll(value);
            if (piece.getEffects().mustLeaveBriefingForBase()) {
                log.reportBriefingEscape(piece);
                board.relocate(piece, BoardSquare.ofBase(piece.getColour()));
                piece.resetAfterCapture();
            }
        }
    }

    private void handleThirdConsecutiveSix(GamePlayer player) {
        for (BoardSquare blockade : board.findBlockSquares(player.getColour())) {
            breakUpBlockade(player, board.getGroupOn(blockade, player.getColour()));
        }
    }

    private void breakUpBlockade(GamePlayer player, List<GamePiece> blockade) {
        List<GamePiece> leaving = selectPiecesLeavingBlockade(blockade);
        List<Integer> shares = RuleConstants.BLOCKADE_BREAK_SHARES.get(leaving.size() - 1);

        for (int index = 0; index < leaving.size(); index++) {
            GamePiece piece = leaving.get(index);
            int units = shares.get(index);
            moveFinder.planForcedMove(piece, piece.getInitialDirection(), units)
                    .ifPresent(forcedMove -> run(player, commands.create(forcedMove)));
        }
    }

    // T-6: the piece closest to home stays, the others leave.
    private List<GamePiece> selectPiecesLeavingBlockade(List<GamePiece> blockade) {
        List<GamePiece> ordered = new ArrayList<>(blockade);
        ordered.sort(Comparator.comparingInt(pathCalculator::calculateDistanceToHome));
        return ordered.subList(1, ordered.size());
    }
}
