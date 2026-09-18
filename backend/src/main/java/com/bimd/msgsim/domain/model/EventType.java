package com.bimd.msgsim.domain.model;

public enum EventType {
    SATURATION,
    QUEUE_FULL,
    FIRST_DLQ,
    LAG,
    BURST_START,
    BURST_END,
    RECOVERED,
    FINISHED
}
