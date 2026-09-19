package com.bimd.msgsim.domain.mapper;

import com.bimd.msgsim.domain.dto.ScenarioRequest;
import com.bimd.msgsim.domain.dto.ScenarioResponse;
import com.bimd.msgsim.domain.model.Scenario;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ScenarioMapper {

    ScenarioResponse toResponse(Scenario scenario);

    void updateEntity(ScenarioRequest request, @MappingTarget Scenario scenario);
}
