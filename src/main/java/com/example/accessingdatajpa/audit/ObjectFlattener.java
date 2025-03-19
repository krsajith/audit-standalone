package com.example.accessingdatajpa.audit;

import com.example.accessingdatajpa.Address;
import com.example.accessingdatajpa.Customer;
import com.example.accessingdatajpa.audit.model.AbstractBaseEntity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Primitives;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.comparators.NullComparator;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ObjectFlattener {

    private static Set<String>  baseFields = FieldUtils.getAllFieldsList(AbstractBaseEntity.class).stream()
            .map(Field::getName)
            .collect(Collectors.toSet());
    static Map<String, List<Field>> fieldMap = Maps.newHashMap();

    public static Map<String, Object> flatten(Object obj,String prefix) {
        return flatten(obj, prefix, new TreeMap<>());
    }

    private static Map<String, Object> flatten(Object obj, String prefix, Map<String, Object> result) {
        if (obj == null) {
            result.put(prefix, null);
            return result;
        }

        if (obj instanceof LocalDateTime) {

        } else
        if (obj instanceof Map) {
            // Use Guava's immutable sorting for consistent map key ordering
            Map<?, ?> map = (Map<?, ?>) obj;
            if (MapUtils.isNotEmpty(map)) {
                List<Map.Entry<?, ?>> entries = Lists.newArrayList(map.entrySet());

                // Sort entries by key string representation
                Collections.sort(entries,
                        Ordering.from(new NullComparator<Map.Entry<?, ?>>(
                                (e1, e2) -> String.valueOf(e1.getKey()).compareTo(String.valueOf(e2.getKey()))
                        )));

                for (Map.Entry<?, ?> entry : entries) {
                    flatten(entry.getValue(), prefix + entry.getKey() + ".", result);
                }
            }
        } else if (obj instanceof Collection) {
            Collection<?> collection = (Collection<?>) obj;
            if (CollectionUtils.isNotEmpty(collection)) {
                List<?> sortedItems;

                if (obj instanceof List) {
                    // Preserve list order by default
                    sortedItems = Lists.newArrayList(collection);
                } else {
                    // For Sets and other collections, establish deterministic ordering
                    // using Guava's Ordering with string representation comparator
                    Comparator<Object> stringComparator =
                            (o1, o2) -> String.valueOf(o1).compareTo(String.valueOf(o2));
                    sortedItems = Ordering
                            .from(new NullComparator<Object>(stringComparator))
                            .sortedCopy(collection);
                }

                int index = 0;
                for (Object item : sortedItems) {
                    flatten(item, prefix + index + ".", result);
                    index++;
                }
            } else {
                // Handle empty collection case
                result.put(prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix,
                        Collections.emptyList());
            }
        } else if (obj.getClass().isArray()) {
            Object[] array = (Object[]) obj;
            if (array.length > 0) {
                // Use Guava's immutable list copy for safety
                List<?> asList = ImmutableList.copyOf(array);
                int index = 0;
                for (Object item : asList) {
                    flatten(item, prefix + index + ".", result);
                    index++;
                }
            } else {
                // Handle empty array case
                result.put(prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix,
                        new Object[0]);
            }
        } else if (isPrimitiveOrWrapper(obj)) {
            result.put(prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix, obj);
        } else if (obj instanceof Enum) {
            // Special handling for enum values
            Enum<?> enumValue = (Enum<?>) obj;
            result.put(prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix,
                    enumValue.name());
        } else {
            List<Field> fields = getFields(obj);

            // Sort fields by name for consistent ordering


            for (Field field : fields) {
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    flatten(value, prefix + field.getName() + ".", result);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Error accessing field: " + field.getName(), e);
                }
            }
        }

        return result;
    }

    private static List<Field> getFields(Object obj) {
        if(!fieldMap.containsKey(obj.getClass().getName())) {
            var fields = new ArrayList<>(FieldUtils.getAllFieldsList(obj.getClass()).stream()
                    .filter(field -> !baseFields.contains(field.getName())).toList());
            fields.sort(Comparator.comparing(Field::getName));
            fieldMap.put(obj.getClass().getName(), fields);
        }
       return fieldMap.get(obj.getClass().getName());
    }

    public static boolean isPrimitiveOrWrapper(Object obj) {
        if (obj == null) {
            return false;
        }

        Class<?> clazz = obj.getClass();
        return clazz.isPrimitive() ||
                Primitives.isWrapperType(clazz) ||
                obj instanceof String ||
                obj instanceof Date ||
                obj instanceof LocalDateTime ||
                obj instanceof Calendar;
        // Enum handling moved to separate case
    }

    public static void main(String[] args) {
        var customer1 = new Customer();
        customer1.setTenantId("System");
        customer1.setFirstName("Create");
//        customer1.setLastName("Doe");
        customer1.setAddress(new Address("1", "2"));
        customer1.setAddressList(List.of(new Address("3", "4")));
        customer1.setStatus(Map.of("status", true));
        customer1.setSkillSet(Set.of("Java", "Python", "CSS", "JavaScript"));
        customer1.setQualification(Map.of(Qualification.BS,true,Qualification.MS,false));


        var customer2 = new Customer();
//        customer2.setTenantId("System");
        customer2.setFirstName("Create");
        customer2.setLastName("Doe");
        customer2.setAddress(new Address("11", "2"));
        customer2.setAddressList(List.of(new Address("31", "4"), new Address("3", "4")));
        customer2.setSkillSet(Set.of("Java", "Python1", "Other"));
        customer2.setStatus(Map.of("status", false, "passed", true));
        customer2.setQualification(Map.of(Qualification.BS,true,Qualification.MS,true));

//        Sets.difference(customer1.getSkillSet(), customer2.getSkillSet()).forEach(System.out::println);

        Map<String, Object> flatten1 = flatten(customer1,"");
        Map<String, Object> flatten2 = flatten(customer2,"");
        var difference = Maps.difference(flatten1, flatten2);

        var diffMap = new HashMap<String, String>();
        difference.entriesDiffering().forEach((k, v) -> {
            if (!ObjectUtils.isEmpty(v.leftValue()) || !ObjectUtils.isEmpty(v.rightValue())) {
                diffMap.put(k, v.leftValue() + "->" + v.rightValue());
            }
        });

        difference.entriesOnlyOnLeft().forEach((k, v) -> {
            if (!ObjectUtils.isEmpty(v)) diffMap.put(k, "-" + v);
        });

        difference.entriesOnlyOnRight().forEach((k, v) -> {
            if (!ObjectUtils.isEmpty(v)) diffMap.put(k, "+" + v);
        });


//        difference.entriesDiffering().forEach();
        System.out.println(diffMap);
    }
}
