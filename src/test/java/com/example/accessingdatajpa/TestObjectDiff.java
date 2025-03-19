package com.example.accessingdatajpa;

import com.example.accessingdatajpa.audit.ObjectDiffUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class TestObjectDiff {

    @Test
    public void testUpdatewitoutFetch() throws Exception {
        var customer1 = new Customer();
        customer1.setTenantId("System");
        customer1.setFirstName("Create");
        customer1.setLastName("Doe");
        customer1.setAddress(new Address("1","2"));
        customer1.setAddressList(List.of(new Address("3","4")));
        customer1.setSkillSet(Set.of("Java", "Python"));


        var customer2 = new Customer();
        customer2.setTenantId("System");
        customer2.setFirstName("Create");
        customer2.setLastName("Doe");
        customer2.setAddress(new Address("11","2"));
        customer2.setAddressList(List.of(new Address("31","4")));
        customer2.setSkillSet(Set.of("Java1", "Python1"));
        System.out.println(ObjectDiffUtil.getDifferencesAsJson(null,customer1));
        var json = ObjectDiffUtil.getDifferencesAsJson(customer1,customer2);
        System.out.println(json);
    }
}
