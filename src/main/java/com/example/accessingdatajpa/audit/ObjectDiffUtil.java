package com.example.accessingdatajpa.audit;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Field;
import java.util.*;

public class ObjectDiffUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Compare two objects of any type and return a JSON structure containing only the differences
     * @param oldObj The original object
     * @param newObj The modified object
     * @return JSON string representing differences
     */
    public static String getDifferencesAsJson(Object oldObj, Object newObj) throws Exception {
        Map<String, Object> diff = getDifferences(oldObj, newObj);
        return objectMapper.writeValueAsString(diff);
    }

    /**
     * Compare two objects of any type and return a Map structure containing only the differences
     * @param oldObj The original object
     * @param newObj The modified object
     * @return Map representing differences
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getDifferences(Object oldObj, Object newObj) throws Exception {
        // Case: both null - no difference
        if (oldObj == null && newObj == null) {
            return Collections.emptyMap();
        }

        // Case: one is null, the other isn't - there's a difference
        if (oldObj == null || newObj == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("value", newObj);  // Will include null if newObj is null
            return result;
        }

        // If types are different, return the new value
        if (!oldObj.getClass().equals(newObj.getClass())) {
            Map<String, Object> result = new HashMap<>();
            result.put("value", newObj);
            return result;
        }

        Map<String, Object> diffMap = new HashMap<>();
        Class<?> clazz = oldObj.getClass();

        // Handle primitive types, String, and enums
        if (isPrimitiveOrWrapper(clazz) || clazz == String.class || clazz.isEnum()) {
            if (!Objects.equals(oldObj, newObj)) {
                Map<String, Object> result = new HashMap<>();
                result.put("value", newObj);
                return result;
            }
            return Collections.emptyMap();
        }

        // Handle collections
        if (Collection.class.isAssignableFrom(clazz)) {
            return handleCollectionDiff((Collection<?>) oldObj, (Collection<?>) newObj);
        }

        // Handle arrays
        if (clazz.isArray()) {
            Object[] oldArray = convertToObjectArray(oldObj);
            Object[] newArray = convertToObjectArray(newObj);
            return handleArrayDiff(oldArray, newArray);
        }

        // Handle maps
        if (Map.class.isAssignableFrom(clazz)) {
            return handleMapDiff((Map<?, ?>) oldObj, (Map<?, ?>) newObj);
        }

        // Handle regular objects with fields
        for (Field field : getAllFields(clazz)) {
            field.setAccessible(true);
            String fieldName = field.getName();
            Object oldValue = field.get(oldObj);
            Object newValue = field.get(newObj);

            // Check for changes, including null values
            if (!Objects.equals(oldValue, newValue)) {
                // Handle case where field is set to null in new object
                if (newValue == null) {
                    diffMap.put(fieldName, null);
                } else {
                    Map<String, Object> fieldDiff = getDifferences(oldValue, newValue);
                    if (!fieldDiff.isEmpty()) {
                        diffMap.put(fieldName, fieldDiff.size() == 1 && fieldDiff.containsKey("value") ?
                                fieldDiff.get("value") : fieldDiff);
                    }
                }
            }
        }

        return diffMap;
    }

    private static Object[] convertToObjectArray(Object array) {
        if (array instanceof Object[]) {
            return (Object[]) array;
        }

        int length = java.lang.reflect.Array.getLength(array);
        Object[] result = new Object[length];
        for (int i = 0; i < length; i++) {
            result[i] = java.lang.reflect.Array.get(array, i);
        }
        return result;
    }

    private static Map<String, Object> handleArrayDiff(Object[] oldArray, Object[] newArray) throws Exception {
        if (oldArray == null && newArray == null) {
            return Collections.emptyMap();
        }

        if (oldArray == null || newArray == null || oldArray.length != newArray.length) {
            Map<String, Object> result = new HashMap<>();
            result.put("value", newArray);
            return result;
        }

        // For arrays, create a list of differences by index
        boolean hasDifferences = false;
        List<Object> diffList = new ArrayList<>();

        for (int i = 0; i < oldArray.length; i++) {
            if (oldArray[i] == null && newArray[i] == null) {
                diffList.add(null);
            } else if (oldArray[i] == null || newArray[i] == null || !Objects.equals(oldArray[i], newArray[i])) {
                hasDifferences = true;
                if (newArray[i] == null) {
                    diffList.add(null);
                } else {
                    Map<String, Object> elementDiff = getDifferences(oldArray[i], newArray[i]);
                    diffList.add(elementDiff.size() == 1 && elementDiff.containsKey("value") ?
                            elementDiff.get("value") : elementDiff);
                }
            } else {
                diffList.add(newArray[i]);
            }
        }

        if (hasDifferences) {
            Map<String, Object> result = new HashMap<>();
            result.put("value", diffList);
            return result;
        }

        return Collections.emptyMap();
    }

    private static Map<String, Object> handleCollectionDiff(Collection<?> oldCollection, Collection<?> newCollection) throws Exception {
        if (oldCollection == null && newCollection == null) {
            return Collections.emptyMap();
        }

        // Convert collections to arrays for easier processing
        Object[] oldArray = oldCollection == null ? null : oldCollection.toArray();
        Object[] newArray = newCollection == null ? null : newCollection.toArray();

        return handleArrayDiff(oldArray, newArray);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> handleMapDiff(Map<?, ?> oldMap, Map<?, ?> newMap) throws Exception {
        Map<String, Object> diffMap = new HashMap<>();

        if (oldMap == null && newMap == null) {
            return Collections.emptyMap();
        }

        if (oldMap == null) {
            return convertToMap(newMap);
        }

        if (newMap == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("value", null);
            return result;
        }

        // Check for added/modified keys
        for (Object key : newMap.keySet()) {
            Object newValue = newMap.get(key);
            Object oldValue = oldMap.get(key);
            String keyStr = key.toString();

            // Handle null values explicitly
            if (newValue == null && oldValue == null) {
                continue; // No change
            } else if (newValue == null) {
                diffMap.put(keyStr, null); // Value changed to null
            } else if (oldValue == null || !oldMap.containsKey(key)) {
                diffMap.put(keyStr, newValue); // Added or changed from null
            } else if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> valueDiff = getDifferences(oldValue, newValue);
                if (!valueDiff.isEmpty()) {
                    diffMap.put(keyStr, valueDiff.size() == 1 && valueDiff.containsKey("value") ?
                            valueDiff.get("value") : valueDiff);
                }
            }
        }

        // Check for removed keys
        Set<Object> removedKeys = new HashSet<>(oldMap.keySet());
        removedKeys.removeAll(newMap.keySet());

        // Uncomment if you want to track removed keys
        /*
        for (Object key : removedKeys) {
            diffMap.put(key.toString() + "_removed", true);
        }
        */

        return diffMap;
    }

    private static Map<String, Object> convertToMap(Object obj) {
        if (obj == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("value", null);
            return result;
        }

        // For primitive types, wrap in a value key
        if (isPrimitiveOrWrapper(obj.getClass()) || obj instanceof String || obj.getClass().isEnum()) {
            Map<String, Object> result = new HashMap<>();
            result.put("value", obj);
            return result;
        }

        // Handle collections and arrays specially
        if (obj instanceof Collection || obj.getClass().isArray()) {
            Map<String, Object> result = new HashMap<>();
            result.put("value", obj);
            return result;
        }

        try {
            // Use Jackson for conversion to map for complex objects
            return objectMapper.convertValue(obj, Map.class);
        } catch (IllegalArgumentException e) {
            // Fallback for objects that can't be directly converted
            Map<String, Object> result = new HashMap<>();
            result.put("value", obj.toString());
            return result;
        }
    }

    private static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == Boolean.class ||
                clazz == Character.class ||
                Number.class.isAssignableFrom(clazz);
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;

        // Get fields from class and all superclasses
        while (currentClass != null && currentClass != Object.class) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }

        return fields;
    }
}
