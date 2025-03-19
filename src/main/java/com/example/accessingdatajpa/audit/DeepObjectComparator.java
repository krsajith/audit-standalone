package com.example.accessingdatajpa.audit;

import com.example.accessingdatajpa.Address;
import com.example.accessingdatajpa.Customer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.*;

public class DeepObjectComparator {

    private static final int MAX_DEPTH = 5;

    /**
     * Compare two objects up to 5 levels deep and return differences
     * @param obj1 First object to compare
     * @param obj2 Second object to compare
     * @return Map with paths of differences (e.g. "addresses.0.city") as keys and arrays [obj1Value, obj2Value] as values
     */
    public static Map<String, Object[]> compareObjects(Object obj1, Object obj2) {
        Map<String, Object[]> differences = new HashMap<>();
        compareObjects(obj1, obj2, "", 0, differences);
        return differences;
    }

    private static void compareObjects(Object obj1, Object obj2, String path, int depth, Map<String, Object[]> differences) {
        if (depth >= MAX_DEPTH) {
            return;
        }

        // Both null or same reference
        if (obj1 == obj2) {
            return;
        }

        // One is null, the other isn't
        if (obj1 == null || obj2 == null) {
            differences.put(path, new Object[]{obj1, obj2});
            return;
        }

        // Different class types
        if (!obj1.getClass().equals(obj2.getClass())) {
            differences.put(path, new Object[]{obj1, obj2});
            return;
        }

        // Handle primitive types and their wrapper classes
        if (isPrimitiveOrWrapper(obj1.getClass()) || obj1 instanceof String) {
            if (!obj1.equals(obj2)) {
                differences.put(path, new Object[]{obj1, obj2});
            }
            return;
        }

        // Handle collections (List, Set)
        if (obj1 instanceof Collection) {
            compareCollections((Collection<?>) obj1, (Collection<?>) obj2, path, depth, differences);
            return;
        }

        // Handle Maps
        if (obj1 instanceof Map) {
            compareMaps((Map<?, ?>) obj1, (Map<?, ?>) obj2, path, depth, differences);
            return;
        }

        // Handle custom objects using reflection
        try {
            List<Field> fields = getAllFields(obj1.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                Object value1 = field.get(obj1);
                Object value2 = field.get(obj2);

                String newPath = path.isEmpty() ? field.getName() : path + "." + field.getName();
                compareObjects(value1, value2, newPath, depth + 1, differences);
            }
        } catch (IllegalAccessException e) {
            differences.put(path, new Object[]{obj1, obj2});
        }
    }

    private static void compareCollections(Collection<?> coll1, Collection<?> coll2, String path, int depth, Map<String, Object[]> differences) {
        // Check if both collections are empty
        if (coll1.isEmpty() && coll2.isEmpty()) {
            return;
        }

        // Collections of different sizes
        if (coll1.size() != coll2.size()) {
            differences.put(path + ".size", new Object[]{coll1.size(), coll2.size()});
        }

        // Convert both collections to lists for easier handling
        List<?> items1 = new ArrayList<>(coll1);
        List<?> items2 = new ArrayList<>(coll2);

        // Track matched items
        boolean[] matched1 = new boolean[items1.size()];
        boolean[] matched2 = new boolean[items2.size()];

        // First pass: try to match elements with each other
        for (int i = 0; i < items1.size(); i++) {
            Object item1 = items1.get(i);

            for (int j = 0; j < items2.size(); j++) {
                if (matched2[j]) continue;

                Object item2 = items2.get(j);
                Map<String, Object[]> tempDiff = new HashMap<>();
                compareObjects(item1, item2, "temp", depth, tempDiff);

                if (tempDiff.isEmpty()) {
                    // Found a match
                    matched1[i] = true;
                    matched2[j] = true;
                    break;
                }
            }
        }

        // Second pass: report unmatched items and compare matching ones
        for (int i = 0; i < items1.size(); i++) {
            if (!matched1[i]) {
                // This is an item in collection1 that has no match in collection2
                compareObjects(items1.get(i), null, path + "." + i, depth + 1, differences);
            } else {
                // Find what this matched with and compare them properly
                for (int j = 0; j < items2.size(); j++) {
                    if (matched2[j]) {
                        Map<String, Object[]> tempDiff = new HashMap<>();
                        compareObjects(items1.get(i), items2.get(j), "temp", depth, tempDiff);

                        if (tempDiff.isEmpty()) {
                            // This is the match - compare at proper depth and path
                            compareObjects(items1.get(i), items2.get(j), path + "." + i, depth + 1, differences);
                            break;
                        }
                    }
                }
            }
        }

        // Third pass: report items in collection2 that don't exist in collection1
        for (int j = 0; j < items2.size(); j++) {
            if (!matched2[j]) {
                // This is a new item in collection2
                compareObjects(null, items2.get(j), path + "." + j, depth + 1, differences);
            }
        }
    }

    private static void compareMaps(Map<?, ?> map1, Map<?, ?> map2, String path, int depth, Map<String, Object[]> differences) {
        // Check all keys in map1
        for (Object key : map1.keySet()) {
            String newPath = path.isEmpty() ? key.toString() : path + "." + key;
            Object value1 = map1.get(key);
            Object value2 = map2.containsKey(key) ? map2.get(key) : null;

            compareObjects(value1, value2, newPath, depth + 1, differences);
        }

        // Check for keys in map2 that are not in map1
        for (Object key : map2.keySet()) {
            if (!map1.containsKey(key)) {
                String newPath = path.isEmpty() ? key.toString() : path + "." + key;
                compareObjects(null, map2.get(key), newPath, depth + 1, differences);
            }
        }
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

    private static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == Boolean.class ||
                clazz == Character.class ||
                clazz == Byte.class ||
                clazz == Short.class ||
                clazz == Integer.class ||
                clazz == Long.class ||
                clazz == Float.class ||
                clazz == String.class ||
                clazz == Double.class;
    }

    // Example usage
//    public static void main(String[] args) {
//        TestAddress addr1 = new TestAddress("New York", "Main St", "10001");
//        TestAddress addr2 = new TestAddress("New York", "Broadway", "10001");
//        TestAddress addr3 = new TestAddress("Boston", "Oak St", "02101");
//
//        TestPerson person1 = new TestPerson("John", 30);
//        person1.addAddress(addr1);
//        person1.addAddress(addr2);
//
//        TestPerson person2 = new TestPerson("John", 30);
//        person2.addAddress(addr1);
//        person2.addAddress(addr3); // Different address
//
//        Map<String, Object[]> differences = DeepObjectComparator.compareObjects(person1, person2);
//
//        // Print differences
//        for (Map.Entry<String, Object[]> entry : differences.entrySet()) {
//            System.out.println(entry.getKey() + ": " +
//                    (entry.getValue()[0] != null ? entry.getValue()[0] : "null") + " vs " +
//                    (entry.getValue()[1] != null ? entry.getValue()[1] : "null"));
//        }
//    }

    // Example test classes
    static class TestPerson {
        private String name;
        private int age;
        private List<TestAddress> addresses = new ArrayList<>();

        public TestPerson(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public void addAddress(TestAddress address) {
            addresses.add(address);
        }

        @Override
        public String toString() {
            return "Person{name='" + name + "', age=" + age + "}";
        }
    }

    static class TestAddress {
        private String city;
        private String street;
        private String zipCode;

        public TestAddress(String city, String street, String zipCode) {
            this.city = city;
            this.street = street;
            this.zipCode = zipCode;
        }

        @Override
        public String toString() {
            return "Address{city='" + city + "', street='" + street + "', zip='" + zipCode + "'}";
        }
    }


    public static void main(String[] args) throws JsonProcessingException {
        var customer1 = new Customer();
        customer1.setTenantId("System");
        customer1.setFirstName("Create");
        customer1.setLastName("Doe");
        customer1.setAddress(new Address("1", "2"));
        customer1.setAddressList(List.of(new Address("3", "4")));
        customer1.setStatus(Map.of("status", true));
        customer1.setSkillSet(Set.of("Java", "Python"));


        var customer2 = new Customer();
        customer2.setTenantId("System");
        customer2.setFirstName("Create");
        customer2.setLastName("Doe");
        customer2.setAddress(new Address("11", "2"));
        customer2.setAddressList(List.of(new Address("31", "4"),new Address("3", "4")));
        customer2.setSkillSet(Set.of("Java", "Python1","Other"));
        customer2.setStatus(Map.of("status", false,"passed", true));
        var diff = DeepObjectComparator.compareObjects(customer1, customer2);
        var objectMapper = new ObjectMapper();
        System.out.println(objectMapper.writeValueAsString(diff));

    }

}
