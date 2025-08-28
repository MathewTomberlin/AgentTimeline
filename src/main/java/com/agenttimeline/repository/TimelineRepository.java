package com.agenttimeline.repository;

import com.agenttimeline.model.TimelineMessage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimelineRepository extends CrudRepository<TimelineMessage, String> {
    List<TimelineMessage> findBySessionIdOrderByTimestampDesc(String sessionId);
    List<TimelineMessage> findAllByOrderByTimestampDesc();
}
