package com.bimd.msgsim.service.simulation.broker;

/** What a broker does with a message the producer is trying to publish. */
public enum Admission {
    /** Stored in the queue/log. */
    ACCEPT,
    /** The producer stops publishing until the backlog falls; nothing is lost (RabbitMQ memory alarm). */
    BLOCK_PRODUCER,
    /** The message is refused and lost. */
    REJECT
}
