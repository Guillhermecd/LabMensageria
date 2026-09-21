package com.bimd.msgsim.service.scenario;

import com.bimd.msgsim.domain.dto.ScenarioRequest;
import com.bimd.msgsim.domain.dto.ScenarioResponse;
import com.bimd.msgsim.domain.mapper.ScenarioMapper;
import com.bimd.msgsim.domain.model.Scenario;
import com.bimd.msgsim.domain.model.User;
import com.bimd.msgsim.exception.BusinessException;
import com.bimd.msgsim.repository.ScenarioRepository;
import com.bimd.msgsim.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final UserRepository userRepository;
    private final ScenarioMapper scenarioMapper;
    private final ScenarioValidator scenarioValidator;

    public List<ScenarioResponse> listForOwner(String ownerEmail) {
        UUID ownerId = ownerId(ownerEmail);
        return scenarioRepository.findByOwnerId(ownerId).stream().map(scenarioMapper::toResponse).toList();
    }

    public ScenarioResponse create(String ownerEmail, ScenarioRequest request) {
        scenarioValidator.validate(request);
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));
        Scenario scenario = new Scenario();
        scenario.setOwner(owner);
        scenarioMapper.updateEntity(request, scenario);
        scenarioRepository.save(scenario);
        return scenarioMapper.toResponse(scenario);
    }

    public ScenarioResponse get(String ownerEmail, UUID id) {
        return scenarioMapper.toResponse(findOwned(ownerEmail, id));
    }

    public ScenarioResponse update(String ownerEmail, UUID id, ScenarioRequest request) {
        scenarioValidator.validate(request);
        Scenario scenario = findOwned(ownerEmail, id);
        scenarioMapper.updateEntity(request, scenario);
        scenarioRepository.save(scenario);
        return scenarioMapper.toResponse(scenario);
    }

    public void delete(String ownerEmail, UUID id) {
        Scenario scenario = findOwned(ownerEmail, id);
        scenarioRepository.delete(scenario);
    }

    public ScenarioResponse duplicate(String ownerEmail, UUID id) {
        Scenario source = findOwned(ownerEmail, id);
        Scenario copy = new Scenario();
        copy.setOwner(source.getOwner());
        copy.setName(source.getName() + " (cópia)");
        copy.setBroker(source.getBroker());
        copy.setRatePerSecond(source.getRatePerSecond());
        copy.setConsumers(source.getConsumers());
        copy.setProcessingMs(source.getProcessingMs());
        copy.setFailurePct(source.getFailurePct());
        copy.setMaxRetries(source.getMaxRetries());
        copy.setMessageSizeKb(source.getMessageSizeKb());
        copy.setDurationSeconds(source.getDurationSeconds());
        copy.setQueueCapacity(source.getQueueCapacity());
        copy.setPartitions(source.getPartitions());
        copy.setVisibilityTimeoutSeconds(source.getVisibilityTimeoutSeconds());
        copy.setRetentionHours(source.getRetentionHours());
        copy.setRetentionMb(source.getRetentionMb());
        copy.setHighWatermarkMb(source.getHighWatermarkMb());
        copy.setPrefetch(source.getPrefetch());
        copy.setInflightMax(source.getInflightMax());
        copy.setDlqEnabled(source.isDlqEnabled());
        copy.setBurstEnabled(source.isBurstEnabled());
        copy.setExecutionMode(source.getExecutionMode());
        copy.setServiceProfile(source.getServiceProfile());
        scenarioRepository.save(copy);
        return scenarioMapper.toResponse(copy);
    }

    private Scenario findOwned(String ownerEmail, UUID id) {
        UUID ownerId = ownerId(ownerEmail);
        return scenarioRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Scenario not found"));
    }

    private UUID ownerId(String ownerEmail) {
        return userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();
    }
}
