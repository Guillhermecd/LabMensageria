package com.bimd.msgsim.service.report;

import java.util.List;

public record BrokerScore(BrokerModel model, int score, boolean best, List<String> pros, List<String> cons) {
}
