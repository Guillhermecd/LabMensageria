package com.bimd.msgsim.service.simulation;

/** A batch of failed messages scheduled to rejoin the queue at second {@code at}. */
record RetryBucket(int at, int count) {
}
