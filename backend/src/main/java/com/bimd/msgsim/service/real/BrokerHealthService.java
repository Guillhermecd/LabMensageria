package com.bimd.msgsim.service.real;

import com.bimd.msgsim.domain.model.BrokerType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Reports which brokers have a real adapter wired up and whether it's currently reachable —
 * backs the "Simulado × Real" toggle so the frontend can disable REAL for brokers without one. */
@Service
@RequiredArgsConstructor
public class BrokerHealthService {

    private final List<MessageBrokerPort> ports;

    public Map<BrokerType, String> health() {
        Map<BrokerType, String> status = new LinkedHashMap<>();
        for (BrokerType broker : BrokerType.values()) {
            status.put(broker, statusFor(broker));
        }
        return status;
    }

    private String statusFor(BrokerType broker) {
        return ports.stream()
                .filter(p -> p.type() == broker)
                .findFirst()
                .map(p -> p.isHealthy() ? "UP" : "DOWN")
                .orElse("NOT_CONFIGURED");
    }
}
