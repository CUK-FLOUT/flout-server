package com.flout.server.domain.inspectionapplication.service;

public interface AttachmentStorage {
  String readUrl(String key);

  void upload(String key, byte[] bytes, String contentType);

  void delete(String key);
}
