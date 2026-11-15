package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.supportapplication.client.GeminiSupportClient;
import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse;
import com.flout.server.domain.supportapplication.response.code.SupportApplicationErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupportProgramMatchingService {
  private final SupportProgramMatchingSnapshotLoader snapshots;
  private final GeminiSupportClient gemini;
  private final SupportProgramMatchingDecisionPolicy decisionPolicy;

  /** DB 스냅샷을 먼저 확정하고 트랜잭션 밖에서 Gemini를 호출한 뒤 실제 DB 사업만 반환한다. */
  public SupportProgramMatchingResponse match(Long userId, Long applicationId) {
    if (applicationId == null || applicationId <= 0) {
      throw new BaseException(GeneralErrorCode.INVALID_HTTP_MESSAGE_PARAMETER);
    }

    var snapshot = snapshots.load(userId, applicationId);
    if (snapshot.candidates().isEmpty()) {
      throw new BaseException(SupportApplicationErrorCode.MATCHING_INPUT_INVALID);
    }

    var decision = gemini.decide(snapshot.input());
    return decisionPolicy.resolve(applicationId, snapshot, decision);
  }
}
