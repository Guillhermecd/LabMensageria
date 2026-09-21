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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scenarios")
@Getter
@Setter
@NoArgsConstructor
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BrokerType broker;

    @Column(name = "rate_per_second", nullable = false)
    private Integer ratePerSecond;

    @Column(nullable = false)
    private Integer consumers;

    @Column(name = "processing_ms", nullable = false)
    private Integer processingMs;

    @Column(name = "failure_pct", nullable = false)
    private BigDecimal failurePct;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries;

    @Column(name = "message_size_kb", nullable = false)
    private Integer messageSizeKb;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "queue_capacity")
    private Integer queueCapacity;

    @Column(name = "partitions")
    private Integer partitions;

    @Column(name = "visibility_timeout_seconds")
    private Integer visibilityTimeoutSeconds;

    @Column(name = "retention_hours")
    private Integer retentionHours;

    @Column(name = "retention_mb")
    private Integer retentionMb;

    @Column(name = "high_watermark_mb")
    private Integer highWatermarkMb;

    @Column(name = "prefetch")
    private Integer prefetch;

    @Column(name = "inflight_max")
    private Integer inflightMax;

    @Column(name = "dlq_enabled", nullable = false)
    private boolean dlqEnabled = true;

    @Column(name = "burst_enabled", nullable = false)
    private boolean burstEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false)
    private ExecutionMode executionMode = ExecutionMode.SIMULATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_profile", nullable = false)
    private ServiceProfile serviceProfile = ServiceProfile.EXPONENTIAL;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
