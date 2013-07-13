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

package jack.scheduler;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SchedulerFactory {

    /**
     * Factory method to create an auction scheduler given a DOM node. At the
     * moment this method is not a typical factory method in that there are not
     * currently different types of auction schedules. Rather this function
     * constructs the schdule given the specification described in the node.
     * This function throws an InvalidArgumentException if the node is invalid.
     *
     * @param node DOM node containing the scheduler specification. The node
     *             must be an element and have the name "schedule".
     * @return The scheduler that matches the specification
     */
    public static Scheduler newScheduler(Node node) {

        // Verify that we have a valid schedule node
        // TODO: In the future e should also try to validate the XML against a
        // predefined schema

        if (node.getNodeType() != Node.ELEMENT_NODE
                || !node.getNodeName().equals("schedule")) {
            throw new IllegalArgumentException("invalid schedule node");
        }

        // For the moment there is only one type of scheduler though this could
        // change in the future.

        Scheduler scheduler = new Scheduler();

        // First add all of the tasks to the scheduler. A task represents the
        // execution of a single auction in the schedule.

        Element schedElem = (Element)node;
        NodeList taskNodes = schedElem.getElementsByTagName("task");
        for (int i = 0; i < taskNodes.getLength(); ++i) {
            scheduler.addAuction(getAuctionId(taskNodes.item(i)));
        }

        // Now add all of the explicit dependencies. Explicit dependencies are
        // those that are spiecified using the startDepend or endDepend tag name
        // in the configuration file.

        for (int i = 0; i < taskNodes.getLength(); ++i) {
            Element taskElem = (Element)taskNodes.item(i);
            int auctionId = getAuctionId(taskElem);

            NodeList startDepends =
                taskElem.getElementsByTagName("startDepend");
            for (int j = 0; j < startDepends.getLength(); ++j) {
                scheduler.addStartDepend(
                    auctionId, getAuctionId(startDepends.item(j)));
            }

            NodeList endDepends = taskElem.getElementsByTagName("endDepend");
            for (int j = 0; j < endDepends.getLength(); ++j) {
                scheduler.addEndDepend(
                    auctionId, getAuctionId(endDepends.item(j)));
            }
        }

        // Add implicit dependencies. These dependencies are not specified
        // directly, but are rather inferred from the presence of two addition
        // tags, "sequential" and "simultaneous", in the configuration.

        NodeList seqNodes = schedElem.getElementsByTagName("sequential");
        for (int i = 0; i < seqNodes.getLength(); ++i) {
            addSequentialDepends(scheduler, seqNodes.item(i));
        }

        // Simultaneous tasks must be handled slightly differently from their
        // sequential counterparts. We add the simultaneous dependencies in a
        // depth first post fix ordering to handle nested simultaneous tags.

        List<Element> simNodes = getSimultaneousPostOrder(schedElem);
        for (int i = 0; i < simNodes.size(); ++i) {
            addSimultaneousDepends(scheduler, simNodes.get(i));
        }

        return scheduler;
    }

    /**
     * This function adds sequential dependencies between autions as specified
     * in the given DOM node. Each task in the sequential node has a starting
     * depenency on the task specified before it. This function is not recursive
     * and does not depend on any of the other explicit or implicit
     * dependencies. If the given node is an invalid sequential element, then
     * this function will throw an IllegalArgumentException.
     *
     * @param scheduler The scheduler containing the tasks that this function
     *                  will add dependencies on to.
     * @param node The DOM node containing the references to the sequential
     *             auctions. Any auctions referenced in this node must have
     *             previsouly been added to the scheduler.
     */
    private static void addSequentialDepends(Scheduler scheduler, Node node) {

        // Verify that we have a valid sequential node

        if (node.getNodeType() != Node.ELEMENT_NODE
                || !node.getNodeName().equals("sequential")) {
            throw new IllegalArgumentException("invalid sequential node");
        }

        // Iterate over the child elements

        Integer prevAuctionId = null;
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); ++i) {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElem = (Element)childNode;

            // If the child is a task then add the start dependencies directly

            if (childElem.getTagName().equals("task")) {
                int auctionId = getAuctionId(childElem);
                if (prevAuctionId != null) {
                    scheduler.addStartDepend(auctionId, prevAuctionId);
                }
                prevAuctionId = auctionId;

            } else if (childElem.getTagName().equals("sequential")
                           || childElem.getTagName().equals("simultaneous")) {

                // If the child is not a task then it is either a sequential or
                // simultaneous collection of tasks. We first perform a depth
                // first search for tasks on the subtree starting at this child.
                // We then add a starting dependency on the first task from this
                // search on the previous child element. And subsequent children
                // will have a starting dependency on the last task from this
                // search.

                NodeList tasks = childElem.getElementsByTagName("task");
                if (tasks.getLength() > 0) {
                    if (prevAuctionId != null) {
                        scheduler.addStartDepend(
                            getAuctionId(tasks.item(0)), prevAuctionId);
                    }
                    prevAuctionId =
                        getAuctionId(tasks.item(tasks.getLength() - 1));
                }
            }
        }
    }

    /**
     * This function add simultaneous dependencies between auctions as specifed
     * in the given DOM node. Unlike sequential dependencies, a simultaneous
     * dependency enforces that all auctions in the group have exactly the same
     * starting and ending dependencies. In order to truely make these auctions
     * simultaneous, this dependency must be added after all other types of
     * dependencies, and after all of the dependencies of this nodes children.
     *
     * @param scheduler The scheduler containing the tasks that this function
     *                  will add dependencies on to.
     * @param node The DOM node containing the references to the simultaneous
     *             auctions. Any auctions referenced in this node must have
     *             previsouly been added to the scheduler.
     */
    private static void addSimultaneousDepends(Scheduler scheduler, Node node) {

        // Verify that we have a valid simultaneous node

        if (node.getNodeType() != Node.ELEMENT_NODE
                || !node.getNodeName().equals("simultaneous")) {
            throw new IllegalArgumentException("invalid simultaneous node");
        }

        // Get all of the tasks that need to start at the same time within this
        // simultaneous block. For those tasks, aggregate their starting
        // dependencies.

        List<Element> startTasks = getSimultaneousStartTasks((Element)node);
        Set<Integer> startDepends = new HashSet<Integer>();
        for (Element startTask : startTasks) {
            startDepends.addAll(scheduler.getStartDepends(
                getAuctionId(startTask)));
        }

        // Enforce the same starting dependencies on all of the synchronized
        // starting tasks.

        for (Element task : startTasks) {
            int auctionId1 = getAuctionId(task);
            for (Integer auctionId2 : startDepends) {
                if (auctionId1 != auctionId2) {
                    scheduler.addStartDepend(auctionId1, auctionId2);
                }
            }
        }

        // Get all of the tasks that need to end at the same time within this
        // simultaneous block and aggregate their starting dependencies.

        List<Element> endTasks = getSimultaneousEndTasks((Element)node);
        Set<Integer> endDepends = new HashSet<Integer>();
        for (Element endTask : endTasks) {
            endDepends.addAll(scheduler.getEndDepends(
                getAuctionId(endTask)));
        }

        // In addition to the above dependencies each of the end tasks is also
        // an end dependency.

        for (Element endTask : endTasks) {
            endDepends.add(getAuctionId(endTask));
        }

        // Finally, enforce the same ending dependencies on all of the
        // synchronized ending tasks.

        for (Element task : endTasks) {
            int auctionId1 = getAuctionId(task);
            for (Integer auctionId2 : endDepends) {
                if (auctionId1 != auctionId2) {
                    scheduler.addEndDepend(auctionId1, auctionId2);
                }
            }
        }
    }

    /**
     * Returns the auctionId attribute of the specified task/dependency element.
     * This function throws an InvalidArgumentException if the specified node
     * is invalid.
     *
     * @param taskOrDepend The task or dependency node whose auctionId is returned.
     *                     This node must be an element and contain an
     *                     auctionId attribtue.
     * @return The auctionId of the task or dependency
     */
    private static int getAuctionId(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            throw new IllegalArgumentException("node must be an element type");
        }

        Element elem = (Element)node;
        if (!elem.hasAttribute("auctionId")) {
            throw new IllegalArgumentException("element missing attribute");
        }

        return Integer.parseInt(elem.getAttribute("auctionId"));
    }

    /**
     * This function returns a depth first search post-order traversal for DOM
     * elements named "simultaneous". This function is recursive.
     *
     * @param elem The element which the search starts from
     * @return The post-ordering of the "simultaneous" elements
     */
    private static List<Element> getSimultaneousPostOrder(Element elem) {
        List<Element> elems = new ArrayList<Element>();
        NodeList childNodes = elem.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); ++i) {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                elems.addAll(getSimultaneousPostOrder((Element)childNode));
            }
            if (childNode.getNodeName().equals("simultaneous")) {
                elems.add((Element)childNode);
            }
        }
        return elems;
    }

    /**
     * This function returns the list of tasks that need to be started
     * synchronously within a simultaneous block. This includes all tasks that
     * are direct children of this element, all tasks nested in simultaneous
     * blocks, and the first task within a nested sequential block.
     *
     * @param elem The simultanous element where the search starts
     * @return The list of tasks that need to start at the same time
     */
    private static List<Element> getSimultaneousStartTasks(Element elem) {
        List<Element> elems = new ArrayList<Element>();
        NodeList childNodes = elem.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); ++i) {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElem = (Element)childNode;
            if (childElem.getNodeName().equals("task")) {
                elems.add(childElem);
            } else if (childElem.getNodeName().equals("sequential")) {
                NodeList taskNodes = childElem.getElementsByTagName("task");
                if (taskNodes.getLength() > 0) {
                    elems.add((Element)taskNodes.item(0));
                }
            } else if (childElem.getNodeName().equals("simultaneous")) {
                elems.addAll(getSimultaneousStartTasks(childElem));
            }
        }
        return elems;
    }

    /**
     * This function returns the list of tasks that need to be ended
     * synchronously within a simultaneous block. This includes all tasks that
     * are direct children of this element, all tasks nested in simultaneous
     * blocks, and the last task within a nested sequential block.
     *
     * @param elem The simultanous element where the search starts
     * @return The list of tasks that need to end at the same time
     */
    private static List<Element> getSimultaneousEndTasks(Element elem) {
        List<Element> elems = new ArrayList<Element>();
        NodeList childNodes = elem.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); ++i) {
            Node childNode = childNodes.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElem = (Element)childNode;
            if (childElem.getNodeName().equals("task")) {
                elems.add(childElem);
            } else if (childElem.getNodeName().equals("sequential")) {
                NodeList taskNodes = childElem.getElementsByTagName("task");
                if (taskNodes.getLength() > 0) {
                    elems.add((Element)taskNodes.item(taskNodes.getLength() - 1));
                }
            } else if (childElem.getNodeName().equals("simultaneous")) {
                elems.addAll(getSimultaneousEndTasks(childElem));
            }
        }
        return elems;
    }

    /**
     * Returns the XML schema for a "schedule" element. This schema can be used
     * to verify the validity of the XML representation. It cannot be used to
     * validate the logic of that schedule.
     *
     * @return The XML schema for the schedule
     */
    private static Schema getSchema() {
        try {
            URL pathname = SchedulerFactory.class.getResource("schedule.xsd");
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return sf.newSchema(new File(pathname.toURI()));
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    /**
     * This function returns the validity of a DOM node given the schema.
     *
     * @param schema The XML schema used to validate this node
     * @param node The DOM node to validate
     * @return True if the node is valid and false if it is not
     */
    private static boolean validate(Schema schema, Node node) {
        try {
            Validator validator = schema.newValidator();
            validator.validate(new DOMSource(node));
            return true;
        } catch (IOException e) {
            return false;
        } catch (SAXException e) {
            return false;
        }
    }
};
