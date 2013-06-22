/**
 * @author TJ Goff goff.tom@gmail.com
 * @version 1.0.0
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License version 2.1 as published
 * by the Free Software Foundation
 *
 * This interface defines common functionality for Valuation functions, which
 * assign scores to combinations of goods.  Clients should know the Valuation
 * function by which their performance will be scored.
 */

package jack.valuations;

import java.util.List;

public interface Valuation {

        /**
         * Read parameters from a config file and initialize the Valuation
         * object can be used to generate scoring functions for clients, and to
         * calculate scores.  @param configFile The name of the configuration
         * file which describes the valuation function, or a distribution of
         * functions.
	 */
        void initialize(String configFile);

        /**
         * Generate a Valuation scoring function and encode it in a string.
         * @return String which encodes a scoring function.
	 */
        String generateScoringFunction();

        /**
         * Given a list of goods and scoring function, calculate the score.
         * @param  scoreFunct  String encoding a scoring function.  @param goods
         * List of goods for which to calculate a score.  @return the score
         * achieved with a combination of goods under a scoring function.
	 */
        double getScore(String scoreFunct, List<String> goods);
}
