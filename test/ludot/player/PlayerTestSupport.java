package ludot.player;

import ludot.board.Board;
import ludot.movement.MoveGenerator;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;

/** Asks a player to choose among the real legal moves for a roll. */
final class PlayerTestSupport {

    private PlayerTestSupport() {
        // Static helpers only.
    }

    static PlannedMove choiceOf(Player player, Board board, int roll) {
        MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));
        return player.chooseMove(generator.optionsFor(player.colour(), roll), roll).orElseThrow();
    }
}
