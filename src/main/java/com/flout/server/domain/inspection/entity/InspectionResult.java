package com.flout.server.domain.inspection.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "inspection_results",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"inspection_id", "result_status"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "follow_up_type", length = 30)
    private String followUpType;

    @Column(name = "internal_memo", columnDefinition = "TEXT")
    private String internalMemo;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false)
    private ResultStatus resultStatus;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
