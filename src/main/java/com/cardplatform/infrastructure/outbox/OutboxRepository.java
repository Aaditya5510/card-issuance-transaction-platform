package com.cardplatform.infrastructure.outbox;

import java.util.List;

public interface OutboxRepository {

    void save(OutboxEvent outboxEvent);

    List<OutboxEvent> findPendingEvents(int limit);
}
