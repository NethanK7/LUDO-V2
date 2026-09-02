package ludot.random;

import java.util.List;

/**
 * The single source of chance in the simulation.
 *
 * <p>Everything random - the dice, the coin toss of Rule T-1, where the mystery cell spawns, which
 * of the six teleport destinations is picked - goes through this interface rather than calling
 * {@code Math.random()} in a dozen places.
 *
 * <p>That is the Dependency Inversion Principle at work: the game rules depend on this abstraction,
 * not on {@code java.util.Random}. Because of it the whole simulation can be replayed exactly by
 * supplying a seeded implementation, or driven by a scripted stub in a unit test.
 */
public interface RandomSource {

    /** A value in {@code [0, boundExclusive)}. */
    int nextInt(int boundExclusive);

    /** A fair true/false, used for the coin toss and for the Alpha aura outcome. */
    boolean nextBoolean();

    /** Picks one element of a non-empty list uniformly at random. */
    default <T> T pick(List<T> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Cannot pick from an empty list");
        }
        return candidates.get(nextInt(candidates.size()));
    }
}
