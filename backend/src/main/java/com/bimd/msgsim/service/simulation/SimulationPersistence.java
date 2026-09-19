package com.bimd.msgsim.service.simulation;

import com.bimd.msgsim.domain.model.RunStatus;
import com.bimd.msgsim.domain.model.SimulationEvent;
import com.bimd.msgsim.domain.model.SimulationRun;
import com.bimd.msgsim.domain.model.SimulationTick;
import com.bimd.msgsim.repository.SimulationEventRepository;
import com.bimd.msgsim.repository.SimulationRunRepository;
import com.bimd.msgsim.repository.SimulationTickRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Converts engine results into entities and persists them — shared by instant and live runs. */
@Component
@RequiredArgsConstructor
public class SimulationPersistence {

    private final SimulationRunRepository runRepository;
    private final SimulationTickRepository tickRepository;
    private final SimulationEventRepository eventRepository;

    public void saveTicks(SimulationRun run, List<TickResult> results) {
        if (results.isEmpty()) return;
        tickRepository.saveAll(results.stream().map(r -> toEntity(run, r)).toList());
    }

    public void saveEvents(SimulationRun run, List<EventResult> results) {
        if (results.isEmpty()) return;
        eventRepository.saveAll(results.stream().map(r -> toEntity(run, r)).toList());
    }

    public void complete(SimulationRun run, RunStatus status, SimulationState state) {
        complete(run, status, state.getProduced(), state.getOk(), state.getDlq(), state.getDropped(), state.getRetries());
    }

    /** Same finalization, for callers that don't have a {@link SimulationState} — e.g. a real-broker
     * run, whose totals come from measured counters, not the simulated engine's internal state. */
    public void complete(
            SimulationRun run, RunStatus status,
            long producedTotal, long deliveredTotal, long dlqTotal, long droppedTotal, long retriesTotal) {
        run.setStatus(status);
        run.setFinishedAt(Instant.now());
        run.setProducedTotal(producedTotal);
        run.setDeliveredTotal(deliveredTotal);
        run.setDlqTotal(dlqTotal);
        run.setDroppedTotal(droppedTotal);
        run.setRetriesTotal(retriesTotal);
        runRepository.save(run);
    }

    private SimulationTick toEntity(SimulationRun run, TickResult result) {
        SimulationTick tick = new SimulationTick();
        tick.setRun(run);
        tick.setSecond(result.second());
        tick.setProduced(result.produced());
        tick.setConsumed(result.consumed());
        tick.setFailed(result.failed());
        tick.setDropped(result.dropped());
        tick.setBacklog(result.backlog());
        tick.setUtilization(result.utilization());
        tick.setP50Ms(result.p50Ms());
        tick.setP95Ms(result.p95Ms());
        tick.setP99Ms(result.p99Ms());
        tick.setCapacity(result.capacity());
        return tick;
    }

    private SimulationEvent toEntity(SimulationRun run, EventResult result) {
        SimulationEvent event = new SimulationEvent();
        event.setRun(run);
        event.setSecond(result.second());
        event.setType(result.type());
        event.setMessage(result.message());
        return event;
    }
}
