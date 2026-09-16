package co.edu.eci.blueprints.persistence.impl;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlueprintJpaRepository extends JpaRepository<BlueprintEntity, Long> {
    Optional<BlueprintEntity> findByAuthorAndName(String author, String name);
    List<BlueprintEntity> findByAuthor(String author);
}
