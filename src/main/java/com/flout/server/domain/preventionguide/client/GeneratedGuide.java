package com.flout.server.domain.preventionguide.client;

import java.util.List;

public record GeneratedGuide(
    GuideGenerationSource source, String subtitle, List<GeneratedGuideItem> items) {}
