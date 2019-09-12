package fr.flef.goyave.webble;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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

import javax.sound.midi.SysexMessage;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.filter.AttributeFilter;
import org.jdom2.filter.ElementFilter;
import org.jdom2.filter.Filter;
import org.jdom2.filter.Filters;
import org.jdom2.input.DOMBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlunit.matchers.CompareMatcher;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.loader.StringLoader;
import com.sun.org.apache.xml.internal.utils.NameSpace;

public class WebbleEngineXml
{
       
    /** 
     * Prepares the docx to be used as a template.
     * Only to use for multiple evaluation of the same template.
     * For single use, see {@link #evaluate(Path, Map)}.
     * @throws ParserConfigurationException 
     * @throws SAXException 
     * @throws TransformerException 
     * @throws DocumentException 
     */
    public static WebbleTemplate prepare(Path docx) throws IOException, ParserConfigurationException, SAXException, TransformerException
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
//                    .map(WebbleEngineXml::preFilterStatement)
//                    .map(WebbleEngineXml::filterStatement)
//                    .map(WebbleEngineXml::filterMacro)
                   // .map(WebbleEngineXml::filterSetters)
                   // .map(WebbleEngineXml::preFilterInlineLoop)
                   // .map(WebbleEngineXml::replaceLoopStart)
                   // .map(WebbleEngineXml::replaceLoopEnd)
                   // .map(WebbleEngineXml::postFilterInlineLoop)
                   // .map(WebbleEngineXml::repeatLinesStart)
                   // .map(WebbleEngineXml::repeatLinesEnd)
                   // .map(WebbleEngineXml::renderBg)
//                    .map(s -> Stream.of(s.split("><")).collect(Collectors.joining(">\r\n<")))
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

        Writer writer = new StringWriter();
        PebbleEngine engine = new PebbleEngine.Builder().loader(new StringLoader()).build();

        Path doc = unpackageDocx.resolve("word/document.xml");

        Files.readAllLines(doc).stream()
                .map(WebbleMarkupSimplifier::simplifyContent)
                .map(WebbleEngineXml::preFilterStatement)
                .map(WebbleEngineXml::filterStatement)
                .map(WebbleEngineXml::filterMacro)
                .map(WebbleEngineXml::preFilterInlineLoop)
                .map(WebbleEngineXml::replaceLoopStart)
                .map(WebbleEngineXml::replaceLoopEnd)
                .map(WebbleEngineXml::postFilterInlineLoop)
                .map(WebbleEngineXml::repeatLinesStart)
                .map(WebbleEngineXml::repeatLinesEnd)
                .map(WebbleEngineXml::renderBg)
                .map(engine::getTemplate)
                .forEach(t ->
                {
                    try
                    {
                        t.evaluate(writer, context);
                    }
                    catch (IOException e)
                    {
                        System.err.println(writer.toString());
                        e.printStackTrace();
                    }
                });
        
        

        Files.write(doc, Arrays.asList(new String[] { writer.toString().replaceAll("\n", "<w:br/>") }),
                StandardOpenOption.TRUNCATE_EXISTING);

        return Packager.packageDocx(unpackageDocx);
    }

    private static String replaceLoopStart(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern endfor = Pattern.compile(
                "(<(w:p)(?:(?!<\\2 ).)*?)(\\{\\%\\s*for[^\\%]*\\%\\}).*?</\\2>");

        Matcher m = endfor.matcher(content);
        while (m.find())
        {
            m.appendReplacement(sb, m.group(3));
        }
        m.appendTail(sb);

        return sb.toString();
    }

    private static String replaceLoopEnd(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern endfor = Pattern.compile(
                "(<(w:p)(?:(?!<\\2 ).)*?)(\\{\\%\\s*endfor[^\\%]*\\%\\}).*?</\\2>");

        Matcher m = endfor.matcher(content);
        while (m.find())
        {
            m.appendReplacement(sb, "{% endfor %}");
        }
        m.appendTail(sb);

        return sb.toString();
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

    private static String preFilterInlineLoop(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern endfor = Pattern.compile(
                "(<(w:p)(?:(?!<\\2 ).)*?)(\\{\\%\\s*for[^\\%]*\\%\\})"
                        + "((?:(?!(?:<\\2|for) ).)*?)"
                        + "(\\{\\%\\s*endfor\\s*\\%\\})(.*?</\\2>)");

        Matcher m = endfor.matcher(content);
        while (m.find())
        {
            m.appendReplacement(sb, m.group(0).replaceAll("for", "INLINED_FOR"));
        }
        m.appendTail(sb);

        return sb.toString();
    }

    private static String postFilterInlineLoop(String content)
    {
        return content.replaceAll("INLINED_FOR", "for");
    }

    private static String repeatLinesStart(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern endfor = Pattern.compile(
                "(<(w:tr)(?:(?!<\\2 ).)*?)(\\{\\%\\s*repeat (.*?) as (([^\\%])*?)\\%\\})(.*?</\\2>)");

        Matcher m = endfor.matcher(content);
        // 2 repeat can be on the same line. Matcher has to be reset to match both.
        while (m.find())
        {
            m.appendReplacement(sb, "{% for " + m.group(5) + " in " + m.group(4) + " %}"
                    + m.group(1) + m.group(7));
            m.appendTail(sb);
            m.reset(sb.toString());
            sb = new StringBuffer();
        }

        m.appendTail(sb);

        return sb.toString();
    }

    private static String repeatLinesEnd(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern endfor = Pattern.compile(
                "(<(w:tr)(?:(?!<\\2 ).)*?)(\\{\\%\\s*endrepeat(([^\\%])*?)\\%\\})(.*?</\\2>)");

        Matcher m = endfor.matcher(content);

        // 2 endrepeat can be on the same line. Matcher has to be reset to match both.
        while (m.find())
        {
            m.appendReplacement(sb, m.group(1) + m.group(6) + "{% endfor %}");
            m.appendTail(sb);
            m.reset(sb.toString());
            sb = new StringBuffer();
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

    private static String preFilterStatement(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern statement = Pattern.compile("\\{(?:(?!%).)*?\\{(.*?)\\}.*?\\}");
        Matcher m = statement.matcher(content);

        while (m.find())
        {
            m.appendReplacement(sb, "{{" + m.group(1).replaceAll("<[^>]+>", "") + "}}");
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
