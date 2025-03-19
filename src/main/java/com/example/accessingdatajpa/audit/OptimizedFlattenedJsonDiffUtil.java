package com.example.accessingdatajpa.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility for comparing flattened JSON maps and finding differences
 * This implementation handles arrays with different ordering by using content-based comparison
 */
public class OptimizedFlattenedJsonDiffUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern ARRAY_PATTERN = Pattern.compile("(.*?)\\.([0-9]+)(\\..+)?");
    private static final Configuration JSONPATH_CONFIG = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(EnumSet.of(Option.DEFAULT_PATH_LEAF_TO_NULL))
            .build();

    /**
     * Compare two flattened JSON maps and return differences
     * @param oldFlatMap The original flattened JSON map
     * @param newFlatMap The modified flattened JSON map
     * @return Map containing only the differences
     */
    public static Map<String, Object> getDifferences(Map<String, Object> oldFlatMap, Map<String, Object> newFlatMap) {
        // Convert flattened maps to JSON tree structures for easier manipulation
        JsonNode oldJson = buildJsonFromFlat(oldFlatMap);
        JsonNode newJson = buildJsonFromFlat(newFlatMap);

        // Find differences, handling array elements with smart matching
        Map<String, Object> diffMap = new HashMap<>();
        collectDifferences(oldJson, newJson, "", diffMap);

        return diffMap;
    }

    /**
     * Build a JSON tree structure from a flattened map
     */
    private static JsonNode buildJsonFromFlat(Map<String, Object> flatMap) {
        ObjectNode root = objectMapper.createObjectNode();
        DocumentContext context = JsonPath.using(JSONPATH_CONFIG).parse(root);

        for (Map.Entry<String, Object> entry : flatMap.entrySet()) {
            String path = "$." + entry.getKey().replace(".", ".");
            Object value = entry.getValue();

            try {
                if (value == null) {
                    context.set(path, null);
                } else if (value instanceof Number) {
                    context.set(path, value);
                } else if (value instanceof Boolean) {
                    context.set(path, value);
                } else {
                    context.set(path, value.toString());
                }
            } catch (Exception e) {
                // Create missing parent structures if needed
                createParentStructures(context, path);

                // Try again after creating parent structures
                if (value == null) {
                    context.set(path, null);
                } else if (value instanceof Number) {
                    context.set(path, value);
                } else if (value instanceof Boolean) {
                    context.set(path, value);
                } else {
                    context.set(path, value.toString());
                }
            }
        }

        return context.json();
    }

    /**
     * Create missing parent structures for a JSON path
     */
    private static void createParentStructures(DocumentContext context, String path) {
        String[] parts = path.split("\\.");
        StringBuilder currentPath = new StringBuilder("$");

        for (int i = 1; i < parts.length - 1; i++) {
            String part = parts[i];

            if (part.matches("[0-9]+")) {
                // This is an array index
                int index = Integer.parseInt(part);
                try {
                    // Check if array exists
                    JsonNode node = context.read(currentPath.toString());
                    if (node == null || !node.isArray()) {
                        context.set(currentPath.toString(), objectMapper.createArrayNode());
                    }

                    // Ensure array has enough elements
                    ArrayNode array = (ArrayNode) context.read(currentPath.toString());
                    while (array.size() <= index) {
                        array.addObject();
                    }
                } catch (Exception e) {
                    // Create array if it doesn't exist
                    context.set(currentPath.toString(), objectMapper.createArrayNode());
                    ArrayNode array = (ArrayNode) context.read(currentPath.toString());
                    while (array.size() <= index) {
                        array.addObject();
                    }
                }
            } else {
                // This is an object field
                try {
                    JsonNode node = context.read(currentPath + "." + part);
                    if (node == null) {
                        context.set(currentPath + "." + part, objectMapper.createObjectNode());
                    }
                } catch (Exception e) {
                    context.set(currentPath + "." + part, objectMapper.createObjectNode());
                }
            }

            currentPath.append(".").append(part);
        }
    }

    /**
     * Recursively collect differences between two JSON structures
     */
    @SuppressWarnings("unchecked")
    private static void collectDifferences(JsonNode oldJson, JsonNode newJson, String path, Map<String, Object> diffMap) {
        // If types are different, consider it a complete change
        if (oldJson == null && newJson == null) {
            return;
        }

        if (oldJson == null || newJson == null || oldJson.getNodeType() != newJson.getNodeType()) {
            addDiffValue(path, newJson, diffMap);
            return;
        }

        switch (newJson.getNodeType()) {
            case OBJECT:
                compareObjects(oldJson, newJson, path, diffMap);
                break;

            case ARRAY:
                compareArrays(oldJson, newJson, path, diffMap);
                break;

            default:
                // Value comparison for primitives, string, etc.
                if (!Objects.equals(oldJson.asText(), newJson.asText())) {
                    addDiffValue(path, newJson, diffMap);
                }
                break;
        }
    }

    /**
     * Compare two JSON objects
     */
    private static void compareObjects(JsonNode oldObj, JsonNode newObj, String path, Map<String, Object> diffMap) {
        // Check fields in new object
        Iterator<Map.Entry<String, JsonNode>> fields = newObj.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();
            JsonNode newValue = entry.getValue();
            JsonNode oldValue = oldObj.has(fieldName) ? oldObj.get(fieldName) : null;

            String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;
            collectDifferences(oldValue, newValue, fieldPath, diffMap);
        }

        // Check for deleted fields
        Iterator<String> oldFields = oldObj.fieldNames();
        while (oldFields.hasNext()) {
            String fieldName = oldFields.next();
            if (!newObj.has(fieldName)) {
                // Field was deleted
                // Uncomment to track deletions
                // diffMap.put(path + "." + fieldName, null);
            }
        }
    }

    /**
     * Compare two JSON arrays - handling potentially different element orders
     */
    private static void compareArrays(JsonNode oldArray, JsonNode newArray, String path, Map<String, Object> diffMap) {
        // If arrays are empty or different sizes, consider it a complete replacement
        if (oldArray.size() == 0 || newArray.size() == 0 || oldArray.size() != newArray.size()) {
            addDiffValue(path, newArray, diffMap);
            return;
        }

        // First, check if the arrays are exactly the same
        boolean exactMatch = true;
        for (int i = 0; i < newArray.size(); i++) {
            JsonNode oldValue = oldArray.get(i);
            JsonNode newValue = newArray.get(i);

            if (!areNodesEqual(oldValue, newValue)) {
                exactMatch = false;
                break;
            }
        }

        if (exactMatch) {
            return; // Arrays are identical
        }

        // If not an exact match, check if elements are the same but in different order
        List<JsonNode> oldElements = new ArrayList<>();
        for (int i = 0; i < oldArray.size(); i++) {
            oldElements.add(oldArray.get(i));
        }

        // For primitive arrays or simple values, do a set-based comparison
        if (isPrimitiveArray(oldArray) && isPrimitiveArray(newArray)) {
            Set<String> oldSet = new HashSet<>();
            Set<String> newSet = new HashSet<>();

            for (int i = 0; i < oldArray.size(); i++) {
                oldSet.add(oldArray.get(i).asText());
                newSet.add(newArray.get(i).asText());
            }

            if (oldSet.equals(newSet)) {
                // Same elements, different order - no difference
                return;
            } else {
                // Different elements
                addDiffValue(path, newArray, diffMap);
                return;
            }
        }

        // For object arrays, try to match elements and check differences
        boolean[] matchedOld = new boolean[oldArray.size()];

        for (int newIndex = 0; newIndex < newArray.size(); newIndex++) {
            JsonNode newElement = newArray.get(newIndex);
            String elementPath = path + "." + newIndex;

            // Try to find matching element in old array
            boolean foundMatch = false;
            int bestMatchIndex = -1;
            double bestSimilarity = 0.0;

            for (int oldIndex = 0; oldIndex < oldArray.size(); oldIndex++) {
                if (matchedOld[oldIndex]) continue;

                JsonNode oldElement = oldArray.get(oldIndex);
                double similarity = calculateSimilarity(oldElement, newElement);

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatchIndex = oldIndex;

                    // If perfect match, no need to check further
                    if (similarity == 1.0) {
                        break;
                    }
                }
            }

            // Use best match if similarity is high enough
            if (bestSimilarity >= 0.7) {
                matchedOld[bestMatchIndex] = true;
                JsonNode oldElement = oldArray.get(bestMatchIndex);
                collectDifferences(oldElement, newElement, elementPath, diffMap);
            } else {
                // No good match found, consider it a new element
                addDiffValue(elementPath, newElement, diffMap);
            }
        }
    }

    /**
     * Calculate similarity between two JSON nodes (0.0 to 1.0)
     */
    private static double calculateSimilarity(JsonNode node1, JsonNode node2) {
        if (node1.getNodeType() != node2.getNodeType()) {
            return 0.0;
        }

        if (!node1.isObject()) {
            return areNodesEqual(node1, node2) ? 1.0 : 0.0;
        }

        // For objects, count matching fields
        int matchingFields = 0;
        int totalFields = 0;

        Iterator<String> fieldNames = node1.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            totalFields++;

            if (node2.has(fieldName) && areNodesEqual(node1.get(fieldName), node2.get(fieldName))) {
                matchingFields++;
            }
        }

        // Count fields in node2 that aren't in node1
        Iterator<String> node2Fields = node2.fieldNames();
        while (node2Fields.hasNext()) {
            String fieldName = node2Fields.next();
            if (!node1.has(fieldName)) {
                totalFields++;
            }
        }

        return totalFields == 0 ? 1.0 : (double) matchingFields / totalFields;
    }

    /**
     * Check if two nodes are equal
     */
    private static boolean areNodesEqual(JsonNode node1, JsonNode node2) {
        if (node1 == null && node2 == null) return true;
        if (node1 == null || node2 == null) return false;
        return node1.equals(node2);
    }

    /**
     * Check if an array contains only primitive values
     */
    private static boolean isPrimitiveArray(JsonNode array) {
        for (int i = 0; i < array.size(); i++) {
            JsonNode element = array.get(i);
            if (element.isObject() || element.isArray()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Add a difference value to the diff map
     */
    private static void addDiffValue(String path, JsonNode value, Map<String, Object> diffMap) {
        if (path.isEmpty()) {
            return;
        }

        // Convert JsonNode to appropriate Java type
        Object javaValue;
        if (value == null) {
            javaValue = null;
        } else if (value.isNull()) {
            javaValue = null;
        } else if (value.isTextual()) {
            javaValue = value.textValue();
        } else if (value.isInt()) {
            javaValue = value.intValue();
        } else if (value.isLong()) {
            javaValue = value.longValue();
        } else if (value.isDouble()) {
            javaValue = value.doubleValue();
        } else if (value.isBoolean()) {
            javaValue = value.booleanValue();
        } else if (value.isObject() || value.isArray()) {
            // For complex objects, add each field individually
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                addDiffValue(path + "." + entry.getKey(), entry.getValue(), diffMap);
            }
            return;
        } else {
            javaValue = value.toString();
        }

        diffMap.put(path, javaValue);
    }

    /**
     * Example usage
     */
    public static void main(String[] args) {
        // Example flattened JSON maps
        Map<String, Object> oldMap = new HashMap<>();
        oldMap.put("name", "John");
        oldMap.put("age", 30);
        oldMap.put("address.city", "New York");
        oldMap.put("address.zip", 10001);
        oldMap.put("hobbies.0", "Reading");
        oldMap.put("hobbies.1", "Gaming");
        oldMap.put("previousAddresses.0.city", "Los Angeles");
        oldMap.put("previousAddresses.0.zip", 90001);
        oldMap.put("previousAddresses.1.city", "Chicago");
        oldMap.put("previousAddresses.1.zip", 60007);

        Map<String, Object> newMap = new HashMap<>();
        newMap.put("name", "John");
        newMap.put("age", 31); // Changed
        newMap.put("address.city", "New York");
        newMap.put("address.zip", 10001);
        newMap.put("hobbies.0", "Reading");
        newMap.put("hobbies.1", "Cooking"); // Changed
        // Order swapped in previousAddresses
        newMap.put("previousAddresses.0.city", "Chicago");
        newMap.put("previousAddresses.0.zip", 60007);
        newMap.put("previousAddresses.1.city", "Los Angeles");
        newMap.put("previousAddresses.1.zip", 90001);

        Map<String, Object> diff = getDifferences(oldMap, newMap);
        System.out.println("Differences: " + diff);
    }
}
