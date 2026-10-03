package ludot.player;

import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.MoveGenerator;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.random.SeededRandomSource;

/** Builds a player the way the game does, and asks it to choose among the real legal moves. */
final class PlayerTestSupport {

    private PlayerTestSupport() {
        // Static helpers only.
    }

    /** The player for a colour, with its real rule chain; the mystery cell has not spawned. */
    static Player playerFor(Board board, PieceColour colour) {
        MysteryCell noMysteryCellYet = new MysteryCell(board, new SeededRandomSource(1));
        return new PlayerFactory(board, new PathResolver(board), noMysteryCellYet).create(colour);
    }

    static PlannedMove choiceOf(Player player, Board board, int roll) {
        MoveGenerator generator = new MoveGenerator(board, new PathResolver(board));
        return player.chooseMove(generator.optionsFor(player.colour(), roll)).orElseThrow();
    }
}
