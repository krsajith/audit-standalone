/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.accessingdatajpa;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.annotation.Rollback;

import java.util.List;
import java.util.Set;
import java.util.UUID;


@SpringBootTest
@ComponentScan(basePackages = {"com.taomish.utils"},basePackageClasses = {})
public class DynamicUpdateTests {
//	@Autowired
//	private TestEntityManager entityManager;

	@Autowired
	private CustomerRepository customers;

	@Autowired
	private EntityManager entityManager;


	@Test
	void testCreateCustomer() {
		var customer = new Customer();
		customer.setUuid(UUID.fromString("742baf3a-6a3d-4911-81e4-9412d916d1d3"));
		customer.setTenantId("System");
		customer.setFirstName("java 210dd");
		customer.setLastName("Doe");
		customers.existsById(customer.getUuid());
		customer = customers.save(customer);


//		var dc = entityManager.find(DynamicCustomer.class, customer.getUuid());
//		dc.setFirstName("Java");
//		dc.setLastName("Doe");
//		entityManager.persist(dc);
	}

	@Test
	@Transactional
	@Rollback(false)
	void testUpdateCustomer() {
		var dc = entityManager.find(DynamicCustomer.class, UUID.fromString("41490830-96a2-4092-b1d4-b8237ae2ddd2"));
		dc.setFirstName("Updated First Name");
		dc.setLastName("Updated Last Name");
		entityManager.persist(dc);
		entityManager.flush();
	}

}
