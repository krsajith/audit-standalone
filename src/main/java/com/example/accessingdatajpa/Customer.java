package com.example.accessingdatajpa;

import com.example.accessingdatajpa.audit.model.AbstractBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "customer")
@Getter
@Setter
public class Customer extends AbstractBaseEntity {
	private String firstName;
	private String lastName;
}
