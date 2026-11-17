# API 경로 카탈로그

> 본 목록은 아래 기준 커밋의 Java Controller 매핑 어노테이션을 정적으로 추출한 스냅샷입니다. 이후 변경이 반영되지 않았을 수 있습니다. 실제 노출 여부·경로·권한·응답 계약은 현재 Controller/DTO/테스트와 실행 중 OpenAPI를 확인하세요.

- 기준 커밋: `b4c05b5`
- Swagger/OpenAPI: 서버 설정에 따른 `/swagger-ui/index.html`, `/v3/api-docs`

| Method | Endpoint | Controller |
|---|---|---|
| `GET` | `/api/admin/applications` | [`AdminApplicationListController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/AdminApplicationListController.java) |
| `GET` | `/api/admin/applications/stats` | [`AdminApplicationListController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/AdminApplicationListController.java) |
| `GET` | `/api/admin/applications/{applicationId}` | [`AdminApplicationDetailController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/AdminApplicationDetailController.java) |
| `GET` | `/api/admin/applications/{applicationId}/attachments` | [`AdminApplicationAttachmentController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/AdminApplicationAttachmentController.java) |
| `GET` | `/api/admin/applications/{applicationId}/construction` | [`AdminApplicationConstructionController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/AdminApplicationConstructionController.java) |
| `GET` | `/api/admin/applications/{applicationId}/histories` | [`AdminApplicationHistoriesController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/AdminApplicationHistoriesController.java) |
| `PATCH` | `/api/admin/applications/{applicationId}/internal-memo` | [`AdminApplicationMemoController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/AdminApplicationMemoController.java) |
| `GET` | `/api/admin/applications/{applicationId}/support` | [`AdminApplicationSupportController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/AdminApplicationSupportController.java) |
| `GET` | `/api/admin/constructions` | [`AdminConstructionListController.java`](../src/main/java/com/flout/server/domain/construction/controller/AdminConstructionListController.java) |
| `POST` | `/api/admin/constructions` | [`AdminConstructionCompletionController.java`](../src/main/java/com/flout/server/domain/construction/controller/AdminConstructionCompletionController.java) |
| `GET` | `/api/admin/constructions/{constructionId}` | [`AdminConstructionDetailController.java`](../src/main/java/com/flout/server/domain/construction/controller/AdminConstructionDetailController.java) |
| `POST` | `/api/admin/constructions/{constructionId}/photos` | [`AdminConstructionPhotoController.java`](../src/main/java/com/flout/server/domain/construction/controller/AdminConstructionPhotoController.java) |
| `POST` | `/api/admin/constructions/{constructionId}/schedule` | [`AdminConstructionScheduleController.java`](../src/main/java/com/flout/server/domain/construction/controller/AdminConstructionScheduleController.java) |
| `GET` | `/api/admin/inspections` | [`AdminInspectionListController.java`](../src/main/java/com/flout/server/domain/inspection/controller/AdminInspectionListController.java) |
| `POST` | `/api/admin/inspections/recommended-facilities/validate` | [`AdminRecommendedFacilityValidationController.java`](../src/main/java/com/flout/server/domain/inspection/controller/AdminRecommendedFacilityValidationController.java) |
| `GET` | `/api/admin/inspections/{inspectionId}` | [`AdminInspectionDetailController.java`](../src/main/java/com/flout/server/domain/inspection/controller/AdminInspectionDetailController.java) |
| `POST` | `/api/admin/inspections/{inspectionId}/assignment` | [`AdminInspectionAssignmentController.java`](../src/main/java/com/flout/server/domain/inspection/controller/AdminInspectionAssignmentController.java) |
| `POST` | `/api/admin/inspections/{inspectionId}/attachments` | [`AdminInspectionPhotoController.java`](../src/main/java/com/flout/server/domain/inspection/controller/AdminInspectionPhotoController.java) |
| `DELETE` | `/api/admin/inspections/{inspectionId}/attachments/{photoId}` | [`AdminInspectionPhotoDeleteController.java`](../src/main/java/com/flout/server/domain/inspection/controller/AdminInspectionPhotoDeleteController.java) |
| `GET` | `/api/admin/inspections/{inspectionId}/result` | [`AdminInspectionResultController.java`](../src/main/java/com/flout/server/domain/inspection/controller/AdminInspectionResultController.java) |
| `POST` | `/api/admin/inspections/{inspectionId}/result` | [`AdminInspectionResultController.java`](../src/main/java/com/flout/server/domain/inspection/controller/AdminInspectionResultController.java) |
| `PUT` | `/api/admin/inspections/{inspectionId}/result/draft` | [`AdminInspectionResultController.java`](../src/main/java/com/flout/server/domain/inspection/controller/AdminInspectionResultController.java) |
| `GET` | `/api/admin/map/applications` | [`AdminApplicationMapController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/AdminApplicationMapController.java) |
| `GET` | `/api/admin/map/public-issues` | [`AdminPublicIssueMapController.java`](../src/main/java/com/flout/server/domain/publicissue/controller/AdminPublicIssueMapController.java) |
| `GET` | `/api/admin/me` | [`AdminAccountController.java`](../src/main/java/com/flout/server/domain/user/controller/AdminAccountController.java) |
| `GET` | `/api/admin/priority-inspections` | [`AdminPriorityInspectionController.java`](../src/main/java/com/flout/server/domain/priorityinspection/controller/AdminPriorityInspectionController.java) |
| `PUT` | `/api/admin/priority-inspections/designation` | [`AdminPriorityInspectionController.java`](../src/main/java/com/flout/server/domain/priorityinspection/controller/AdminPriorityInspectionController.java) |
| `GET` | `/api/admin/priority-inspections/order` | [`AdminPriorityInspectionController.java`](../src/main/java/com/flout/server/domain/priorityinspection/controller/AdminPriorityInspectionController.java) |
| `PUT` | `/api/admin/priority-inspections/order` | [`AdminPriorityInspectionController.java`](../src/main/java/com/flout/server/domain/priorityinspection/controller/AdminPriorityInspectionController.java) |
| `GET` | `/api/admin/priority-inspections/{applicationId}` | [`AdminPriorityInspectionController.java`](../src/main/java/com/flout/server/domain/priorityinspection/controller/AdminPriorityInspectionController.java) |
| `GET` | `/api/admin/public-issues` | [`AdminPublicIssueController.java`](../src/main/java/com/flout/server/domain/publicissue/controller/AdminPublicIssueController.java) |
| `POST` | `/api/admin/public-issues` | [`AdminPublicIssueController.java`](../src/main/java/com/flout/server/domain/publicissue/controller/AdminPublicIssueController.java) |
| `GET` | `/api/admin/public-issues/status` | [`AdminPublicIssueController.java`](../src/main/java/com/flout/server/domain/publicissue/controller/AdminPublicIssueController.java) |
| `GET` | `/api/admin/public-issues/{publicIssueId}` | [`AdminPublicIssueController.java`](../src/main/java/com/flout/server/domain/publicissue/controller/AdminPublicIssueController.java) |
| `POST` | `/api/admin/public-issues/{publicIssueId}/actions` | [`AdminPublicIssueActionController.java`](../src/main/java/com/flout/server/domain/publicissue/controller/AdminPublicIssueActionController.java) |
| `GET` | `/api/admin/public-issues/{publicIssueId}/resident-applications` | [`AdminPublicIssueController.java`](../src/main/java/com/flout/server/domain/publicissue/controller/AdminPublicIssueController.java) |
| `POST` | `/api/admin/public-issues/{publicIssueId}/review` | [`AdminFacilityReviewController.java`](../src/main/java/com/flout/server/domain/publicissue/controller/AdminFacilityReviewController.java) |
| `GET` | `/api/admin/support-applications` | [`AdminSupportListController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/AdminSupportListController.java) |
| `GET` | `/api/admin/support-applications/status` | [`AdminSupportStatusController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/AdminSupportStatusController.java) |
| `GET` | `/api/admin/support-applications/{supportApplicationId}` | [`AdminSupportDetailController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/AdminSupportDetailController.java) |
| `POST` | `/api/admin/support-applications/{supportApplicationId}/approve` | [`AdminSupportApprovalController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/AdminSupportApprovalController.java) |
| `GET` | `/api/admin/support-applications/{supportApplicationId}/histories` | [`AdminSupportReviewController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/AdminSupportReviewController.java) |
| `POST` | `/api/admin/support-applications/{supportApplicationId}/reject` | [`AdminSupportRejectionController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/AdminSupportRejectionController.java) |
| `POST` | `/api/auth/admin/google` | [`GoogleLoginController.java`](../src/main/java/com/flout/server/domain/user/controller/GoogleLoginController.java) |
| `POST` | `/api/auth/admin/login` | [`LoginController.java`](../src/main/java/com/flout/server/domain/user/controller/LoginController.java) |
| `POST` | `/api/auth/google` | [`GoogleLoginController.java`](../src/main/java/com/flout/server/domain/user/controller/GoogleLoginController.java) |
| `POST` | `/api/auth/login` | [`LoginController.java`](../src/main/java/com/flout/server/domain/user/controller/LoginController.java) |
| `POST` | `/api/auth/logout` | [`TokenRefreshController.java`](../src/main/java/com/flout/server/domain/user/controller/TokenRefreshController.java) |
| `POST` | `/api/auth/refresh` | [`TokenRefreshController.java`](../src/main/java/com/flout/server/domain/user/controller/TokenRefreshController.java) |
| `POST` | `/api/auth/signup` | [`SignupController.java`](../src/main/java/com/flout/server/domain/user/controller/SignupController.java) |
| `PUT` | `/api/notifications/tokens` | [`FcmTokenController.java`](../src/main/java/com/flout/server/domain/notification/controller/FcmTokenController.java) |
| `GET` | `/api/resident/addresses/coordinates` | [`AddressController.java`](../src/main/java/com/flout/server/domain/address/controller/AddressController.java) |
| `GET` | `/api/resident/addresses/search` | [`AddressController.java`](../src/main/java/com/flout/server/domain/address/controller/AddressController.java) |
| `PUT` | `/api/resident/application-form-drafts/{draftId}` | [`ResidentFormDraftController.java`](../src/main/java/com/flout/server/domain/residentprogress/ResidentFormDraftController.java) |
| `GET` | `/api/resident/applications` | [`ResidentApplicationDetailController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/ResidentApplicationDetailController.java) |
| `POST` | `/api/resident/applications` | [`InspectionApplicationController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/InspectionApplicationController.java) |
| `GET` | `/api/resident/applications/drafts` | [`ResidentFormDraftController.java`](../src/main/java/com/flout/server/domain/residentprogress/ResidentFormDraftController.java) |
| `GET` | `/api/resident/applications/{applicationId}` | [`ResidentApplicationDetailController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/ResidentApplicationDetailController.java) |
| `PATCH` | `/api/resident/applications/{applicationId}` | [`InspectionApplicationController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/InspectionApplicationController.java) |
| `POST` | `/api/resident/applications/{applicationId}/attachments` | [`AttachmentController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/AttachmentController.java) |
| `GET` | `/api/resident/applications/{applicationId}/recommendations` | [`ResidentRecommendationsController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/ResidentRecommendationsController.java) |
| `GET` | `/api/resident/applications/{applicationId}/recommended-facilities` | [`ResidentRecommendedFacilitiesController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/ResidentRecommendedFacilitiesController.java) |
| `GET` | `/api/resident/applications/{applicationId}/result` | [`ResidentInspectionResultController.java`](../src/main/java/com/flout/server/domain/inspectionapplication/controller/ResidentInspectionResultController.java) |
| `POST` | `/api/resident/consents` | [`ConsentController.java`](../src/main/java/com/flout/server/domain/user/controller/ConsentController.java) |
| `GET` | `/api/resident/constructions/{constructionId}` | [`ResidentConstructionController.java`](../src/main/java/com/flout/server/domain/construction/controller/ResidentConstructionController.java) |
| `GET` | `/api/resident/constructions/{constructionId}/schedule` | [`ResidentConstructionController.java`](../src/main/java/com/flout/server/domain/construction/controller/ResidentConstructionController.java) |
| `PATCH` | `/api/resident/guide-items/{itemId}/progress` | [`ResidentGuideProgressController.java`](../src/main/java/com/flout/server/domain/preventionguide/controller/ResidentGuideProgressController.java) |
| `GET` | `/api/resident/notifications` | [`ResidentNotificationController.java`](../src/main/java/com/flout/server/domain/notification/controller/ResidentNotificationController.java) |
| `PATCH` | `/api/resident/notifications/read-all` | [`ResidentNotificationController.java`](../src/main/java/com/flout/server/domain/notification/controller/ResidentNotificationController.java) |
| `GET` | `/api/resident/notifications/unread-status` | [`ResidentNotificationController.java`](../src/main/java/com/flout/server/domain/notification/controller/ResidentNotificationController.java) |
| `GET` | `/api/resident/prevention-guides/{guideId}` | [`ResidentGuideProgressController.java`](../src/main/java/com/flout/server/domain/preventionguide/controller/ResidentGuideProgressController.java) |
| `PATCH` | `/api/resident/prevention-guides/{guideId}/resolution` | [`ResidentGuideProgressController.java`](../src/main/java/com/flout/server/domain/preventionguide/controller/ResidentGuideProgressController.java) |
| `GET` | `/api/resident/progress` | [`ResidentProgressController.java`](../src/main/java/com/flout/server/domain/residentprogress/ResidentProgressController.java) |
| `POST` | `/api/resident/risk-assessments` | [`ResidentRiskAssessmentController.java`](../src/main/java/com/flout/server/domain/riskassessment/controller/ResidentRiskAssessmentController.java) |
| `GET` | `/api/resident/risk-assessments/{assessmentId}` | [`ResidentProgressController.java`](../src/main/java/com/flout/server/domain/residentprogress/ResidentProgressController.java) |
| `POST` | `/api/resident/risk-assessments/{assessmentId}/guide` | [`ResidentPreventionGuideController.java`](../src/main/java/com/flout/server/domain/preventionguide/controller/ResidentPreventionGuideController.java) |
| `POST` | `/api/resident/support-application/{supportApplicationId}/matching` (경로 값: **현장점검 신청 ID**, 지원 신청 ID 아님) | [`SupportProgramMatchingController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/SupportProgramMatchingController.java) |
| `POST` | `/api/resident/support-applications` | [`SupportApplicationController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/SupportApplicationController.java) |
| `GET` | `/api/resident/support-applications/drafts` | [`ResidentFormDraftController.java`](../src/main/java/com/flout/server/domain/residentprogress/ResidentFormDraftController.java) |
| `GET` | `/api/resident/support-applications/{supportApplicationId}/approval` | [`ResidentSupportDetailController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/ResidentSupportDetailController.java) |
| `GET` | `/api/resident/support-applications/{supportApplicationId}/attachments` | [`ResidentSupportProgressController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/ResidentSupportProgressController.java) |
| `POST` | `/api/resident/support-applications/{supportApplicationId}/attachments` | [`SupportAttachmentController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/SupportAttachmentController.java) |
| `GET` | `/api/resident/support-applications/{supportApplicationId}/rejection` | [`ResidentSupportDetailController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/ResidentSupportDetailController.java) |
| `GET` | `/api/resident/support-applications/{supportApplicationId}/review` | [`ResidentSupportDetailController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/ResidentSupportDetailController.java) |
| `GET` | `/api/resident/support-applications/{supportApplicationId}/schedule` | [`ResidentSupportProgressController.java`](../src/main/java/com/flout/server/domain/supportapplication/controller/ResidentSupportProgressController.java) |
| `PUT` | `/api/resident/support-form-drafts/{draftId}` | [`ResidentFormDraftController.java`](../src/main/java/com/flout/server/domain/residentprogress/ResidentFormDraftController.java) |
| `GET` | `/dev/google-login` | [`GoogleLoginDevController.java`](../src/main/java/com/flout/server/domain/user/controller/GoogleLoginDevController.java) |

### 자동 추출의 제한

- 경로가 상수로 정의되었거나 클래스/메서드 맵핑에 배열·복잡한 표현식이 쓰인 경우 확인이 필요합니다.
- 이 목록은 정적 소스 추출이며 실행 중 등록된 Spring MVC 전체 매핑 목록을 대체하지 않습니다.
- 관리자 엔드포인트에서는 운영자 JWT 및 관할 검증이 적용되는지 별도로 확인해야 합니다.
