package com.bimd.msgsim.domain.dto;

import com.bimd.msgsim.domain.model.BrokerType;
import java.util.List;

public record TradeoffResponse(
        BrokerType broker,
        boolean best,
        int score,
        double capacity,
        double ratio,
        double p50Ms,
        double p99Ms,
        long backlog,
        long loss,
        double lossPct,
        double cost,
        List<String> pros,
        List<String> cons) {
}
