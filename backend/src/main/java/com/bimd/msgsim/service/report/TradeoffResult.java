package com.bimd.msgsim.service.report;

import com.bimd.msgsim.domain.model.BrokerType;
import java.util.List;

public record TradeoffResult(List<BrokerScore> rows, BrokerScore best) {

    public BrokerScore find(BrokerType broker) {
        return rows.stream().filter(r -> r.model().broker() == broker).findFirst().orElseThrow();
    }
}
