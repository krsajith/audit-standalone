package com.example.accessingdatajpa;

import com.example.accessingdatajpa.audit.Qualification;
import com.example.accessingdatajpa.audit.model.AbstractBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Getter
@Setter
@MappedSuperclass
public class CustomerBase extends AbstractBaseEntity {
	private String firstName;
	private String lastName;
	private Double salary = 1000.0;
	@Column(columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private Address address;
	private LocalDateTime birthday = LocalDateTime.now();

	@Column(columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private List<Address> addressList;

	@Column(columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private Set<String> skillSet;

	@Column(columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String,Boolean> status;


	@Column(columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<Qualification,Boolean> qualification;
}
