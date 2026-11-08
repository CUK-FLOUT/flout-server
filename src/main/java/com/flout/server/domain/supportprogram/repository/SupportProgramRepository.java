package com.flout.server.domain.supportprogram.repository;

import com.flout.server.domain.supportprogram.entity.SupportProgram;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportProgramRepository extends JpaRepository<SupportProgram, Long> {
  List<SupportProgram> findAllByOrderByIdAsc();
}
