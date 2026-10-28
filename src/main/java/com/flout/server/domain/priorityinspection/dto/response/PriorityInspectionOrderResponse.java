package com.flout.server.domain.priorityinspection.dto.response;

import com.flout.server.domain.priorityinspection.entity.PriorityInspectionDesignation;
import java.util.List;
import java.util.Locale;

public record PriorityInspectionOrderResponse(List<Item> applications) {
  public record Item(Long applicationId, String receiptNo, int selectionOrder) {
    public static Item from(PriorityInspectionDesignation designation) {
      return new Item(
          designation.getApplicationId(),
          String.format(Locale.ROOT, "%06d", designation.getApplicationId()),
          designation.getSelectionOrder());
    }
  }
}
