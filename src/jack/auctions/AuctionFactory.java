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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * The auction factory class creates auctions from their corresponding DOM Node.
 * Each node is expected to contain at least two attributes "id" and "type"
 * which are used in the construction of every auction. In addition, these nodes
 * can define any number of elements which will be pased to the auction as a map
 * of key values pairs for configuration.
 */
public class AuctionFactory {

    public static Auction newAuction(Node node) {

        // TODO: Validate the node against the auction schema

        Element element = (Element)node;
        int id = Integer.parseInt(element.getAttribute("id"));
        String type = element.getAttribute("type");

        Auction auction = null;
        if (type.equals("AscendingAuction")) {
            auction = new AscendingAuction(id);
        //} else if (type.equals("DescendingAuction")) {
        //} else if (type.equals("SealedBidAuction")) {
        } else {
            System.out.println("Unknown auction: " + type);
            return null;
        }

        auction.setParams(getParams(node));
        return auction;
    }

    private static Schema getSchema() {
        try {
            URL pathname = AuctionFactory.class.getResource("auction.xsd");
            SchemaFactory sf =
                SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            return sf.newSchema(new File(pathname.toURI()));
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

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

    private static Map<String, String> getParams(Node node) {
        Map<String, String> params = new HashMap<String, String>();
        for (Node childNode = node.getFirstChild();
             childNode != null; childNode = childNode.getNextSibling()) {
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element)childNode;
                String key = childElement.getTagName();
                String value = childElement.getFirstChild().getNodeValue();
                params.put(key, value);
            }
        }
        return params;
    }
};
