package com.bimd.msgsim.domain.mapper;

import com.bimd.msgsim.domain.dto.EventResponse;
import com.bimd.msgsim.domain.dto.RunSummaryResponse;
import com.bimd.msgsim.domain.dto.TickResponse;
import com.bimd.msgsim.domain.model.SimulationEvent;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.domain.model.SimulationTick;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SimulationRunMapper {

    @Mapping(target = "scenarioId", source = "scenario.id")
    RunSummaryResponse toResponse(SimulationRun run);

    TickResponse toResponse(SimulationTick tick);

    EventResponse toResponse(SimulationEvent event);
}
