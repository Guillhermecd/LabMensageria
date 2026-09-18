package com.bimd.msgsim.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "simulation_ticks")
@Getter
@Setter
@NoArgsConstructor
public class SimulationTick {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "run_id")
    private SimulationRun run;

    @Column(nullable = false)
    private int second;

    @Column(nullable = false)
    private int produced;

    @Column(nullable = false)
    private int consumed;

    @Column(nullable = false)
    private int failed;

    @Column(nullable = false)
    private int dropped;

    @Column(nullable = false)
    private int backlog;

    @Column(nullable = false)
    private double utilization;

    @Column(name = "p50_ms", nullable = false)
    private int p50Ms;

    @Column(name = "p95_ms", nullable = false)
    private int p95Ms;

    @Column(name = "p99_ms", nullable = false)
    private int p99Ms;

    @Column(nullable = false)
    private double capacity;
}
