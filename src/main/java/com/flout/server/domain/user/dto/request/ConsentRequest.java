package com.flout.server.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ConsentRequest(
    @NotNull(message = "개인정보 제3자 제공 동의는 필수입니다.") @AssertTrue(message = "개인정보 제3자 제공 동의는 필수입니다.") @Schema(
            description = "개인정보 제3자 제공 필수 동의. true만 허용",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean thirdPartyProvisionAgreed,
    @NotNull(message = "진행상태 알림 수신 동의는 필수입니다.") @AssertTrue(message = "진행상태 알림 수신 동의는 필수입니다.") @Schema(
            description = "진행상태 알림 수신 필수 동의. true만 허용",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean statusNotificationAgreed) {}
