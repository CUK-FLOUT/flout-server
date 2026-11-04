package com.flout.server.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "jurisdictions",
    uniqueConstraints = @UniqueConstraint(name = "uk_jurisdictions_name", columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Jurisdiction {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "jurisdiction_id")
  private Long id;

  @Column(nullable = false, length = 50)
  private String name;
}
