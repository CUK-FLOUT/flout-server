package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.dto.request.PublishInspectionResultRequest;
import com.flout.server.domain.inspection.dto.response.PublishInspectionResultResponse;
import com.flout.server.domain.inspection.entity.*;
import com.flout.server.domain.inspection.repository.*;
import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.domain.user.entity.UserRole;
import com.flout.server.domain.user.repository.UserRepository;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublishInspectionResultService {

    private static final Pattern BUCHEON_ADDRESS =
            Pattern.compile("^(?:(?:경기도|경기)\\s+)?부천시(?:\\s|$)");

    private final UserRepository users;
    private final InspectionRepository inspections;
    private final InspectionResultRepository results;
    private final InspectionVulnerabilityRepository vulnerabilities;
    private final PreventionRecommendationRepository recommendations;
    private final InspectionStatusHistoryRepository histories;
    private final Clock inspectionClock;

    @Transactional
    public PublishInspectionResultResponse publish(
            Long operatorId,
            Long inspectionId,
            PublishInspectionResultRequest request
    ) {
        // 점검ID 검증
        if (inspectionId == null || inspectionId <= 0) {
            throw new BaseException(
                    GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER
            );
        }

        // 운영자 인증
        var operator = users.findById(operatorId)
                .orElseThrow(() -> new BaseException(
                        GeneralErrorCode.UNAUTHORIZED_ERROR
                ));

        if (operator.getRole() != UserRole.ADMIN) {
            throw new BaseException(
                    GeneralErrorCode.ACCESS_DENIED_REQUEST
            );
        }

        // 점검 조회(동시 수정 방지)
        Inspection inspection = inspections.findForStatusUpdate(inspectionId)
                .orElseThrow(() -> new BaseException(
                        InspectionErrorCode.INSPECTION_NOT_FOUND
                ));

        // 관할 지역 확인
        if (operator.getJurisdiction() == null
                || !"부천시".equals(operator.getJurisdiction().getName())
                || !BUCHEON_ADDRESS.matcher(
                inspection.getApplication().getAddress().strip()
        ).find()) {
            throw new BaseException(
                    InspectionErrorCode.JURISDICTION_ACCESS_DENIED
            );
        }

        // 진행 중인 점검만 공개 가능
        if (inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
            throw new BaseException(
                    InspectionErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        // 이미 공개된 결과가 있는지 확인
        if (results.findByInspectionIdAndResultStatus(
                inspectionId, ResultStatus.PUBLISHED
        ).isPresent()) {
            throw new BaseException(
                    InspectionErrorCode.INVALID_STATUS_TRANSITION
            );
        }

        // 기존 임시저장 결과 조회 또는 새 결과 생성, 저장
        InspectionResult result = results
                .findByInspectionIdAndResultStatus(
                        inspectionId, ResultStatus.DRAFT
                )
                .orElseGet(() -> InspectionResult.draft(inspection));

        result.updateContent(
                request.summary(),
                request.followUpType().name(),
                request.internalMemo()
        );

        result = results.saveAndFlush(result);

        // 기존 임시저장 취약지점 및 예방조치 교체
        vulnerabilities.deleteByResultId(result.getId());
        recommendations.deleteByResultId(result.getId());

        // 취약지점 저장
        for (int i = 0; i < request.vulnerabilities().size(); i++) {
            var item = request.vulnerabilities().get(i);

            vulnerabilities.save(InspectionVulnerability.create(
                    result,
                    item.type().name(),
                    item.title(),
                    item.expertOpinion(),
                    item.fieldFindings(),
                    i
            ));
        }

        // 권장 예방조치 저장
        for (int i = 0; i < request.recommendedActions().size(); i++) {
            var item = request.recommendedActions().get(i);

            recommendations.save(PreventionRecommendation.create(
                    result,
                    item.title(),
                    item.relatedTarget().name(),
                    item.description(),
                    i
            ));
        }

        LocalDateTime now = LocalDateTime.now(inspectionClock)
                .truncatedTo(ChronoUnit.SECONDS);
        result.publish(now);

        InspectionStatus previous = inspection.getStatus();
        inspection.changeStatus(InspectionStatus.COMPLETED, now);
        inspection.getApplication().completeInspection();

        histories.saveAndFlush(InspectionStatusHistory.changed(
                inspection,
                previous,
                operator,
                now
        ));

        return new PublishInspectionResultResponse(
                inspection.getId(),
                result.getResultStatus().name(),
                inspection.getStatus().name(),
                inspection.getApplication().getStatus().name(),
                OffsetDateTime.of(now, ZoneOffset.ofHours(9)),
                "NOT_APPLIED"
        );
    }
}
