package com.flout.server.domain.inspection.repository;

import com.flout.server.domain.inspection.entity.InspectionResultPhoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionResultPhotoRepository
    extends JpaRepository<InspectionResultPhoto, Long> {
  // 추후 사진 조회 API에 사용 예정
  List<InspectionResultPhoto> findAllByResultIdOrderByIdAsc(Long resultId);
}
