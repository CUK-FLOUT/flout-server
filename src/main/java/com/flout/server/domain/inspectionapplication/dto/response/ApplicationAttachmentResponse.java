package com.flout.server.domain.inspectionapplication.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicationAttachmentResponse(
    @Schema(description = "첨부사진 ID", example = "1") Long attachmentId,
    @Schema(description = "원본 파일명", example = "출입구.jpg") String originalFilename,
    @Schema(
            description = "15분 유효 이미지 URL. 만료 시 목록 재조회",
            example =
                "https://bucket.s3.ap-northeast-2.amazonaws.com/photo.jpg?X-Amz-Signature=...")
        String imageUrl) {}
