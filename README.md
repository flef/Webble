# Webble
Word integration for Pebble Template Engine

[![Maven Central](https://img.shields.io/badge/maven%20central-1.0.0-success)](https://oss.sonatype.org/content/groups/public/io/github/flef/webble/1.0.0)

See [Pebble](https://github.com/PebbleTemplates/pebble).

Create Word Template Document for Java Templating directly inside word.

Easy to create, easy to customize, easy to generate.

Full example at https://github.com/flef/Webble/blob/master/src/test/resources/example.docx

![example](https://i.ibb.co/2tBqNCy/2019-12-09-17h13-58.png)


```java
package io.github.flef.webble;

public class UseCaseTest
{
    private static final  WebbleContext CONTEXT = new WebbleContext();
    
    static
    {
        // Bindings
        CONTEXT.bind("names", Arrays.asList(new String[] { "Nal AYA", "Flo LEF" }));
        CONTEXT.bind("dates", Arrays.asList(new Date[] { Date.from(Instant.now()) }));
        CONTEXT.bind("multiline", "This is line1.\nThis is line 2.");
        CONTEXT.bind("aujd", Date.from(Instant.now()));
        String[][] items = new String[][] { new String[] { "list1_item1", "list1_item2" },
            new String[] { "list2_item1", "list2_item2", "list2_item3" } };
        CONTEXT.bind("list", items);
        
        // Document properties
        CONTEXT.setPropertyTitle("Template title");
        CONTEXT.setPropertyCreator("Webble_user");
        CONTEXT.setPropertyLastModifiedBy("Webble_user");
        CONTEXT.setPropertySubject("Webble example");
        CONTEXT.setPropertyDescription("This is an example of webble template");
        CONTEXT.setPropertyModified(Instant.now());
        
        CONTEXT.setCustomProperty("_PROPERTY_TEST", "Value of Property _PROPERTY_TEST.");
    }
    
    /**
     * Tries to generate a document from an unprepared docx template document (.docx).
     */
    public void generateOnce() throws IOException
    {
        System.out.println("Document generated at: " + WebbleEngine.evaluate(docx, CONTEXT));
    }

    /**
     * Tries to generate many document from an already prepared docx template document (.wbbl).
     */
    public void prepareAndPersist() throws IOException
    {
        Path docx = Paths.get(UseCaseTest.class.getClassLoader().getResource("example.docx").toURI());
        
        // Prepare docx -> .wbbl
        WebbleTemplate template = WebbleEngine.prepare(docx);
        
        Path dst = Files.createTempDirectory("WEBBLE_TEST");
        template.persist(dst, "template");
        
        // Prepare docx -> .wbbl
        WebbleTemplate persistedTemplate = WebbleTemplate.load(dst.resolve("template.wbbl"));
        System.out.println("Document generated at: " + WebbleEngine.evaluate(persistedTemplate, CONTEXT));
    }
}
```
