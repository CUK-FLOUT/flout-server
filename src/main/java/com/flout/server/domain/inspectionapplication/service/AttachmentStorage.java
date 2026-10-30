package com.flout.server.domain.inspectionapplication.service;

public interface AttachmentStorage {
  void upload(String key, byte[] bytes, String contentType);

  void delete(String key);
}
