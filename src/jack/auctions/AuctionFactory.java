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
//import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class AuctionFactory {

    public static Auction newAuction(Node node) {

        Element element = (Element)node;
        int id = Integer.parseInt(element.getAttribute("id"));
        String type = element.getAttribute("type");

        Auction auction = null;
        if (type.equals("FantasyFootballAuction")) {
            auction = new FantasyFootballAuction(id);
        //} else if (type.equals("SealedBidAuction")) {
        //} else if (type.equals("AscendingAuction")) {
        //} else if (type.equals("DescendingAuction")) {
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
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
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
