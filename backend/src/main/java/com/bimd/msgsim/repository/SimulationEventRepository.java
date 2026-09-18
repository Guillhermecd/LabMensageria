package com.bimd.msgsim.repository;

import com.bimd.msgsim.domain.model.SimulationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationEventRepository extends JpaRepository<SimulationEvent, UUID> {

    List<SimulationEvent> findByRunIdOrderBySecondAsc(UUID runId);
}
