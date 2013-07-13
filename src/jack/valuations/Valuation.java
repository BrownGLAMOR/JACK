/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jack.valuations;

import java.util.List;

/**
 * @author TJ Goff goff.tom@gmail.com
 * @version 1.0.0
 * This interface defines common functionality for Valuation functions, which
 * assign scores to combinations of goods.  Clients should know the Valuation
 * function by which their performance will be scored.
 */
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
