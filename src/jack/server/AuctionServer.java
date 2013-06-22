/**
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License version 2.1 as published
 * by the Free Software Foundation.
 */

package jack.server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import jack.scheduler.Scheduler;
import jack.scheduler.SchedulerFactory;

import jack.auctions.*;
import jack.valuations.*;

public class AuctionServer
{
    private Scheduler scheduler = null;

    private Map<Integer, Auction> auctions = new HashMap<Integer, Auction>();

    private final Logger LOGGER = Logger.getLogger(AuctionServer.class.getName());

    int port = 1300;

    int maxWaitTime = 10000;

    public AuctionServer(String filename) {
        loadConfig(filename);
    }

    public void run() {

        // Wait for client connections. If we do not get at least one
        // connection, then we cannot continue.

        Vector<ClientHandler> clients = waitForClients();
        if (clients.isEmpty()) {
            LOGGER.info("Failed to receive any connections");
            return;
        }

        // Notify each auction of the clients that will be participating. It is
        // the auctions responsibility to register themselves with each client
        // before they begin and unregister themselves after they have ended.

        for (Auction auction : auctions.values()) {
            auction.setSessionId(1);
            auction.setClients(clients);
        }

        // Not entirely sure how this should work yet, but for the moment, each
        // auction is going to send its "specification" to the bidders. This is
        // effectively notifying them of the scheduler before it is executed.

        for (Auction auction : auctions.values()) {
            auction.sendSpec();
        }

        // TODO: This should not be here--wait 5 secods before executing the
        // schedule. This is really just a nicety especially when dealing with
        // human interfaces. Maybe sending a countdown to the clients would be
        // better?

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Execute the schedule

        scheduler.execute(auctions);

        // TODO: Evaluate the results

        // Shutdown the sockets

        for (ClientHandler client : clients) {
            client.close();
        }
    }

    /**
     */
    private Vector<ClientHandler> waitForClients() {

        // Set up the server socket to listen for clients

        Vector<ClientHandler> clients = new Vector<ClientHandler>();
        ServerSocket serverSocket = null;

        try {
            InetSocketAddress endpoint = new InetSocketAddress(port);
            serverSocket = new ServerSocket();
            serverSocket.bind(endpoint);
            serverSocket.setSoTimeout(1000);
            LOGGER.info("Listening for connections at " + endpoint.toString());

        } catch (IOException e) {
            LOGGER.warning("Failed to bind to address: " + e.getMessage());
            return clients;
        }

        // Wait for connections until we either run out of time or we have
        // reached the max number of clients.

        long endTime = System.currentTimeMillis() + maxWaitTime;
        while (System.currentTimeMillis() < endTime) {

            // Update the terminal with the remaining time

            int remainingTime = (int)(endTime - System.currentTimeMillis());
            LOGGER.fine("" + (remainingTime / 1000) + " seconds remaining");

            try {

                // Wait for a client connection until the socket times out. Once
                // we get a connection, create a handler to deal with
                // communicating to and from the socket.

                Socket clientSocket = serverSocket.accept();
                ClientHandler client = new ClientHandler(clientSocket);
                client.start();
                clients.add(client);

                InetAddress clientAddress = clientSocket.getInetAddress();
                LOGGER.info("Received connection from " + clientAddress.toString());

            } catch (SocketTimeoutException e) {
                continue;

            } catch (IOException e) {
                LOGGER.warning("Error accepting connection: " + e.getMessage());
                break;
            }
        }

        return clients;
    }

    /**
     * This function initializes the auction server from a configuration file.
     */
    private void loadConfig(String filename) {
        try {

            // Load the xml configuration and get a DOM document object

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new File(filename));
            doc.getDocumentElement().normalize();

            // Load the schedule

            NodeList schedulerNodes = doc.getElementsByTagName("schedule");
            if (schedulerNodes.getLength() > 0) {
                scheduler = SchedulerFactory.newScheduler(schedulerNodes.item(0));
                //scheduler.dump();
            }

            // Load the auction(s)

            NodeList auctionNodes = doc.getElementsByTagName("auction");
            for (int i = 0; i < auctionNodes.getLength(); ++i) {
                Auction auction = AuctionFactory.newAuction(auctionNodes.item(i));
                auctions.put(auction.getAuctionId(), auction);
            }

            // TODO: Load the valuation(s)

        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Starts the auction server with the specified xml configuration file.
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage: AuctionServer xml_config_file");
            return;
        }
        AuctionServer server = new AuctionServer(args[0]);
        server.run();
    }
}
