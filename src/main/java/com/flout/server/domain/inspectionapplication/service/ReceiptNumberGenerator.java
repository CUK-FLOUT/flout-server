package com.flout.server.domain.inspectionapplication.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReceiptNumberGenerator {
  public String next() {
    return "IA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 17).toUpperCase();
  }
}
