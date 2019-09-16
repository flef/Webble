package fr.flef.goyave.webble;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.jdom2.Comment;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Parent;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.DOMBuilder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.loader.StringLoader;

public class WebbleEngineXml
{
    /** w: namespace. */
    private final static Namespace NS_W = Namespace.getNamespace("w",
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main");

    /**
     * Prepares the docx to be used as a template. Only to use for multiple evaluation of the same template. For single
     * use, see {@link #evaluate(Path, Map)}.
     * 
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws TransformerException
     * @throws DocumentException
     */
    public static WebbleTemplate prepare(Path docx)
            throws IOException, ParserConfigurationException, SAXException, TransformerException
    {
        Path unpackageDocx = Packager.unpackageDocx(docx);

        List<Path> parts = Files.list(unpackageDocx.resolve("word"))
                .filter(f -> f.getFileName().toString().matches("((document)|(header\\d+)|(footer\\d+)).xml"))
                .collect(Collectors.toList());

        // Nous récupérons une instance de factory qui se chargera de nous fournir
        // un parseur
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Création de notre parseur via la factory
        DocumentBuilder builder = factory.newDocumentBuilder();

        Map<Path, String> templates = new HashMap<>();
        for (Path p : parts)
        {

            if (!p.toString().endsWith("document.xml"))
            {
                continue;
            }

            String content = Files.readAllLines(p).stream()
                    // .map(WebbleEngineXml::preFilterStatement)
                    // .map(WebbleEngineXml::filterStatement)
                    // .map(WebbleEngineXml::filterMacro)
                    // .map(WebbleEngineXml::filterSetters)
                    // .map(WebbleEngineXml::preFilterInlineLoop)
                    // .map(WebbleEngineXml::replaceLoopStart)
                    // .map(WebbleEngineXml::replaceLoopEnd)
                    // .map(WebbleEngineXml::postFilterInlineLoop)
                    // .map(WebbleEngineXml::repeatLinesStart)
                    // .map(WebbleEngineXml::repeatLinesEnd)
                    // .map(WebbleEngineXml::renderBg)
                    // .map(s -> Stream.of(s.split("><")).collect(Collectors.joining(">\r\n<")))
                    .collect(Collectors.joining());

            // parsing de notre fichier via un objet File et récupération d'un
            // objet Document
            // Ce dernier représente la hiérarchie d'objet créée pendant le parsing
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));

            org.w3c.dom.Document w3cDoc = builder.parse(new InputSource(new StringReader(content)));

            Document contentAsXml = new DOMBuilder().build(w3cDoc);

            // Via notre objet Document, nous pouvons récupérer un objet Element
            // Ce dernier représente un élément XML mais, avec la méthode ci-dessous,
            // cet élément sera la racine du document
            Element root = contentAsXml.getRootElement();

            templates.put(p, content);
        }

        return null;
    }

    public static Path evaluate(Path docx, Map<String, Object> context) throws IOException
    {
        Path unpackageDocx = Packager.unpackageDocx(docx);

        PebbleEngine engine = new PebbleEngine.Builder().loader(new StringLoader()).build();

        Path doc = unpackageDocx.resolve("word/document.xml");

        String xmlContent = Files.readAllLines(doc).stream().collect(Collectors.joining());

        Document xmlDoc = WebbleMarkupSimplifier.toDocument(xmlContent);

        WebbleMarkupSimplifier.simplifyContent(xmlDoc);

        xmlContent = WebbleMarkupSimplifier.toString(xmlDoc);
        System.err.println(xmlContent);

        
        replaceTableRowFor(xmlDoc);
        xmlContent = WebbleMarkupSimplifier.toString(xmlDoc);
        System.err.println(xmlContent);
        replaceParagraphFor(xmlDoc);
        replaceInlineFor(xmlDoc);
        
        xmlContent = WebbleMarkupSimplifier.toString(xmlDoc);
        System.err.println(xmlContent);
        
        xmlContent = removeComments(xmlContent).replaceAll("\r\n", "");
        
        

        xmlContent = WebbleEngineXml.filterStatement(xmlContent);
        xmlContent = WebbleEngineXml.filterMacro(xmlContent);
        xmlContent = WebbleEngineXml.renderBg(xmlContent);

        Writer writer = new StringWriter();
        engine.getTemplate(xmlContent).evaluate(writer, context);
        Files.write(doc, Arrays.asList(new String[] { writer.toString().replaceAll("\n", "<w:br/>") }),
                StandardOpenOption.TRUNCATE_EXISTING);

        return Packager.packageDocx(unpackageDocx);
    }

    private static void replaceInlineFor(Document xmlDoc)
    {
        // NOTHING TO DO
    }

    private static void replaceParagraphFor(Document xmlDoc)
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
                    
                    String childrenText = getChildText(p, NS_W, "r", "t");
                    if (childrenText.contains("for") && childrenText.matches("^((?!endfor).)*$")
                    || childrenText.contains("endfor") && !childrenText.matches(".*(%|\\s)+for.*"))
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
            String childText = getChildText(p, NS_W, "r", "t");
            Parent parent = p.getParent();
            int currentIndex = parent.indexOf(p);
            parent.addContent(currentIndex, new Comment(childText));
            p.detach();
        }
    }

    private static void replaceTableRowFor(Document xmlDoc)
    {
        List<Element> tcs = new ArrayList<>();
        xmlDoc.getDescendants(new ElementFilter("tc", NS_W)
        {
            @Override
            public Element filter(Object content)
            {
                if (super.filter(content) != null)
                {
                    Element tc = (Element) content;
                    
                    String childrenText = getChildText(tc, NS_W, "p", "r", "t");
                    if (childrenText.contains("for") && childrenText.matches("^((?!endfor).)*$")
                    || childrenText.contains("endfor") && !childrenText.matches(".*(%|\\s)+for.*"))
                    {
                        return tc;
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
        }).forEach(tcs::add);
        
        for (Element tc : tcs)
        {
            String childText = getChildText(tc, NS_W, "p", "r", "t");
            Parent parent = tc.getParent().getParent(); // TR -> TR's PARENT
            int currentIndex = parent.indexOf((Element) tc.getParent());
            parent.addContent(currentIndex, new Comment(childText));
            ((Element) tc.getParent()).detach(); // Detach TR
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

    private static String getChildText(Element e, Namespace ns, String... children)
    {
        String last = children[children.length - 1];

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


    private static String renderBg(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern endfor = Pattern.compile(
                "(<(w:tr)(?:(?!<\\2 ).)*?)(\\{\\%\\s*renderif([^\\%]*)\\%\\})(.*?</\\2>)");

        Matcher m = endfor.matcher(content);
        while (m.find())
        {
            m.appendReplacement(sb, "{% if " + m.group(4) + "%}" + m.group(1) + m.group(5) + "{% endif %}");
        }
        m.appendTail(sb);

        return sb.toString();
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
    
    private static String filterMacro(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern statement = Pattern.compile("(\\{\\%\\s*macro[^\\%]*\\%\\})"
                + "(.*?)"
                + "(\\{\\%\\s*endmacro\\s*\\%\\})");
        Matcher m = statement.matcher(content);

        while (m.find())
        {
            m.appendReplacement(sb, m.group(1) + m.group(2).replaceAll("<[^>]+>", "") // Remove XML Nodes
                    + "{% endmacro %}");
        }

        m.appendTail(sb);

        return sb.toString();
    }
}
