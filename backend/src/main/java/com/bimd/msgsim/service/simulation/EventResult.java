package com.bimd.msgsim.service.simulation;

import com.bimd.msgsim.domain.model.EventType;

public record EventResult(int second, EventType type, String message) {
}
