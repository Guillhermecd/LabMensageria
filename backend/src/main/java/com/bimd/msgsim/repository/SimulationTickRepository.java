package com.bimd.msgsim.repository;

import com.bimd.msgsim.domain.model.SimulationTick;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationTickRepository extends JpaRepository<SimulationTick, UUID> {

    List<SimulationTick> findByRunIdOrderBySecondAsc(UUID runId);
}
