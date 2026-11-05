package com.flout.server.domain.inspectionapplication.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description =
        "거주층: SEMI_BASEMENT=반지하/지하, FIRST_FLOOR=1층, SECOND_FLOOR_OR_ABOVE=2층 이상. 미입력은 null")
public enum HousingType {
  SEMI_BASEMENT,
  FIRST_FLOOR,
  SECOND_FLOOR_OR_ABOVE;

  @JsonCreator
  public static HousingType fromValue(String value) {
    return HousingType.valueOf(value);
  }
}
