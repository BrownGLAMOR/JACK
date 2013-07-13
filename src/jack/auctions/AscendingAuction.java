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

package jack.auctions;

import java.util.Map;
import java.util.HashMap;
import java.util.Vector;

import jack.server.*;

public class AscendingAuction extends Auction
{
    /* Message types used by this auction */
    private static final String START_MSG = "start";
    private static final String STATUS_MSG = "status";
    private static final String STOP_MSG = "stop";
    private static final String BID_MSG = "bid";

    /* Argument keys used in messages */
    private static final String TIMER_KEY = "timer";
    private static final String BIDDER_KEY = "bidder";
    private static final String BID_KEY = "bid";

    /** The maximum amount of time given to bidders after a new bid (ms) */
    private final long MAX_TIMEOUT = 30000;

    /** The minimum amount of time given to bidders after an new bid (ms) */
    private final long MIN_TIMEOUT = 10000;

    /** The name of the highest bidder */
    private String highBidder = null;

    /** The value of the highest bid */
    private int highBid = 0;

    /** The time that this auction should end */
    private long endTime = 0;

    public AscendingAuction(int auctionId) {
        super(auctionId);
        putHandler(BID_MSG, new BidHandler());
    }

    @Override
    protected void initialize() {
        sendStart();
    }

    @Override
    protected void resolve() {
        sendStop();
    }

    @Override
    protected void idle() {
        if (System.currentTimeMillis() > endTime) {
            tryEndable();
        }
    }

    private void sendStart() {
        endTime = System.currentTimeMillis() + MAX_TIMEOUT;
        long seconds = MAX_TIMEOUT / 1000;
        Map<String, String> args = new HashMap<String, String>();
        args.put(TIMER_KEY, Long.toString(seconds));
        sendMessage(START_MSG, args);
    }

    private void sendStatus() {
        long seconds = (endTime - System.currentTimeMillis()) / 1000;
        Map<String, String> args = new HashMap<String, String>();
        args.put(TIMER_KEY, Long.toString(seconds));
        if (highBidder != null) {
            args.put(BIDDER_KEY, highBidder);
            args.put(BID_KEY, Integer.toString(highBid));
        }
        sendMessage(STATUS_MSG, args);
    }

    private void sendStop() {
        Map<String, String> args = new HashMap<String, String>();
        if (highBidder != null) {
            args.put(BIDDER_KEY, highBidder);
            args.put(BID_KEY, Integer.toString(highBid));
        }
        sendMessage(STOP_MSG, args);
    }

    private class BidHandler implements MessageHandler {
        public void handle(Map<String, String> args)
                throws IllegalArgumentException {

            // Verify this message contains the correct keys

            if (!args.containsKey(BIDDER_KEY)) {
                throw new IllegalArgumentException("Invalid message: no "
                                                       + BIDDER_KEY);
            }

            if (!args.containsKey(BID_KEY)) {
                throw new IllegalArgumentException("Invalid message: no "
                                                       + BID_KEY);
            }

            // Check for a high bid

            int bid = Integer.parseInt(args.get(BID_KEY));
            if (highBid < bid) {
                highBidder = args.get(BIDDER_KEY);
                highBid = bid;

                // Increase the length of the auction if necessary

                long currTime = System.currentTimeMillis();
                if (endTime - currTime < MIN_TIMEOUT) {
                    endTime = currTime + MIN_TIMEOUT;
                }

                // Update the clients

                sendStatus();
            }
        }
    }
}
