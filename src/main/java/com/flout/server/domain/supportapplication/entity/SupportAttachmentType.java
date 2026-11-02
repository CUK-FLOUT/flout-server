package com.flout.server.domain.supportapplication.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "증빙서류 유형: LANDLORD_CONSENT=임대인 동의서, OWNERSHIP_PROOF=주택 소유 증명서")
public enum SupportAttachmentType {
  LANDLORD_CONSENT,
  OWNERSHIP_PROOF
}
