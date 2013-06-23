/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jack.valuations;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

/**
 * @author TJ Goff  goff.tom@gmail.com
 * @version 1.0.0
 *
 * This class defines a Valuation function by assigning a linear and/or an
 * exponential scoring factor to the number of a type of good owned by a client.
 * If the client has multiple types of goods, the value of each type is computed
 * separately, and the total score awarded to the player is the sum of these
 * values.  That is, the marginal value of a good is independent of number of
 * goods a client has of other types, though the exponential factor for the
 * particular type of good under consideration allows for complementarity or
 * substitutability (marginal goods of the same type become worth more or less
 * depending on how many of that type of good the client already has).
 */
public class AdditiveValuation implements Valuation
{
    //Stores parameters from config file. "name_of_good" -> [params]
    Hashtable<String, Hashtable<String, String>> params;

    public AdditiveValuation(String configFile){
            this.initialize(configFile);
    }

    /**
     * Read config file to get parameters for generating additive
     * valuation functions. The config file should have 1 line per
     * type of good, and all parameters for that type of good should
     * be defined on that line(else default values used).  There are
     * 5 values that can be specified for a type of good: The name of
     * the type of good, a lower bound (double) for a linear constant,
     * an upper bound (double) for the linear constant, a lower bound
     * (double) for an exponential factor, and an upper bound (double)
     * for the exponential factor.
     * A client's score is obtained by adding the score achieved for
     * each type of good.  Each type of good gets a score by applying
     * the exponential factor to the number of goods owned, and then
     * multiplying by the linear constant.  For example:
     * score = (L0*(G0^E0)) + (L1*(G1^E1)) + (L2*(G2^E2))
     * @param configFile File containing parameters used for the
     * Valuation function generating and scoring functions.
     */
    public void initialize(String configFile) {
        params = new Hashtable<String, Hashtable<String, String>>();
        try {
            DataInputStream in = new DataInputStream(new FileInputStream("src/valuations/"+configFile));
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;

            while ((strLine = br.readLine()) != null) {//read config file line-by-line
                if (strLine.isEmpty()){
                    continue;//skip blank lines
                }

                //Split line from config file by whitespace (delimiter)
                String[] currParams = strLine.split("\\s+");
                Hashtable<String, String> subParams = new Hashtable<String, String>();
                for (String curr : currParams) {
                    String[] parts = curr.split(":");
                    subParams.put(parts[0], parts[1]);
                }
                params.put(subParams.get("Good"), subParams);
            }
            in.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * @return A string which encodes the parameters for an additive
     * valuation function which maps combinations of goods to score
     * values.  Within the string, there is a 3-tuple for each type
     * of good, and these are separated by spaces.  Within each
     * 3-tuple, the 3 values are separated by "," (commas), and
     * represent the name of the type of good, linear constant
     * and the exponential factor used to calculate a score based on
     * the number of that good a client owns.
     */
    public String generateScoringFunction() {
        String scoreFunct = new String();
        Random rand = new Random();

        String[] goodTypes = new String[params.size()];
        goodTypes = params.keySet().toArray(goodTypes);
        int numGoodTypes = goodTypes.length;

        for (int i=0; i < numGoodTypes; i++) {
            String currGood = goodTypes[i];
            Hashtable<String, String> currParams = params.get(currGood);

            double lo = Double.parseDouble(currParams.get("Linear_Low_Bound"));
            double hi = Double.parseDouble(currParams.get("Linear_High_Bound"));
            double linear = (rand.nextDouble()*(hi-lo)) + lo;

            lo = Double.parseDouble(currParams.get("Exponent_Low_Bound"));
            hi = Double.parseDouble(currParams.get("Exponent_High_Bound"));
            double exponent = (rand.nextDouble()*(hi-lo)) + lo;

            //Append 3-tuple of "good,linear,exponent" to scoring function
            scoreFunct = scoreFunct + " " + currGood + "," + linear + "," + exponent;
        }
        return scoreFunct.substring(1);//remove leading " "
    }


    /**
     * Given a list of goods and a string encoding an additive valuation
     * function, return the score found by applying the function to the goods.
     * @param scoreFunct A string encoding the linear and exponential factors
     * to be applied to each type of good when calculating the score component
     * for that type of good.  These scores are added together for final score.
     * @param goods A list of goods for which to find a valuation score.
     * @return double Score calculated by applying linear and exponential
     * factors to each type of good and then summing the score components.
     */
    public double getScore(String scoreFunct, List<String> goods) {
        if (goods == null || scoreFunct == null) {
            return 0;
        }

        double value = 0;
        //Decode scoreFunct into 3-tuples, one for each good type
        String[] functParts = scoreFunct.split(" ");

        //count each type of good
        Hashtable<String, Integer> goodCounts = new Hashtable<String, Integer>();
        for (String good : goods) {
            if (goodCounts.containsKey(good)) {
                goodCounts.put(good, new Integer(goodCounts.get(good)+1));
            } else {
                goodCounts.put(good, new Integer(1));
            }
        }

        //For each type of good, calculate the component score and add to sum
        for (int i=0; i < functParts.length; i++) {
            String[] currParts = functParts[i].split(",");
            String currGood = currParts[0];
            if (goodCounts.containsKey(currGood)){
                double linear = Double.parseDouble(currParts[1]);
                double exponent = Double.parseDouble(currParts[2]);
                value += linear * Math.pow(goodCounts.get(currGood),exponent);
            }
        }
        return value;
    }
}
