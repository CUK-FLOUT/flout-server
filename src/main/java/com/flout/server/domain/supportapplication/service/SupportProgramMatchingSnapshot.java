package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.supportapplication.dto.response.SupportProgramMatchingResponse.ProgramInfo;
import java.util.List;
import java.util.Map;

record SupportProgramMatchingSnapshot(
    Map<String, Object> input,
    List<SupportProgramCandidate> candidates,
    String region,
    String summary,
    List<SupportFacilityMatcher.Action> actions) {}

record SupportProgramCandidate(Long id, ProgramInfo info) {}
