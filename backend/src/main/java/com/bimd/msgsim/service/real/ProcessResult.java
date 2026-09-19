package com.bimd.msgsim.service.real;

/** What a {@link MessageProcessor} decided to do with one message — the adapter executes it mechanically. */
public enum ProcessResult {
    OK,
    RETRY,
    DROP,
    DLQ
}
