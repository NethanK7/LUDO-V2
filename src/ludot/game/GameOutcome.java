package ludot.game;

import java.util.List;
import ludot.board.PlayerColour;

/** DTO: how a game ended and who got which place. */
public record GameOutcome(List<PlayerColour> placings, Ending ending, int rounds) {

    public enum Ending {
        ALL_PLACES_DECIDED,
        GRIDLOCK,
        ROUND_LIMIT
    }

    public GameOutcome {
        placings = List.copyOf(placings);
    }
}
