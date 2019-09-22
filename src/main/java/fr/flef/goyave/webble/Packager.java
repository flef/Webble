package fr.flef.goyave.webble;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * This class is used for package and unpackage DOCX documents.
 */
public class Packager
{
    /**
     * Unpackage a docx file.
     * 
     * @throws IOException if docx file cannot be unpackaged as folder.
     */
    static Path unpackageDocx(Path docx) throws IOException
    {
        Path dstFolder = Files.createTempDirectory(docx.getFileName().toString());
        dstFolder.toFile().deleteOnExit();

        try (FileInputStream fis = new FileInputStream(docx.toFile());
                ZipInputStream zis = new ZipInputStream(fis);)
        {
            byte[] buffer = new byte[1024];

            ZipEntry ze = zis.getNextEntry();
            while (ze != null)
            {
                String fileName = ze.getName();
                File newFile = dstFolder.resolve(Paths.get(fileName)).toFile();

                new File(newFile.getParent()).mkdirs();

                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0)
                {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
        }

        return dstFolder;
    }

    /**
     * Package a docx file.
     * 
     * @throws IOException if folder cannot be packaged as docs file.
     */
    static Path packageDocx(Path unpackageDocxFolder) throws IOException
    {
        Path p = Files.createTempFile(unpackageDocxFolder.getFileName().toString(), ".docx");
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(p)))
        {
            Files.walk(unpackageDocxFolder)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path ->
                    {
                        String zipEntryPath = converterFileDelimitersToUnix(unpackageDocxFolder.relativize(path).toString());
                        ZipEntry zipEntry = new ZipEntry(zipEntryPath);
                        try
                        {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        }
                        catch (IOException e)
                        {
                            System.err.println(e);
                        }
                    });
        }
        return p;
    }
    
    /** Used to convert Windows File separator to Unix one as Unix one will work on both Unix and Windows. */
    private static String converterFileDelimitersToUnix(String referencePath)
    {
        return referencePath.replaceAll("\\\\", "/");
    }
}
