package fr.flef.goyave.webble;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.loader.StringLoader;

public class WebbleEngine
{
    /** 
     * Prepares the docx to be used as a template.
     * Only to use for multiple evaluation of the same template.
     * For single use, see {@link #evaluate(Path, Map)}.
     */
    public static WebbleTemplate prepare(Path docx) throws IOException
    {
        Path unpackageDocx = Packager.unpackageDocx(docx);

        List<Path> parts = Files.list(unpackageDocx.resolve("word"))
                .filter(f -> f.getFileName().toString().matches("((document)|(header\\d+)|(footer\\d+)).xml"))
                .collect(Collectors.toList());

        Map<Path, String> templates = new HashMap<>();
        for (Path p : parts)
        {
            String template = Files.readAllLines(p).stream()
                    .map(WebbleEngine::preFilterStatement)
                    .map(WebbleEngine::filterStatement)
                    .map(WebbleEngine::filterMacro)
                    .map(WebbleEngine::filterSetters)
                    .map(WebbleEngine::preFilterInlineLoop)
                    .map(WebbleEngine::replaceLoopStart)
                    .map(WebbleEngine::replaceLoopEnd)
                    .map(WebbleEngine::postFilterInlineLoop)
                    .map(WebbleEngine::repeatLinesStart)
                    .map(WebbleEngine::repeatLinesEnd)
                    .map(WebbleEngine::renderBg)
                    .collect(Collectors.joining());
            
            templates.put(p, template);
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
                .map(WebbleEngine::preFilterStatement)
                .map(WebbleEngine::filterStatement)
                .map(WebbleEngine::filterMacro)
                .map(WebbleEngine::filterSetters)
                .map(WebbleEngine::preFilterInlineLoop)
                .map(WebbleEngine::replaceLoopStart)
                .map(WebbleEngine::replaceLoopEnd)
                .map(WebbleEngine::postFilterInlineLoop)
                .map(WebbleEngine::repeatLinesStart)
                .map(WebbleEngine::repeatLinesEnd)
                .map(WebbleEngine::renderBg)
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
                    .replaceAll("</?w:.*?>", "") // Remove XML Nodes
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
            m.appendReplacement(sb, "{{" + m.group(1) + "}}");
        }

        m.appendTail(sb);

        return sb.toString();
    }

    private static String filterMacro(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern statement = Pattern.compile("(<(w:p)(?:(?!<\\2 ).)*?)(\\{\\%\\s*macro[^\\%]*\\%\\})"
                + "((?:(?!<\\2 ).)*?)"
                + "(\\{\\%\\s*endmacro\\s*\\%\\})(.*?</\\2>)");
        Matcher m = statement.matcher(content);

        while (m.find())
        {
            m.appendReplacement(sb, m.group(3) + m.group(4) + "{% endmacro %}");
        }

        m.appendTail(sb);

        return sb.toString();
    }

    private static String filterSetters(String content)
    {
        StringBuffer sb = new StringBuffer();

        Pattern statement = Pattern.compile("(<(w:p)(?:(?!<\\2 ).)*?)(\\{\\%\\s*set[^\\%]*\\%\\}).*?</\\2>");
        Matcher m = statement.matcher(content);

        while (m.find())
        {
            m.appendReplacement(sb, m.group(3));
        }

        m.appendTail(sb);

        return sb.toString();
    }

}
