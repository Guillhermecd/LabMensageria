package com.bimd.msgsim.controller;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.service.real.BrokerHealthService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BrokerController {

    private final BrokerHealthService healthService;

    @GetMapping("/api/brokers/health")
    public Map<BrokerType, String> health() {
        return healthService.health();
    }
}
