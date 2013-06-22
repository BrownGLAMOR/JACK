/**
 * @author TJ Goff  goff.tom@gmail.com
 * @version 1.0.0
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License version 2.1 as published
 * by the Free Software Foundation.
 *
 * This class defines a Schedule Valuation function.  The function specifies a
 * required number of goods, a deadline for obtaining those goods, and a
 * decaying set of valuations which are awarded for fulfilling the requirement
 * by that time.  Because the valuations decay over time, the earlier an agent
 * obtains the specified number of goods, the higher their valuation will be.
 * If the agent does not obtain enough goods before the deadline, they receive
 * no positive valuation at all.
 */

package jack.valuations;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

public class ScheduleValuation implements Valuation {

    //Stores parameters from config file. "Name_Of_Param" -> "Param"
    Hashtable<String, String> params;

    public ScheduleValuation(String configFile){
        this.initialize(configFile);
    }

    /**
     * Read config file to get parameters for generating a schedule
     * based valuation function.
     * @param configFile File containing parameters to generate
     * scheduling valuation functions.
     */
    public void initialize(String configFile) {
        params = new Hashtable<String, String>();
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
                for (String param : currParams) {
                    String[] parts = param.split(":");
                    params.put(parts[0], parts[1]);
                }
            }
            in.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }



    /**
     * @return A string which encodes the parameters for a schedule-
     * based valuation function which maps a collection of goods to
     * a score value.  The strings have the following format:
     * "Number_Of_Goods:3 Need:2 Deadline:3 Valuations:10,5,3"
     *
     * The number of goods is number of types of goods that exist. Goods
     * must be integers from 0 to N, where "int n" represents a good at the
     * n'th time-slot.  The deadline designates the last time-slot that
     * can be used to fulfill the schedule.  The valuations are in
     * decreasing order and represent the valuation awarded for obtaining
     * the required number of goods by that time-slot.
     */
    public String generateScoringFunction() {
        String schedule;
        Random rand = new Random();

        //Generate a number of goods needed using param boundaries
        int lo = Integer.parseInt(params.get("Need_Low_Bound"));
        int hi = Integer.parseInt(params.get("Need_High_Bound"));
        int numNeed = rand.nextInt(hi-lo+1) + lo;

        //Generate a random deadline using param boundaries
        lo = Integer.parseInt(params.get("Deadline_Low_Bound"));
        hi = Integer.parseInt(params.get("Deadline_High_Bound"));
        //deadline should never be less than number of goods needed (else impossible)
        int deadline = Math.max(numNeed, rand.nextInt(hi-lo+1)+lo);

        //Generate valuations from distribution with param boundaries
        double loD = Double.parseDouble(params.get("Value_High_Bound"));
        double hiD = Double.parseDouble(params.get("Value_Low_Bound"));
        double[] vals = new double[Integer.parseInt(params.get("Number_Of_Goods"))];
        for (int i=0; i < vals.length; i++) {
            vals[i] = (rand.nextDouble()*(hiD-loD)) + loD;
        }
        //sort valuations so that highest is first (0th is highest)
        Arrays.sort(vals);//smallest payoffs first... reverse array order
        for (int i=0; i < vals.length/2; i++) {
            double tmp = vals[i];
            vals[i] = vals[ vals.length-1-i ];
            vals[ vals.length-1-i ] = tmp;
        }
        //any valuations greater than the deadline are set to 0
        for (int i=deadline; i<vals.length; i++) {
            vals[i] = 0;
        }

        //Construct the string encoding the valuation function
        schedule = new String("Number_Of_Goods:" + params.get("Number_Of_Goods") +
                " Need:" + numNeed +
                " Deadline: " + deadline +
                " Valuations:");
        String valuations = "";
        for(int j=0; j < vals.length; j++)
            valuations = valuations + "," + vals[j];

        return schedule + valuations.substring(1);//remove leading ","
    }


    /**
     * Given a list of goods and a string encoding a schedule valuation
     * function, return the score found by applying the function to the goods.
     * @param scoreFunct A string encoding the parameters of the schedule-based
     * valuation function, including the number of deadline and valuations for
     * obtaining a required number of goods at each possible point in time.
     * @param goods A list of goods for which to find a valuation score.
     * @return double Score calculated by applying valuation function to goods.
     */
    public double getScore(String scoreFunct, List<String> goods) {
        if (goods == null || scoreFunct == null) {
            return 0;
        }
        String[] scoreFunctParts = scoreFunct.split("\\s+");
        Hashtable<String, String> functParams = new Hashtable<String, String>();
        for (int i=0; i < scoreFunctParts.length; i++) {
            String[] parts = scoreFunctParts[i].split(":");
            functParams.put(parts[0], parts[1]);
        }

        int numNeed = Integer.parseInt(functParams.get("Need"));
        if (numNeed > goods.size() ) {
            return 0;//agent didn't have enough goods, so no reward
        }

        //build an array of payoffs based on the values after "Valuations"
        String[] valueStrings = functParams.get("Valuations").split(",");
        double[] payoffs = new double[valueStrings.length];
        for (int i=0; i < valueStrings.length; i++) {
            payoffs[i] = Double.parseDouble(valueStrings[i]);
        }

        //"Goods" are string representations of integers. Build int[] of their int values
        int[] goodsOwned = new int[goods.size()];
        for (int i=0; i < goods.size(); i++) {
            goodsOwned[i] = Integer.parseInt(goods.get(i));
        }
        Arrays.sort(goodsOwned);//smallest (earliest) goods first.

        //get payoff at earliest opportunity
        if (numNeed < 1) {
            return 0;
        } else if (goodsOwned[numNeed-1]  < payoffs.length) {
            //numNeed-1 is index at which min req is met
            return payoffs[goodsOwned[numNeed-1]];
        }
        return 0;
    }

}
