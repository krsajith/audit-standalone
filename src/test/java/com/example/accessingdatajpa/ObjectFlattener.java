package com.example.accessingdatajpa;

import java.lang.reflect.Field;
import java.util.*;

public class ObjectFlattener {
    public static Map<String, Object> flatten(Object obj) {
        return flatten(obj, "", new TreeMap<>());
    }

    private static Map<String, Object> flatten(Object obj, String prefix, Map<String, Object> result) {
        if (obj == null) {
            result.put(prefix, null);
            return result;
        }

        if (obj instanceof Map) {
            ((Map<?, ?>) obj).forEach((key, value) ->
                    flatten(value, prefix + key + ".", result));
        } else if (obj instanceof Collection) {
            int index = 0;
            for (Object item : (Collection<?>) obj) {
                flatten(item, prefix + index + ".", result);
                index++;
            }
        } else if (obj.getClass().isArray()) {
            Object[] array = (Object[]) obj;
            for (int i = 0; i < array.length; i++) {
                flatten(array[i], prefix + i + ".", result);
            }
        } else if (isPrimitiveOrWrapper(obj)) {
            result.put(prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix, obj);
        } else {
            for (Field field : obj.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    flatten(field.get(obj), prefix + field.getName() + ".", result);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return result;
    }

    private static boolean isPrimitiveOrWrapper(Object obj) {
        return obj instanceof String || obj instanceof Number || obj instanceof Boolean || obj.getClass().isPrimitive();
    }

    public static void main(String[] args) {


        var customer2 = new Customer();
        customer2.setTenantId("System");
        customer2.setFirstName("Create");
        customer2.setLastName("Doe");
        customer2.setAddress(new Address("11", "2"));
        customer2.setAddressList(List.of(new Address("31", "4"),new Address("3", "4")));
        customer2.setSkillSet(Set.of("Java", "Python1","Other"));
        customer2.setStatus(Map.of("status", false,"passed", true));
        Map<String, Object> flatMap = flatten(customer2);
        flatMap.forEach((k, v) -> System.out.println(k + " => " + v));
    }
}
