package fr.flef.goyave.webble;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;

public class Main
{

    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException
    {
        
        
        Path docx = Paths.get("C:\\Users\\Florian\\Desktop\\Weble\\step5.docx");

        
        try
        {
            WebbleEngineXml.prepare(docx);
        }
        catch (TransformerException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
//        
//        
        final Map<String, Object> context = new HashMap<>();
        context.put("names", Arrays.asList(new String[] { "Nalish AYA", "Florian LEF" }));
        context.put("dates", Arrays.asList(new Date[] { Date.from(Instant.now()) }));
        context.put("multiline", "This is line1.\nThis is line 2.");

        context.put("aujd", Date.from(Instant.now()));

        String[][] items = new String[][] { new String[] { "list1_item1", "list1_item2" },
            new String[] { "list2_item1", "list2_item2", "list2_item3" } };

        context.put("list", items);

        
        System.err.println(WebbleEngineXml.evaluate(docx, context));
    }

}
