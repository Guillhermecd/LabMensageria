package com.bimd.msgsim.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "simulation_runs")
@Getter
@Setter
@NoArgsConstructor
public class SimulationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scenario_id")
    private Scenario scenario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunMode mode;

    @Column(nullable = false)
    private long seed;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "produced_total", nullable = false)
    private long producedTotal;

    @Column(name = "delivered_total", nullable = false)
    private long deliveredTotal;

    @Column(name = "dlq_total", nullable = false)
    private long dlqTotal;

    @Column(name = "dropped_total", nullable = false)
    private long droppedTotal;

    @Column(name = "retries_total", nullable = false)
    private long retriesTotal;
}
