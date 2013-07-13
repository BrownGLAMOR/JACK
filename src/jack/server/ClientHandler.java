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

package jack.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Vector;
import java.util.logging.Logger;

import jack.auctions.Auction;

public class ClientHandler extends Thread {

    private final Socket socket;

    private final BufferedReader input;

    private final PrintWriter output;

    private final Vector<Auction> auctions = new Vector<Auction>();

    private final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    public ClientHandler(Socket sock) throws IOException {
        socket = sock;
        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new PrintWriter(socket.getOutputStream(), true);
    }

    /**
     * Ends communication with the client. This function closes the client
     * socket and effectively ends any further communication.
     */
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            LOGGER.warning("Failed to close socket: " + e.getMessage());
        }
    }

    /**
     * Resgisters an auction with this handler. Once registered an auction will
     * receive every message that arrives at this socket. Generally auctions
     * should register with a handler when they begin and unregister when they
     * end. This function is thread safe.
     * @param auction The auction to register with this client
     */
    public void register(Auction auction) {
        auctions.add(auction);
    }

    /**
     * Unregisters an auction with this handler. When an auction is unregistered
     * it will no longer messages that arrive at this socket. If the auction has
     * not been previously registered this function will silently fail. This
     * function is thread safe.
     * @param auction The auction to unregsiter with this client
     */
    public void unregister(Auction auction) {
        auctions.remove(auction);
    }

    /**
     * This function sends a message to the client.
     * @param message The message to send
     */
    public void sendMessage(String message) {
        output.println(message);
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = input.readLine()) != null) {
                for (Auction auction : auctions) {
                    auction.queueMessage(message);
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to read from socket: " + e.getMessage());
        }
    }
}
