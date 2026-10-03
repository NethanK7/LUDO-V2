package ludot.player;

import ludot.board.GameBoard;
import ludot.board.PlayerColour;
import ludot.movement.CandidateMove;
import ludot.movement.MoveOptionFinder;
import ludot.movement.PathNavigator;
import ludot.mystery.MysteryCellScheduler;
import ludot.random.SeededRandomnessProvider;

/** Builds a player the same way the game does, for the player tests. */
final class PlayerTestHelper {

    private PlayerTestHelper() {
    }

    static GamePlayer createPlayer(GameBoard board, PlayerColour colour) {
        MysteryCellScheduler noMysteryCellYet = new MysteryCellScheduler(board, new SeededRandomnessProvider(1));
        return new GamePlayerFactory(board, new PathNavigator(board), noMysteryCellYet).create(colour);
    }

    static CandidateMove chooseMoveFor(GamePlayer player, GameBoard board, int roll) {
        MoveOptionFinder moveFinder = new MoveOptionFinder(board, new PathNavigator(board));
        return player.chooseMove(moveFinder.findOptions(player.getColour(), roll)).orElseThrow();
    }
}
