/**
 * @author TJ Goff  goff.tom@gmail.com
 * @version 1.0.0
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License version 2.1 as published
 * by the Free Software Foundation.
 *
 * This class defines a Valuation function that uses contracts to assign a score
 * to a combination of goods.  The score is determined by selecting the
 * (hopefully) optimal combination of contracts that are to be satisfied using
 * the available goods.  WARNING: Solving this combinatorial optimization
 * problem for large sets of goods and contracts can quickly become
 * computationally intractable.
 */

package jack.valuations;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.regex.*;

public class ContractValuation implements Valuation {

    //Stores parameters from config file. "name_of_good" -> [params]
    Hashtable<String, Hashtable<String, String>> params;
    int contractsPerClient = 1;

    public ContractValuation(String configFile){
        this.initialize(configFile);
    }

    /**
     * Read config file to get parameters for generating contracts.
     * The config file should have 1 line per type of good, and all
     * parameters for that type of good should be defined on that line
     * (else default values used).  There are 5 values that can be
     * specified for a type of good: The name of the type of good, a
     * lower bound (integer) for how many of the good are needed for
     * to satisfy a contract, an upper bound (integer) for the number
     * of the good needed to satisfy a contract, a lower bound (integer)
     * for the value of each instance of the good, and an upper bound
     * (integer) for the value each instance contributes to the value
     * of a contract containing the good.
     * @param configFile File containing parameters used for the
     * Valuation function generating and scoring functions.
     */
    public void initialize(String configFile)
    {
        params = new Hashtable<String, Hashtable<String, String>>();
        try {
            DataInputStream in = new DataInputStream(new FileInputStream("src/valuations/"+configFile));
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;

            while ((strLine = br.readLine()) != null) {//read config file line-by-line
                if (strLine.isEmpty()){
                    continue;//skip blank lines
                }
                //Special case, if line designates number of contracts per client.
                else if (strLine.contains("Contracts_Per_Client:")) {
                    Pattern  pattern = Pattern.compile("Contracts_Per_Client:(d+)");
                    Matcher matcher = pattern.matcher(strLine);

                    String[] parts = strLine.split(":");

                    contractsPerClient = Integer.parseInt(parts[1]); //(matcher.group(0));
                    continue;
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
        } catch (FileNotFoundException e) {
            System.err.println("FileNotFoundException: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        }
    }


    /**
     * @return A string which encodes 1 or more contracts to be give to
     * 		a client. The number of contracts encoded within the string
     * 		is set by the parameter contractsPerClient.
     * Contracts are separated by spaces.  Within a contract, there is
     * one or more goodType:numberNeeded pair, separated by "," commas,
     * and at the end there is a double value indicating the value for
     * fullfilling the contract.
     * For example, the string below encodes 2 contracts; the first is
     * for 2 Apples and 3 Bananas (value of 4), and the second contract
     * is for 5 Oranges (value of 6):
     * "Apple:2,Banana:3,4 Orange:5,6"
     */
    public String generateScoringFunction()
    {
        String contracts = new String();
        Random rand = new Random();
        for (int i=0; i < contractsPerClient; i++) {
            String currContract = "";
            double val = 0;//as required goods are added, add to value of contract

            //for each good type, add requirement to contract
            Enumeration<String> keys = params.keys();
            while (keys.hasMoreElements()) {
                String currType = keys.nextElement();
                Hashtable<String, String> typeParams = params.get(currType);

                //Pick number of this good needed and the marginal value to contract
                int numNeeded = rand.nextInt(Integer.parseInt(typeParams.get("Need_High_Bound"))-
                        Integer.parseInt(typeParams.get("Need_Low_Bound"))+1)+
                    Integer.parseInt(typeParams.get("Need_Low_Bound"));
                double currVal = (rand.nextDouble()*
                        (Double.parseDouble(typeParams.get("Value_High_Bound"))-
                         Double.parseDouble(typeParams.get("Value_Low_Bound")))) +
                    Double.parseDouble(typeParams.get("Value_Low_Bound"));

                currContract = currContract + "," + currType + ":" + numNeeded;
                val += numNeeded * currVal;//contract value is aggregation of marginal values
            }
            //Append value for fulfilling currContract, and remove leading ",".
            //Add currContract to output string containing all contracts.
            contracts = contracts + " " + currContract.substring(1)  + "," + val;
        }
        return contracts.substring(1);//remove leading " "
    }

    /**
     * Given a list of goods and a string encoding one or more contracts,
     * return a score which is the combinatorial optimization of goods
     * used to satisfy contracts.
     * @param scoreFunct A string encoding one or more contract. The string
     * 	uses same convention as ContractValuation.generateScoringFunction()
     * 	do decode the contract(s).
     * @param goods A list of goods that will be used to satisfy contracts
     * 	for a valuation score.
     * @return double score achieved by solving the optimization problem of
     * 	assigning goods to satisfy contracts to achieve the highest score.
     */
    @SuppressWarnings("unchecked")
    public double getScore(String scoreFunct, List<String> goods) {
        if (goods == null || scoreFunct == null) {
            return 0;
        }
        double maxValue = Double.NEGATIVE_INFINITY;
        int i, j, k, l;//declare variables used in for-loops only once

        //Decode scoreFunct into distinct contracts
        //Each contract is separated by spaces
        String[] contracts = scoreFunct.split(" ");

        //create indexable lists of good type and how many of that good are in "goods"
        Vector<String> goodType = new Vector<String>();
        Vector<Integer> goodCount = new Vector<Integer>();
        for (i=0; i<goods.size(); i++) {
            if ((j=goodType.indexOf(goods.get(i))) > -1 ) {//good type already seen
                goodCount.set(j, new Integer(goodCount.get(j)+1));
            }
            else { //add the new good type to the list and start a count
                goodType.add(goods.get(i));
                goodCount.add(new Integer(1));
            }
        }

        //Array of booleans used to enumerate every combination of contracts
        boolean[] activeContracts = new boolean[contracts.length];
        for (i=0; i<activeContracts.length; i++) {
            activeContracts[i] = false;
        }

        //find value obtained from trying each combination of contracts
        for (i=0; i < Math.pow(2,contracts.length); i++) {
            //make a deep copy of Vector of counts for each type of good owned by agent
            //the count at goodsOwned.get(i) corresponds to type goodType.get(i)
            Vector<Integer> goodsOwned = (Vector<Integer>)goodCount.clone();//deep copy of counts

            //find total number of each type needed to satisfy all "active" contracts
            Vector<String> neededType = new Vector<String>();
            Vector<Integer> neededNum = new Vector<Integer>();
            double contractsValue = 0;//sum of values for all "active" contracts

            for (j=0; j < activeContracts.length; j++) {
                if (activeContracts[j]) {//try to satisfy this contract
                    String[] parts = contracts[j].split("[,:]");//type0 need0 type1 need1... value
                    contractsValue = contractsValue + Double.parseDouble(parts[parts.length-1]);
                    //add to counts of goods needed
                    for (k=0; k+1 < parts.length; k+=2) {
                        if ((l=neededType.indexOf(parts[k])) > -1) {//repeat type
                            neededNum.set(l, new Integer(neededNum.get(l)+ Integer.parseInt(parts[k+1])));
                        }
                        else {
                            neededType.add(parts[k]);//new type of good needed
                            neededNum.add( Integer.parseInt(parts[k+1]) );
                        }
                    }
                }
            }
            //if goodsOwned is superset of goods needed, you can satisfy the contracts and get their value
            boolean fulfill = true;
            for (j=0; j < neededType.size(); j++) {
                if (neededNum.get(j) == 0){
                    continue;//don't worry about types where agent needs 0
                }
                else if((k=goodType.indexOf(neededType.get(j))) > -1) {
                    if (goodsOwned.get(k) >= neededNum.get(j)) {//if agent has enough of the good...
                        goodsOwned.set(k, new Integer(goodsOwned.get(k)-neededNum.get(j)));
                    }
                    else {
                        fulfill = false; //agent doesn't have enough of this type of good
                        break;
                    }
                }
                else {
                    fulfill = false;//needed type of good agent doesn't have
                    break;
                }
            }
            if (fulfill) {
                maxValue = Math.max(maxValue, contractsValue);
            }

            //increment to next combination of contracts
            for (j=0; j<activeContracts.length; j++) {//search to first false...
                if (activeContracts[j] == false) {//set true, and flip all lower bits
                    for (; j >=0; j--){
                        activeContracts[j] = !activeContracts[j];
                    }
                    break;//stop after flipping bits
                }
            }
        }//end "for each combination of contracts"...
        return maxValue;//best value
    }
}
