package foodassistant.agent;

import foodassistant.model.Recommendation;
import foodassistant.model.Restaurant;

import java.util.List;

/** Swaps one way of ordering restaurants for another. */
public interface RankingStrategy {
    List<Recommendation> rank(List<Restaurant> restaurants, RankContext context);
}
