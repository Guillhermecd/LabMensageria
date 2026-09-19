package com.bimd.msgsim.repository;

import com.bimd.msgsim.domain.model.Scenario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {

    List<Scenario> findByOwnerId(UUID ownerId);

    Optional<Scenario> findByIdAndOwnerId(UUID id, UUID ownerId);
}
