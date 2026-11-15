package com.flout.server.domain.supportapplication.service;

import java.util.Arrays;
import java.util.Optional;

enum SupportProgramMatchType {
  DIRECT,
  COMPLEMENTARY;

  static Optional<SupportProgramMatchType> fromModel(String value) {
    return Arrays.stream(values()).filter(type -> type.name().equals(value)).findFirst();
  }
}

enum SupportProgramMatchStatus {
  MATCHED,
  NEEDS_CONFIRMATION
}

enum SupportProgramRecommendationType {
  PROGRAM
}
