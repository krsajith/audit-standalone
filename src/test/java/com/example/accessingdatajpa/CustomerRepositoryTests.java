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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CustomerRepositoryTests {
//	@Autowired
//	private TestEntityManager entityManager;

	@Autowired
	private CustomerRepository customers;


	@Test
	public void testUpdatewitoutFetch() {
		var customer = new Customer();
		customer.setUuid(UUID.fromString("6e5cfe54-5b4b-486f-b160-2b15ae859131"));
		customer.setTenantId("System");
		customer.setFirstName("java 21");
		customer.setLastName("Doe");
		customers.existsById(customer.getUuid());
		customer = customers.save(customer);
	}

	@Test
	public void testFindByLastName() {
		var customer = new Customer();
		customer.setTenantId("System");
		customer.setFirstName("Create");
		customer.setLastName("Doe");
		customer = customers.save(customer);

		List<Customer> findByLastName = customers.findByLastName(customer.getLastName());

		assertThat(findByLastName).extracting(Customer::getLastName).containsOnly(customer.getLastName());

		customer.setFirstName("Update 01");
		customers.save(customer);

		customer.setFirstName("Update 02");
		customers.save(customer);
	}
}
