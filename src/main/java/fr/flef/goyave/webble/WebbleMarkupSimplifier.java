package fr.flef.goyave.webble;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Simplify WordProcessingML xml markup. */
public class WebbleMarkupSimplifier
{
    /** w: namespace. */
    private final static Namespace NS_W = Namespace.getNamespace("w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main");
    /** xml: namespace. */
    private static final Namespace NS_XML = Namespace.getNamespace("xml", "http://www.w3.org/XML/1998/namespace");

    
    private WebbleMarkupSimplifier()
    {
        
    }
    
    public static String simplifyContent(String xmlContent)
    {
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            org.w3c.dom.Document w3cDoc = builder.parse(new InputSource(new StringReader(xmlContent)));
            
            Document doc = new DOMBuilder().build(w3cDoc);
            
            removeProof(doc);
            removeRsidInfo(doc);
            removeBookmarks(doc);
            mergeAdjacentRuns(doc);
            
            
            XMLOutputter xmlOutput = new XMLOutputter();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            xmlOutput.output(doc, baos);
            return baos.toString(StandardCharsets.UTF_8.toString());
        }
        catch (SAXException | IOException | ParserConfigurationException e)
        {
            return xmlContent;
        }
        
    }


    // Removes w:proofErr from document (SpellChecks)
    private static void removeProof(Document doc)
    {
        List<Element> toRemove = new ArrayList<>();
        doc.getDescendants(new ElementFilter("proofErr")).forEach(toRemove::add);
        toRemove.forEach(Element::detach);
    }

    
    // Removes w:rsid* from document (used by word to merge document between users)
    private static void removeRsidInfo(Document doc)
    {
        List<Attribute> toRemove = new ArrayList<>();
        doc.getDescendants().forEach(c -> {
            if (c instanceof Element)
            {
                Element e = (Element) c;
                e.getAttributes().stream().filter(a -> a.getName().startsWith("rsid")).forEach(toRemove::add);
            }
        });
        toRemove.forEach(Attribute::detach);
    }
    
    private static void removeBookmarks(Document doc)
    {
        List<Element> nodesToRemove = new ArrayList<>();
        doc.getDescendants(new ElementFilter("bookmarkStart")).forEach(nodesToRemove::add);
        doc.getDescendants(new ElementFilter("bookmarkEnd")).forEach(nodesToRemove::add);
        
        nodesToRemove.forEach(Element::detach);
    }
    
    private static void mergeAdjacentRuns(Document contentAsXml)
    {
        List<Element> ps = new ArrayList<>();
        contentAsXml.getRootElement().getDescendants(new ElementFilter("p")).forEach(ps::add);
        
        List<Element> toRemove = new ArrayList<>();
        Map<Element, List<Element>> toAdd = new HashMap<>();
        
        for (Element p : ps)
        {
            List<Element> pChildrend = p.getChildren();
            for (int i = 0, j = 1; i < pChildrend.size() - 2 && j < pChildrend.size(); i++, j++)
            {
                Element n = pChildrend.get(i);
                Element m = pChildrend.get(j);
                if (n.getName().equals("r") && m.getName().equals("r") 
                        && n.getChildren().stream()
                        .anyMatch(e -> e.getName().equals("t"))
                        && m.getChildren().stream()
                        .anyMatch(e -> e.getName().equals("t")) )
                {
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
                       i--;
                       
                       System.err.println("Merge : '" + nContent + "', '" + mContent + "'");
                   }
                   else
                   {
                       System.err.println("Keep : " + pChildrend.get(i).getName());
                   }
                }
                else
                {
                    System.err.println("Keep : " + pChildrend.get(i).getName());
                }
                
            }
        }
        
        toRemove.forEach(Element::detach);
    }
    
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
            return false;
        } 
    }

}
