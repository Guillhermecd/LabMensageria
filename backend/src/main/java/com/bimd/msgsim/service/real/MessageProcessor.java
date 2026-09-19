package com.bimd.msgsim.service.real;

/** Decides pass/fail/retry/give-up for one message. All simulation-of-failure logic lives
 * here (in {@code RealSimulationService}), never in the broker adapter — the adapter only
 * knows how to mechanically ack/republish/dead-letter, not what a scenario's failure rate is. */
@FunctionalInterface
public interface MessageProcessor {

    ProcessResult process(byte[] payload, int retryCount);
}
