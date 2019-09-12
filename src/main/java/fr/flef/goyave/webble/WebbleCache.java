package fr.flef.goyave.webble;

import java.nio.file.Path;
import java.util.Map;

public interface WebbleCache
{
    public void cache(Path template, String reference);
    
    public Path evaluate(String reference, Map<String, Object> context);
}
