package io.github.flef.webble;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.DOMBuilder;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Simplify WordProcessingML XML markup file. */
class WebbleMarkupSimplifier
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WebbleMarkupSimplifier.class);
    
    /** w: namespace. */
    private final static Namespace NS_W = Namespace.getNamespace("w",
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main");
    /** xml: namespace. */
    private static final Namespace NS_XML = Namespace.getNamespace("xml", "http://www.w3.org/XML/1998/namespace");

    /** Util class. */
    private WebbleMarkupSimplifier()
    {

    }

    /**
     * Simplifies WordProcessingML XML markup file by removing Proof, RsidInfo, Bookmarks and adjacentRuns.
     * @param doc the {@link Document} to simplify.
     */
    static void simplifyContent(Document doc)
    {
        removeProof(doc);
        removeRsidInfo(doc);
        removeBookmarks(doc);
        mergeAdjacentRuns(doc);
    }

    /**
     * Creates a {@link Document} object from an XML content represented as a {@link String}.
     * @param xmlContent the document to parse.
     * @return a {@link Document} object from the parse xmlContent.
     * @throws IOException if content cannot be parsed to a valid {@link Document}.
     */
    static Document stringToDocument(String xmlContent) throws IOException
    {
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            org.w3c.dom.Document w3cDoc = builder.parse(new InputSource(new StringReader(xmlContent)));

            return new DOMBuilder().build(w3cDoc);
        }
        catch (SAXException | IOException | ParserConfigurationException e)
        {
            throw new IOException("Cannot parse given XML content to a valid DOM Document.", e);
        }

    }

    /**
     * Serializes a {@link Document} object to its {@link String} representation.
     * @param doc the {@link Document} to serialize.
     * @return the serialized XML representation of the given doc.
     * @throws IllegalStateException if document cannot be serialize to a {@link String}.
     */
    static String documentToString(Document doc)
    {
        try
        {
            XMLOutputter xmlOutput = new XMLOutputter();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            xmlOutput.output(doc, baos);
            return baos.toString(StandardCharsets.UTF_8.toString());
        }
        catch (IOException e)
        {
            LOGGER.error("Cannot write xml content to memory. Is memory full ?", e);
            throw new IllegalStateException(e);
        }
    }

    /** Removes w:proofErr from document (SpellChecks). */
    private static void removeProof(Document doc)
    {
        List<Element> toRemove = new ArrayList<>();
        doc.getDescendants(new ElementFilter("proofErr")).forEach(toRemove::add);
        toRemove.forEach(Element::detach);
    }

    /** Removes w:rsid* from document (used by word to merge document between users). */
    private static void removeRsidInfo(Document doc)
    {
        List<Attribute> toRemove = new ArrayList<>();
        doc.getDescendants().forEach(c ->
        {
            if (c instanceof Element)
            {
                Element e = (Element) c;
                e.getAttributes().stream().filter(a -> a.getName().startsWith("rsid")).forEach(toRemove::add);
            }
        });
        toRemove.forEach(Attribute::detach);
    }

    /** Removes bookmark* from document. */
    private static void removeBookmarks(Document doc)
    {
        List<Element> nodesToRemove = new ArrayList<>();
        doc.getDescendants(new ElementFilter("bookmarkStart")).forEach(nodesToRemove::add);
        doc.getDescendants(new ElementFilter("bookmarkEnd")).forEach(nodesToRemove::add);

        nodesToRemove.forEach(Element::detach);
    }

    /** Merges two consecutive runs if properties are equals. */
    private static void mergeAdjacentRuns(Document contentAsXml)
    {
        List<Element> ps = new ArrayList<>();
        contentAsXml.getRootElement().getDescendants(new ElementFilter("p")).forEach(ps::add);

        List<Element> toRemove = new ArrayList<>();

        for (Element p : ps)
        {
            List<Element> pChildrend = p.getChildren();
            for (int i = 0, j = 1; i < pChildrend.size() - 1 && j < pChildrend.size(); i++, j++)
            {
                Element n = pChildrend.get(i);
                Element m = pChildrend.get(j);

                
                // IF NODES ARE TEXTUALS (CONTAIN TEXT)
                if (n.getName().equals("r") && m.getName().equals("r")
                        && n.getChildren("t", NS_W).size() > 0
                        && m.getChildren("t", NS_W).size() > 0)
                {
                    // PREVIOUS NODE HAS BEEN REMOVED.
                    // SKIP IT and ROLLBACK SECOND POINTER FOR NEXT LOOP.
                    if (n.getAttribute("REMOVED") != null)
                    {
                        j--;
                        continue;
                    }
                    
                    // PROPERTIES ARE EQUALS, LET'S MERGE CONTENT
                    if (areEquals(n.getChild("rPr", NS_W), m.getChild("rPr", NS_W)))
                    {

                        String nContent = n.getChildText("t", NS_W);
                        String mContent = m.getChildText("t", NS_W);
                        n.getChild("t", NS_W).setText(nContent + mContent);
                        if (nContent.startsWith(" ") || mContent.endsWith(" "))
                        {
                            n.setAttribute("space", "preserve", NS_XML);
                        }
                        toRemove.add(m);
                        m.setAttribute("REMOVED", "REMOVED");
                        i--;
                    }
                    // ELSE KEEP BOTH NODES
                }
             // ELSE KEEP BOTH NODES
            }
        }

        toRemove.forEach(Element::detach);
    }

    /** Returns true if given element are strictly equal (element, content, attributes). 
     * @throws IllegalStateException if equality cannot be tested (Node are serialized to String).
     */
    private static boolean areEquals(Element n, Element m)
    {
        if (n == null && m != null
                || n != null && m == null)
        {
            return false;
        }
        if (n == m)
        {
            return true;
        }

        XMLOutputter xmlOutput = new XMLOutputter();
        ByteArrayOutputStream nXml = new ByteArrayOutputStream();
        ByteArrayOutputStream mXml = new ByteArrayOutputStream();
        try
        {
            xmlOutput.output(n, nXml);
            xmlOutput.output(m, mXml);

            String nXmlString = nXml.toString();
            String mXmString = mXml.toString();

            return nXmlString.equals(mXmString);
        }
        catch (IOException e)
        {
            LOGGER.error("Cannot write xml content to memory. Is memory full ?", e);
            throw new IllegalStateException(e);
        }
    }
}
