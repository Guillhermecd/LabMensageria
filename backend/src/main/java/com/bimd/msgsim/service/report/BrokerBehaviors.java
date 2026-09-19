package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.service.simulation.broker.BrokerBehavior;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Small shared lookup so narrative/insights don't each re-index the same {@link BrokerBehavior} list. */
@Component
public class BrokerBehaviors {

    private final Map<BrokerType, BrokerBehavior> byType;

    public BrokerBehaviors(List<BrokerBehavior> implementations) {
        this.byType = new EnumMap<>(BrokerType.class);
        implementations.forEach(behavior -> byType.put(behavior.type(), behavior));
    }

    public BrokerBehavior get(BrokerType broker) {
        return byType.get(broker);
    }
}
