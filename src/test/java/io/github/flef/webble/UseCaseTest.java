package io.github.flef.webble;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.Test;

import io.github.flef.webble.WebbleEngine;
import io.github.flef.webble.WebbleTemplate;

/**
 * Test on a valid docx template document.
 */
public class UseCaseTest
{
    private static final  Map<String, Object> CONTEXT = new HashMap<>();
    
    static
    {
        CONTEXT.put("names", Arrays.asList(new String[] { "Nal AYA", "Flo LEF" }));
        CONTEXT.put("dates", Arrays.asList(new Date[] { Date.from(Instant.now()) }));
        CONTEXT.put("multiline", "This is line1.\nThis is line 2.");
        CONTEXT.put("aujd", Date.from(Instant.now()));
        String[][] items = new String[][] { new String[] { "list1_item1", "list1_item2" },
            new String[] { "list2_item1", "list2_item2", "list2_item3" } };
        CONTEXT.put("list", items);
    }
    
    /**
     * Tries to generate a document from an unprepared docx template document (.docx).
     * @throws URISyntaxException 
     * @throws IOException 
     */
    @Test
    public void generateOnce() throws URISyntaxException, IOException
    {
        Path docx = Paths.get(UseCaseTest.class.getClassLoader().getResource("example.docx").toURI());
        
        // Generate document
        System.out.println("Document generated at:" + WebbleEngine.evaluate(docx, CONTEXT));
    }
    
    /**
     * Tries to generate many document from an unprepared docx template document (.docx).
     * @throws URISyntaxException 
     * @throws IOException 
     */
    @Test
    public void generateManyTimes() throws URISyntaxException, IOException
    {
        Path docx = Paths.get(UseCaseTest.class.getClassLoader().getResource("example.docx").toURI());
        
        // Prepare docx -> .wbbl
        WebbleTemplate template = WebbleEngine.prepare(docx);
        System.out.println("Document 1 generated at:" + WebbleEngine.evaluate(template, CONTEXT));
        System.out.println("Document 2 generated at:" + WebbleEngine.evaluate(template, CONTEXT));
    }
    
    /**
     * Tries to generate many document from an already prepared docx template document (.wbbl).
     * @throws URISyntaxException 
     * @throws IOException 
     */
    @Test
    public void generateFromPreparedTemplate() throws URISyntaxException, IOException
    {
        Path wbbl = Paths.get(UseCaseTest.class.getClassLoader().getResource("example.wbbl").toURI());
        
        // Prepare docx -> .wbbl
        WebbleTemplate template = WebbleTemplate.load(wbbl);
        System.out.println("Document generated at:" + WebbleEngine.evaluate(template, CONTEXT));
    }
    
    /**
     * Tries to generate many document from an already prepared docx template document (.wbbl).
     * @throws URISyntaxException 
     * @throws IOException 
     */
    @Test
    public void prepareAndPersist() throws URISyntaxException, IOException
    {
        Path docx = Paths.get(UseCaseTest.class.getClassLoader().getResource("example.docx").toURI());
        
        // Prepare docx -> .wbbl
        WebbleTemplate template = WebbleEngine.prepare(docx);
        
        Path dst = Files.createTempDirectory("WEBBLE_TEST");
        template.persist(dst, "template");
        
        // Prepare docx -> .wbbl
        WebbleTemplate persistedTemplate = WebbleTemplate.load(dst.resolve("template.wbbl"));
        System.out.println("Document generated at:" + WebbleEngine.evaluate(persistedTemplate, CONTEXT));
    }
}
