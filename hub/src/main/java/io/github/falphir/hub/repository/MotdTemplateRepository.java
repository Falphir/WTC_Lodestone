package io.github.falphir.hub.repository;

import java.util.List;
import java.util.Optional;

import io.github.falphir.hub.entity.MotdTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MotdTemplateRepository extends JpaRepository<MotdTemplate, Long> {

    List<MotdTemplate> findAllByOrderByNameAsc();

    Optional<MotdTemplate> findByIsDefaultTrue();

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsByName(String name);
}
