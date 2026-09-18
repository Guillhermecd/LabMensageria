package com.bimd.msgsim.domain.dto;

import com.bimd.msgsim.domain.model.EventType;

public record EventResponse(int second, EventType type, String message) {
}
