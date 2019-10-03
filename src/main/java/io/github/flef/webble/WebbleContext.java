package io.github.flef.webble;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The {@link WebbleContext} holds all the values to bind to the {@link WebbleTemplate}. Values can be document
 * properties or rendered objects.
 */
public class WebbleContext
{
    private static final String MODIFIED = "modified";
    private static final String CREATED = "created";
    private static final String REVISION = "revision";
    private static final String LAST_MODIFIED_BY = "lastModifiedBy";
    private static final String DESCRIPTION = "description";
    private static final String KEYWORDS = "keywords";
    private static final String CREATOR = "creator";
    private static final String SUBJECT = "subject";
    private static final String TITLE = "title";
    
    private final WordProperty<String> propertyCoreTitle = new WordProperty<>(TITLE, WordProperty.STRING);
    private final WordProperty<String> propertyCoreSubject = new WordProperty<>(SUBJECT, String::valueOf);
    private final WordProperty<String> propertyCoreCreator = new WordProperty<>(CREATOR, WordProperty.STRING);
    private final WordProperty<String> propertyCoreKeywords = new WordProperty<>(KEYWORDS, WordProperty.STRING);
    private final WordProperty<String> propertyCoreDescription = new WordProperty<>(DESCRIPTION,
            WordProperty.STRING);
    private final WordProperty<String> propertyCoreLastModifiedBy = new WordProperty<>(LAST_MODIFIED_BY,
            WordProperty.STRING);
    private final WordProperty<Integer> propertyCoreRevision = new WordProperty<>(REVISION, WordProperty.INTEGER);
    private final WordProperty<Instant> propertyCoreCreated = new WordProperty<>(CREATED, WordProperty.DATE);
    private final WordProperty<Instant> propertyCoreModified = new WordProperty<>(MODIFIED, WordProperty.DATE);

    private final Map<String, WordProperty<?>> coreProperties = new HashMap<>();
    private final Map<String, String> customProperties = new HashMap<>();
    private final Map<String, Object> objectsBindings = new HashMap<>();
    
    public WebbleContext()
    {
        coreProperties.put(MODIFIED , propertyCoreModified);
        coreProperties.put(CREATED , propertyCoreCreated);
        coreProperties.put(REVISION , propertyCoreRevision);
        coreProperties.put(LAST_MODIFIED_BY , propertyCoreLastModifiedBy);
        coreProperties.put(DESCRIPTION , propertyCoreDescription);
        coreProperties.put(KEYWORDS , propertyCoreKeywords);
        coreProperties.put(CREATOR , propertyCoreCreator);
        coreProperties.put(SUBJECT , propertyCoreSubject);
        coreProperties.put(TITLE , propertyCoreTitle);
    }

    /**
     * Bind the given reference (used in templates) with the given object to be render when evaluating the template.
     * Overrides precendent bindings.
     * 
     * @param reference the template object reference.
     * @param value     the value for the given reference.
     */
    public void bind(String reference, Object value)
    {
        objectsBindings.put(reference, value);
    }
    
    /**
     * Unbind the given reference (to not be used used in templates).
     * 
     * @param reference the template object reference.
     */
    public void unbind(String reference)
    {
        objectsBindings.remove(reference);
    }
    
    
    /**
     * Returns the bindings betwin pebble properties and objects.
     * @return the bindings betwin pebble properties and objects.
     */
    Map<String, Object> getBindings()
    {
        return Collections.unmodifiableMap(objectsBindings);
    }

    /**
     * Set the custom property of the Word document.
     * 
     * @param reference the property's reference.
     * @param value     the value to set.
     */
    public void setCustomProperty(String reference, String value)
    {
        customProperties.put(reference, value);
    }

    /**
     * Set the Document's property 'Title'.
     * 
     * @param value the value to set.
     */
    public void setPropertyTitle(String value)
    {
        propertyCoreTitle.setValue(value);
    }

    /**
     * Set the Document's property 'Subject'.
     * 
     * @param value the value to set.
     */
    public void setPropertySubject(String value)
    {
        propertyCoreSubject.setValue(value);
    }

    /**
     * Set the Document's property 'Creator'.
     * 
     * @param value the value to set.
     */
    public void setPropertyCreator(String value)
    {
        propertyCoreCreator.setValue(value);
    }

    /**
     * Set the Document's property 'Keywords'.
     * 
     * @param value the value to set.
     */
    public void setPropertyKeywords(String value)
    {
        propertyCoreKeywords.setValue(value);
    }

    /**
     * Set the Document's property 'Description'.
     * 
     * @param value the value to set.
     */
    public void setPropertyDescription(String value)
    {
        propertyCoreDescription.setValue(value);
    }

    /**
     * Set the Document's property 'LastModifiedBy'.
     * 
     * @param value the value to set.
     */
    public void setPropertyLastModifiedBy(String value)
    {
        propertyCoreLastModifiedBy.setValue(value);
    }

    /**
     * Set the Document's property 'Revision'.
     * 
     * @param value the value to set.
     */
    public void setPropertyRevision(Integer value)
    {
        propertyCoreRevision.setValue(value);
    }

    /**
     * Set the Document's property 'Created'.
     * 
     * @param value the value to set.
     */
    public void setPropertyCreated(Instant value)
    {
        propertyCoreCreated.setValue(value);
    }

    /**
     * Set the Document's property 'Modified'.
     * 
     * @param value the value to set.
     */
    public void setPropertyModified(Instant value)
    {
        propertyCoreModified.setValue(value);
    }
    
    
    
    Map<String, WordProperty<?>> getCoreProperties()
    {
        return coreProperties;
    }

    
    Map<String, String> getCustomProperties()
    {
        return customProperties;
    }


    static class WordProperty<T>
    {
        static final Function<String, String> STRING = s -> s;
        static final Function<Integer, String> INTEGER = i -> Integer.toString(i);
        static final Function<Instant, String> DATE = Instant::toString;

        private final String referer;
        private final Function<T, String> formatter;
        private T value;

        WordProperty(String referer, Function<T, String> formatter)
        {
            this.referer = referer;
            this.formatter = formatter;
        }

        public String getReferer()
        {
            return referer;
        }

        public T getValue()
        {
            return value;
        }
        
        public String getFormattedValue()
        {
            return formatter.apply(value);
        }

        public void setValue(T value)
        {
            this.value = value;
        }

        public boolean isSet()
        {
            return true;
        }
    }
}
