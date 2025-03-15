package com.example.accessingdatajpa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

public interface CustomerRepository extends CrudRepository<Customer, UUID> {
	List<Customer> findByLastName(String lastName);
}
