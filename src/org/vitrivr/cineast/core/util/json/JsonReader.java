package org.vitrivr.cineast.core.util.json;

import java.io.File;

/**
 * Wraps the Json to Object deserialization so as to make sure that it can be provided
 * independently of a concrete library.
 *
 * @author rgasser
 * @version 1.0
 * @created 13.01.17
 */
public interface JsonReader {
    /**
     * Tries to deserialize a JSON string into a object of the provided class. On error
     * this method returns null. Exceptions must be handled by the implementation!
     *
     * @param jsonString String to deserialize.
     * @param c Class to which the string should be deserialized.
     * @param <T> Type of Object that should be returned.
     * @return Object of type <T> on success or null otherwise.
     */
    public <T> T toObject(String jsonString, Class<T> c);

    /**
     * Tries to deserialize a JSON file into a object of the provided class. On error
     * this method returns null. Exceptions must be handled by the implementation!
     *
     * @param json File to deserialize.
     * @param c Class to which the string should be deserialized.
     * @param <T> Type of Object that should be returned.
     * @return Object of type <T> on success or null otherwise.
     */
    public <T> T toObject(File json, Class<T> c);
}