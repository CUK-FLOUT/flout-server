package com.flout.server.domain.construction;

import static org.mockito.Mockito.*;

import com.flout.server.domain.construction.repository.ConstructionRepository;
import com.flout.server.domain.construction.service.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ConstructionAutoStartSchedulerTest {
  @Test
  void continuesAfterIndividualFailureAndRetriesOnNextRun() {
    var repository = mock(ConstructionRepository.class);
    var service = mock(ConstructionStatusService.class);
    var clock = Clock.fixed(Instant.parse("2030-01-02T00:30:00Z"), ZoneId.of("Asia/Seoul"));
    var now = LocalDateTime.of(2030, 1, 2, 9, 30);
    when(repository.findDueConstructionIds(now, PageRequest.of(0, 100)))
        .thenReturn(List.of(1L, 2L));
    when(service.startIfDue(1L))
        .thenThrow(new IllegalStateException("temporary failure"))
        .thenReturn(true);
    var scheduler = new ConstructionAutoStartScheduler(repository, service, clock);
    scheduler.startDueConstructions();
    verify(service).startIfDue(2L);
    scheduler.startDueConstructions();
    verify(service, times(2)).startIfDue(1L);
    verify(service, times(2)).startIfDue(2L);
    verify(repository, times(2)).findDueConstructionIds(now, PageRequest.of(0, 100));
  }
}
