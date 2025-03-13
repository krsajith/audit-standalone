package com.example.accessingdatajpa;

import com.example.accessingdatajpa.audit.model.AbstractBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class Customer extends AbstractBaseEntity {
	private String firstName;
	private String lastName;
}
