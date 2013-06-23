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

package jack.auctions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import jack.server.ClientHandler;
import jack.scheduler.Task;

/**
 * The auction class is the base class for all auction implementations. It it
 * responsible for running the auction and handles the serialization and
 * deserialization of all messages to and from the clients. Subclasses should
 * define MessageHandlers for each message type that they wish to respond to.
 * When that message type is recieved by this class it will forward the message
 * to the appropriate handler. In addition, auction implementations can override
 * their choice of functions below to gain even more control over their
 * interactions with the clients.
 */
public abstract class Auction extends Task
{
    /* Message keys required by this auction*/
    protected final String SESSION_KEY = "sessionId";
    protected final String AUCTION_KEY = "auctionId";

    /** The parameters used to initialize this auction */
    protected Map<String, String> params = new HashMap<String, String>();

    /** A collection of sockets for client communication */
    protected Vector<ClientHandler> clients;

    /** The minium time to wait before idle function calls in ms */
    private static final long IDLE_TIMEOUT = 50;

    /** A queue of messages to handle */
    private final BlockingQueue<String> messages;

    /** A map of message handlers */
    private final Map<String, MessageHandler> handlers;

    /** Logger for writing log messages */
    protected final Logger LOGGER = Logger.getLogger(Auction.class.getName());

    /**
     * Constructs an auction with the specified identification.
     * @param auctionId The unique identified of this auction
     */
    public Auction(int auctionId) {
        super(auctionId);
        messages = new LinkedBlockingQueue<String>();
        handlers = new HashMap<String, MessageHandler>();
    }

    /**
     * Returns this auctions unique identification. This is the same as the task
     * identifier.
     * @return The auction identifier
     */
    public final int getAuctionId() {
        return getTaskId();
    }

    /**
     * Sets the client handlers to use for all auction communication. When the
     * auction is started, it will register itself with these clients, and when
     * the auction is ended, it will unregister itself with these clients.
     * @param newClients The new set of client handlers
     */
    public final void setClients(Vector<ClientHandler> newClients) {
        clients = newClients;
    }

    /**
     * Adds the message to the end of the message queue. This function should be
     * called whenever a new message arrives for this auction. The message
     * queue is drained and each message is passed to its corresponding handler
     * when the auction is in STATE_RUNNING or STATE_ENDABLE. This function is
     * thread safe, but it is not synchronized on the same lock as the auctions
     * state.
     * @param message Message to be handled by this auction
     */
    public void queueMessage(String message) {
        try {
            messages.put(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * This function sets the specified auction parameters. Subclasses should
     * override this function to receive configuration parameters. These
     * parameters are read from the configuration xml file and passed to each
     * auction by the AuctionFactory.
     * @param newParams A map of configuration parameters.
     */
    public void setParams(Map<String, String> newParams) {
        params = new HashMap<String, String>(newParams);
    }

    /**
     * This function sends the auction specification to each of the clients. The
     * auction specification consists of the "auction" tag followed by all of
     * the parameters used to initialize the auction. Subclasses should override
     * this function to only send the information that they want.
     */
    public void sendSpec() {
        sendMessage("auction", params);
    }

    /**
     * This function runs the auction. All auctions at their most basic level
     * can be represented as a series of messages passed between the auctions
     * and thir bidders. This function tries to take care of most of the leg
     * work of receiving messages. It drains the message queue and passes each
     * message to its registered handler. When their are no messages to drain it
     * calls the idle function in which a subclass can do whatever they would
     * like.
     */
    @Override
    public void run() {

        // If this auction has already been run then it cannot be run again.
        // This restriction could be relaxed in the future, but for the moment
        // it seems like the safest behavior.

        if (getState() != STATE_NEW) {
            return;
        }

        // Initialize the auction. This involes setting the state to be
        // STATE_RUNNING, registering the auction with the ComThreads so that
        // they can receive messages from the bidders, and calling the auction
        // specific initialization routine.

        setState(STATE_RUNNING);
        register();
        initialize();

        try {

            // Begin processing messages. As long as the curretn state of the
            // auction is not state ending we will continue to process messages.
            // This includes STATE_ENDABLE, where an auction can still receive
            // messages, but may be ended at any time.

            while (getState() < STATE_ENDING) {

                // Get the message off the top of the queue. If there is no
                // message then idle and try again.

                String message = messages.poll(IDLE_TIMEOUT,
                                               TimeUnit.MILLISECONDS);
                if (message == null) {
                    idle();
                    continue;
                }

                // Split the message by whitespace. Here we expect an auction
                // message starts with the type and is followed by an
                // unspecified number of key=value pairs:
                // "messageType key1=value1 ... keyN=valueN"
                // Messages that do not fit this format will either not be
                // processed or processed incorrectly.

                String[] keyVals = message.split("\\s+");
                if (keyVals.length > 0) {

                    // Parse the message type and the arguments

                    String messageType = keyVals[0];
                    Map<String, String> args =
                        toMap(Arrays.copyOfRange(keyVals, 1, keyVals.length));

                    // Check if this message was intended for this auction

                    if (!args.containsKey(SESSION_KEY)) {
                        LOGGER.warning("Invalid message: no " + SESSION_KEY);
                        continue;
                    }

                    if (!args.containsKey(AUCTION_KEY)) {
                        LOGGER.warning("Invalid message: no " + AUCTION_KEY);
                        continue;
                    }

                    // Silently ignore messages meant for other auctions

                    int sessionId = Integer.parseInt(args.get(SESSION_KEY));
                    int auctionId = Integer.parseInt(args.get(AUCTION_KEY));
                    if (sessionId != getSessionId() || auctionId != getAuctionId()) {
                        continue;
                    }

                    // Pass the message to the appropriate handler and
                    // ignore any unknown messages.

                    MessageHandler handler = handlers.get(messageType);
                    if (handler != null) {
                        try {
                            handler.handle(args);
                        } catch (IllegalArgumentException e) {
                            LOGGER.warning(e.toString());
                        }
                    } else {
                        LOGGER.warning("Unknown message type: " + messageType);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Finally resolve the message, unregister this auction from the
        // ClientHandlers and set the state to be ended.

        resolve();
        unregister();

        // TODO: This is currently an unfortunate hack which is necessary
        // because of the multithreaded nature of the scheduler. As soon as
        // we set the state to ended then the scheduler will try to launch
        // additional auctions in their own threads. If any auction
        // implementations decided to send a message to their clients in the
        // resolve function (which most will) then there is a chance that
        // clients will not receive this message until the after the next
        // auction starts. This is clearly undesirable behavior for clients
        // which want to know the results of the previous auction before moving
        // onto the next.

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        setState(STATE_ENDED);
    }

    /**
     * Called Immediately before the auction begins. Subclasses should override
     * this method to setup their own initialization routines and send any
     * pertinant information to the bidders.
     */
    protected void initialize() {}

    /**
     * Called whenever there are no messages to process. Subclasses should
     * override this method if they want to perform actions that are not
     * triggered by client messages, such as timed events.
     */
    protected void idle() throws InterruptedException {}

    /**
     * Called immediately after the auction ends. Subclasses should override
     * this method to setup their own resolution routines. This generally
     * includes informing the bidders of the winner.
     */
    protected void resolve() {}

    /**
     * Adds the specified handler to the handler map. If a handler has already
     * been specified for the given message type then this call will replace
     * that handler.
     * @param type The message type that this handler should be called on.
     * @param handler The handler responsible for handling this message type.
     */
    protected final void putHandler(String type, MessageHandler handler) {
        handlers.put(type, handler);
    }

    /**
     * Sends a message of the specifed type with the specifeid parameters to
     * each of the clients. In addition to the arguments passed into this
     * function, the sessionId and auctionId will be automatically appended.
     * @param type The message type that is being sent
     * @param args A key value map of arguments to pass with that message
     */
    protected final void sendMessage(String type, Map<String, String> args) {
        args.put(SESSION_KEY, Integer.toString(getSessionId()));
        args.put(AUCTION_KEY, Integer.toString(getAuctionId()));

        String message = type + toString(args);
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    /**
     * This function registers this auction with all of its clients. Once
     * registered this auction will receive message from these clients, which
     * will be palced in the message queue and passed to their corresponding
     * handlers.
     */
    private final void register() {
        for (ClientHandler client : clients) {
            client.register(this);
        }
    }

    /**
     * This function unregisters this auction with all of its clients. After
     * unregistering this auction no no longer be able to receive messages from
     * its clients.
     */
    private final void unregister() {
        for (ClientHandler client : clients) {
            client.unregister(this);
        }
    }

    /**
     * Constructs a map from an array of key value strings. Each string in the
     * array should be of the form "key=value". Extra whitespace on either end
     * of the key or value will be trimmed. Any non conformant strings will be
     * silently ignored.
     * @param keyVals An array of strings of the form "key=value"
     * @return A map of values indexed by their corresponding keys
     */
    private static final Map<String, String> toMap(String[] keyVals) {
        Map<String, String> m = new HashMap<String, String>();
        for (String keyVal : keyVals) {
            String[] pair = keyVal.split("=");
            if (pair.length == 2) {
                m.put(pair[0].trim(), pair[1].trim());
            }
        }
        return m;
    }

    /**
     * Constructs a key value string from a map of strings. Each key value pair
     * in the result will be of the form "key=value" seperated from ech other by
     * whitespace.
     * @param map A map of key value pairs
     * @return A string of key value pairs
     */
    public static final String toString(Map<String, String> keyVals) {
        String s = new String();
        for (Map.Entry<String, String> entry : keyVals.entrySet()) {
            s += " " + entry.getKey() + "=" + entry.getValue().replace(' ', '_');
        }
        return s;
    }

    /**
     * The MessageHandler interface provides an abstraction between the
     * deserialization of messages coming over the wire and the auctions that
     * wish to handle them. Subclasses of Auction should create handlers for
     * each message type they wish to handle, and then register that handler
     * with this class.
     */
    protected interface MessageHandler {
        public void handle(Map<String, String> args)
            throws IllegalArgumentException;
    }
}
