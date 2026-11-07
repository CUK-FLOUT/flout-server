package com.flout.server.domain.preventionguide.service;

import com.flout.server.domain.preventionguide.client.GeneratedGuide;
import com.flout.server.domain.preventionguide.dto.response.PreventionGuideItemResponse;
import com.flout.server.domain.preventionguide.dto.response.PreventionGuideResponse;
import com.flout.server.domain.preventionguide.entity.PreventionGuide;
import com.flout.server.domain.preventionguide.entity.PreventionGuideItem;
import com.flout.server.domain.preventionguide.repository.PreventionGuideItemRepository;
import com.flout.server.domain.preventionguide.response.code.PreventionGuideErrorCode;
import com.flout.server.domain.riskassessment.entity.RiskLevel;
import com.flout.server.global.exception.BaseException;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PreventionGuideResponseMapper {
  private static final String GUIDE_TITLE = "이것부터 확인하세요";
  private final PreventionGuideItemRepository itemsRepository;
  private final ObjectMapper objectMapper;

  public String serialize(GeneratedGuide guide) {
    try {
      return objectMapper.writeValueAsString(guide);
    } catch (Exception e) {
      throw new BaseException(PreventionGuideErrorCode.GUIDE_SAVE_FAILED);
    }
  }

  public GeneratedGuide readContent(PreventionGuide guide) {
    try {
      return objectMapper.readValue(guide.getContentJson(), GeneratedGuide.class);
    } catch (Exception e) {
      throw new BaseException(PreventionGuideErrorCode.GUIDE_SAVE_FAILED);
    }
  }

  public PreventionGuideResponse response(
      PreventionGuide guide, GeneratedGuide generated, Long assessmentId, RiskLevel riskLevel) {
    var persisted = itemsRepository.findByGuideIdOrderByItemOrderAsc(guide.getId());
    if (persisted.isEmpty()) {
      for (int index = 0; index < generated.items().size(); index++)
        itemsRepository.save(PreventionGuideItem.create(guide, index + 1));
      itemsRepository.flush();
      persisted = itemsRepository.findByGuideIdOrderByItemOrderAsc(guide.getId());
    }
    if (persisted.size() != generated.items().size())
      throw new BaseException(PreventionGuideErrorCode.GUIDE_SAVE_FAILED);
    var storedItems = persisted;
    var items =
        java.util.stream.IntStream.range(0, generated.items().size())
            .mapToObj(
                index -> {
                  var item = generated.items().get(index);
                  var state = storedItems.get(index);
                  return new PreventionGuideItemResponse(
                      state.getId(),
                      index + 1,
                      item.ruleId(),
                      item.title(),
                      item.description(),
                      state.isCompleted());
                })
            .toList();
    return new PreventionGuideResponse(
        guide.getId(),
        assessmentId,
        generated.source(),
        riskLevel,
        GUIDE_TITLE,
        generated.subtitle(),
        (int) storedItems.stream().filter(PreventionGuideItem::isCompleted).count(),
        items.size(),
        items,
        guide.getResolution(),
        guide
            .getGeneratedAt()
            .atZone(ZoneId.systemDefault())
            .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toOffsetDateTime());
  }
}
