package org.cobbzilla.util.xml;

import org.w3c.dom.*;
import org.w3c.tidy.Tidy;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TidyUtil {

    public static void parse(InputStream in, OutputStream out, boolean removeScripts) {
        Tidy tidy = createTidy();
        if (!removeScripts) {
            tidy.parse(in, out);
        } else {
            Document doc = tidy.parseDOM(in, null);
            removeElement(doc.getDocumentElement(), "script");
            removeElement(doc.getDocumentElement(), "style");
            removeDuplicateAttributes(doc.getDocumentElement());
            tidy.pprint(doc, out);
        }
    }

    public static void removeDuplicateAttributes(Node parent) {
        if (parent.getNodeType() == Node.ELEMENT_NODE) {
            Element elt = (Element) parent;
            if (parent.getAttributes().getLength() > 0) {
                NamedNodeMap map = elt.getAttributes();
                Set<String> found = new HashSet<String>();
                Set<Attr> toRemove = null;
                for (int i=0; i<map.getLength(); i++) {
                    Attr attr = (Attr) map.item(i);
                    if (found.contains(attr.getNodeName())) {
                        if (toRemove == null) toRemove = new HashSet<Attr>();
                        toRemove.add(attr);
                    } else {
                        found.add(attr.getNodeName());
                    }
                }
                if (toRemove != null) {
                    for (Attr attr : toRemove) {
                        elt.removeAttributeNode(attr);
                    }
                }
            }
            NodeList childNodes = elt.getChildNodes();
            for (int i=0; i<childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                removeDuplicateAttributes(child);
            }
        }
    }

    public static void removeElement(Node parent, String elementName) {
        List<Node> toRemove = null;
        NodeList childNodes = parent.getChildNodes();
        for (int i=0; i<childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    child.getNodeName().equalsIgnoreCase(elementName)) {
                if (toRemove == null) toRemove = new ArrayList<Node>();
                toRemove.add(child);
            }
        }
        if (toRemove != null) {
            for (Node dead : toRemove) {
                parent.removeChild(dead);
            }
        }
        childNodes = parent.getChildNodes();
        for (int i=0; i<childNodes.getLength(); i++) {
            removeElement(childNodes.item(i), elementName);
        }
    }

    public static Tidy createTidy() {
        Tidy tidy = new Tidy();
        tidy.setQuiet(true);
        // tidy.setMakeClean(true);
        // tidy.setIndentContent(true);
        tidy.setSmartIndent(true);
        // tidy.setXmlOut(true);
        tidy.setXHTML(true);
        tidy.setWraplen(Integer.MAX_VALUE);

        // tidy.setDocType("omit");
        // tidy.setNumEntities(true);
        return tidy;
    }
}
