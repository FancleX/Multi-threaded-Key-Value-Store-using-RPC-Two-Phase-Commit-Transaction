package org.neu.db;

import java.util.HashMap;
import java.util.Map;

/**
 * Database
 */
public class DB {

    // create a hash map to storage key value pairs
    private final Map<String, String> db;

    public DB() {
        this.db = new HashMap<>();
    }

    /**
     * Get a value by a key.
     *
     * @param key the key to be found
     * @return the value of the key if the key is available, otherwise null
     */
    public synchronized String get(String key) {
        return db.get(key);
    }

    /**
     * Insert or update a key with its value.
     *
     * @param key the key to be inserted or updated
     * @param value the value of the key
     */
    public synchronized void put(String key, String value) {
        db.put(key, value);
    }

    /**
     * Delete a key value pair from the storage.
     *
     * @param key the key to be deleted
     */
    public synchronized void delete(String key) {
        db.remove(key);
    }

    /**
     * Determine if the database has the key
     *
     * @param key key
     * @return true if it has, otherwise false
     */
    public synchronized boolean isContain(String key) { return db.containsKey(key); }

    /**
     * Get the database entities
     *
     * @return all entities
     */
    public synchronized Map<String, String> getDB() {
        return db;
    }

    /**
     * Store amount of entities
     *
     * @param data data
     */
    public synchronized void addAll(Map<String, String> data) {
        db.putAll(data);
    }

}
