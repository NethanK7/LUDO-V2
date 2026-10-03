package ludot.game;

import java.util.List;
import ludot.board.PieceColour;

/**
 * How a game ended (Data Transfer Object).
 *
 * @param placings the colours that have a place, best first. All four places for a normal game; only
 *                 the players that really finished when the game was cut short.
 * @param rounds   the number of rounds played.
 */
public record GameResult(List<PieceColour> placings, Ending ending, int rounds) {

    /** Why the game stopped. */
    public enum Ending {
        /** Three players brought every piece home, so the fourth place follows by elimination. */
        ALL_PLACES_DECIDED,
        /** Blocks froze the board: no piece moved for {@link GameRules#GRIDLOCK_ROUNDS} rounds. */
        GRIDLOCK,
        /** The safety limit of {@link GameRules#MAX_ROUNDS} rounds was reached. */
        ROUND_LIMIT
    }

    public GameResult {
        placings = List.copyOf(placings);
    }
}
