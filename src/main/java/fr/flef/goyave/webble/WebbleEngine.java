package fr.flef.goyave.webble;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jdom2.Comment;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Parent;
import org.jdom2.filter.ElementFilter;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.loader.StringLoader;

public class WebbleEngine
{
    /** w: namespace. */
    private final static Namespace NS_W = Namespace.getNamespace("w",
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main");

    /**
     * Prepares the docx document to be used as a template.
     * For single use, see {@link #evaluate(Path, Map)}.
     */
    public static WebbleTemplate prepare(Path docx) throws IOException
    {
        Path unpackageDocx = Packager.unpackageDocx(docx);

        Path doc = unpackageDocx.resolve("word/document.xml");

        String xmlContent = prepareDocument(doc);

        System.err.println(xmlContent);
        
        Files.write(doc, Arrays.asList(new String[] { xmlContent }), StandardOpenOption.TRUNCATE_EXISTING);
        
        Path packageTemplate = Packager.packageDocx(unpackageDocx);
        return new WebbleTemplate(packageTemplate, docx.getFileName().toString().replaceFirst("(.*)\\.docx$", "$1"));
    }
    
    public static Path evaluate(Path docx, Map<String, Object> context) throws IOException
    {
        Path unpackageDocx = Packager.unpackageDocx(docx);
    
        PebbleEngine engine = new PebbleEngine.Builder().loader(new StringLoader()).build();
    
        Path doc = unpackageDocx.resolve("word/document.xml");
    
        String xmlContent = prepareDocument(doc);
    
        Writer writer = new StringWriter();
        engine.getTemplate(xmlContent).evaluate(writer, context);
        Files.write(doc, Arrays.asList(new String[] { writer.toString().replaceAll("\n", "<w:br/>") }),
                StandardOpenOption.TRUNCATE_EXISTING);
    
        return Packager.packageDocx(unpackageDocx);
    }
    
    public static Path evaluate(WebbleTemplate template, Map<String, Object> context) throws IOException
    {
        Path unpackageDocx = Packager.unpackageDocx(template.getTemplatePath());
    
        PebbleEngine engine = new PebbleEngine.Builder().loader(new StringLoader()).build();
    
        Path doc = unpackageDocx.resolve("word/document.xml");
    
        String xmlContent = Files.readAllLines(doc).stream().collect(Collectors.joining());
    
        Writer writer = new StringWriter();
        engine.getTemplate(xmlContent).evaluate(writer, context);
        Files.write(doc, Arrays.asList(new String[] { writer.toString().replaceAll("\n", "<w:br/>") }),
                StandardOpenOption.TRUNCATE_EXISTING);
    
        return Packager.packageDocx(unpackageDocx);
    }

    private static String prepareDocument(Path doc) throws IOException
    {
        String xmlContent = Files.readAllLines(doc).stream().collect(Collectors.joining());
        Document xmlDoc = WebbleMarkupSimplifier.stringToDocument(xmlContent);

        WebbleMarkupSimplifier.simplifyContent(xmlDoc);
        moveStatementsInTableRow(xmlDoc);
        moveStatementsInParagraph(xmlDoc);
        
        // Treat macro and setters
        xmlContent = WebbleMarkupSimplifier.documentToString(xmlDoc);
        xmlContent = removeComments(xmlContent);
        xmlContent = removeNewLines(xmlContent);
        xmlContent = WebbleEngine.filterStatement(xmlContent);
        
        return xmlContent;
    }

    private static String removeNewLines(String xmlContent)
    {
        return xmlContent.replaceAll("\r\n", "");
    }

    private static void moveStatementsInParagraph(Document xmlDoc)
    {
        List<Element> ps = new ArrayList<>();
        xmlDoc.getDescendants(new ElementFilter("p", NS_W)
        {
            @Override
            public Element filter(Object content)
            {
                if (super.filter(content) != null)
                {
                    Element p = (Element) content;
                    
                    String childrenText = getChildrenText(p, NS_W, "r", "t");
                    if (childrenText.trim().matches("(\\{%.*?%\\}\\s*)+"))
                    {
                        return p;
                    }
                    else
                    {
                        return null;
                    }
                }
                else
                {
                    return null;
                }
            }
        }).forEach(ps::add);
        
        for (Element p : ps)
        {
            String childText = getChildrenText(p, NS_W, "r", "t");
            Parent parent = p.getParent();
            int currentIndex = parent.indexOf(p);
            parent.addContent(currentIndex, new Comment(childText));
            p.detach();
        }
    }

    private static void moveStatementsInTableRow(Document xmlDoc)
    {
        List<Element> trs = new ArrayList<>();
        xmlDoc.getDescendants(new ElementFilter("tr", NS_W)
        {
            @Override
            public Element filter(Object content)
            {
                if (super.filter(content) != null)
                {
                    Element tr = (Element) content;

                    String childrenText = getChildrenText(tr, NS_W, "tc", "p", "r", "t");
                    if (childrenText.trim().matches("(\\{%.*?%\\}\\s*)+"))
                    {
                        return tr;
                    }
                    else
                    {
                        return null;
                    }
                }
                else
                {
                    return null;
                }
            }
        }).forEach(trs::add);

        for (Element tr : trs)
        {
            String childText = getChildrenText(tr, NS_W, "tc", "p", "r", "t");
            Parent parent = tr.getParent(); // TR -> TR's PARENT
            int currentIndex = parent.indexOf((Element) tr);
            parent.addContent(currentIndex, new Comment(childText));
            ((Element) tr).detach(); // Detach TR
        }
    }

    private static String removeComments(String xmlContent)
    {
        StringBuffer sb = new StringBuffer();

        Pattern comment = Pattern.compile("\\<!--(.*?)--\\>");

        Matcher m = comment.matcher(xmlContent);
        while (m.find())
        {
            m.appendReplacement(sb, m.group(1));
        }
        m.appendTail(sb);

        return sb.toString();
    }

    private static String getChildrenText(Element e, Namespace ns, String... children)
    {
        List<Element> currentElements = new ArrayList<>();
        currentElements.add(e);

        for (String child : children)
        {
            currentElements = currentElements.stream()
                    .flatMap(c -> c.getChildren(child, ns).stream())
                    .collect(Collectors.toList());
        }

        String childText = currentElements.stream()
                .map(c -> c.getText())
                .map(t -> t == null ? "" : t)
                .collect(Collectors.joining());

        return childText;
    }

    private static String filterStatement(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern statement = Pattern.compile("((\\{\\{)|(\\{%)).*?((\\}\\})|(%\\}))");
        Matcher m = statement.matcher(content);

        while (m.find())
        {
            String filteredContent = m.group(0)
                    .replaceAll("<[^>]+>", "") // Remove XML Nodes
                    .replaceAll("(« ?)|( ?»)", "\"")
                    .replaceAll("(‘ ?)|( ?’)", "'")
                    .replaceAll("&gt;", ">")
                    .replaceAll("&lt;", "<")
                    .replaceAll(" ", " "); // CAREFULL, here we replace Word space ( ) with common space.

            m.appendReplacement(sb, filteredContent);
        }

        m.appendTail(sb);

        return sb.toString();
    }
}
