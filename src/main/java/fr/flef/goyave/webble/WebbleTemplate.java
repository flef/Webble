package fr.flef.goyave.webble;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A WebbleTemplate is a pre processed Word document, ready to be used.
 */
public class WebbleTemplate
{
    private static final String WEBBLE_EXTENSION = ".wbbl";
    
    private final Path templatePath;
    private final String name;
    
    /**
     * The class constructor.
     * @param name the name fo the template
     */
    WebbleTemplate(Path webbleTemplate, String name)
    {
        this.templatePath = webbleTemplate;
        this.name = name;
    }
    
    /**
     * Persists this {@link WebbleTemplate} to the given folder.
     * @param dstFolder the folder in which template should be persit.
     * @param filename the template filename.
     * @throws IOException if the file cannot be persited.
     */
    public void persist(Path dstFolder, String filename) throws IOException
    {
        Files.copy(templatePath, new FileOutputStream(new File(dstFolder.resolve(filename + WEBBLE_EXTENSION).toString())));
    }
    
    /**
     * Persists this {@link WebbleTemplate} to the given folder.
     * @param sourceFile the persisted {@link WebbleTemplate}.
     * @throws IOException if the file cannot be read..
     */
    public static WebbleTemplate load(Path sourceFile) throws IOException
    {
        Path template = Files.createTempFile(sourceFile.getFileName().toString(), ".docx");
        Files.copy(sourceFile, new FileOutputStream(template.toFile()));
        return new WebbleTemplate(template, sourceFile.getFileName().toString());
    }

    /**
     * Returns the template path.
     */
    public Path getTemplatePath()
    {
        return templatePath;
    }

    /**
     * Returns the name.
     */
    public String getName()
    {
        return name;
    }
}
