package com.bimd.msgsim.service.simulation;

import java.util.Arrays;

/** FIFO of waiting messages stored in flat arrays (a run can hold millions), oldest first. */
final class MessageQueue {

    private double[] arrivalMs = new double[1024];
    private int[] failures = new int[1024];
    private int head;
    private int size;
    /** Failed attempts of the message returned by the last {@link #poll()}. */
    private int lastFailures;

    int size() {
        return size;
    }

    void add(double arrival, int failedAttempts) {
        if (size == arrivalMs.length) {
            grow();
        }
        int tail = (head + size) % arrivalMs.length;
        arrivalMs[tail] = arrival;
        failures[tail] = failedAttempts;
        size++;
    }

    double peekArrival() {
        return arrivalMs[head];
    }

    /** Removes the oldest message and returns its original arrival instant. */
    double poll() {
        double arrival = arrivalMs[head];
        lastFailures = failures[head];
        head = (head + 1) % arrivalMs.length;
        size--;
        return arrival;
    }

    int lastFailures() {
        return lastFailures;
    }

    double sumWaitUntil(double nowMs) {
        double sum = 0;
        for (int i = 0; i < size; i++) {
            sum += nowMs - arrivalMs[(head + i) % arrivalMs.length];
        }
        return sum;
    }

    private void grow() {
        double[] newArrival = new double[arrivalMs.length * 2];
        int[] newFailures = new int[failures.length * 2];
        for (int i = 0; i < size; i++) {
            int from = (head + i) % arrivalMs.length;
            newArrival[i] = arrivalMs[from];
            newFailures[i] = failures[from];
        }
        arrivalMs = newArrival;
        failures = newFailures;
        head = 0;
    }

    /** Test/diagnostic helper. */
    @Override
    public String toString() {
        return "MessageQueue" + Arrays.toString(Arrays.copyOfRange(arrivalMs, 0, Math.min(size, 5)));
    }
}
