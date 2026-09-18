package com.bimd.msgsim.repository;

import com.bimd.msgsim.domain.model.SimulationRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, UUID> {

    Optional<SimulationRun> findByIdAndScenarioOwnerId(UUID id, UUID ownerId);

    List<SimulationRun> findByScenarioIdOrderByStartedAtDesc(UUID scenarioId);
}
