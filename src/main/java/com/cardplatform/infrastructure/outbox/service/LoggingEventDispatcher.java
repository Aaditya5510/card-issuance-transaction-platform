package com.cardplatform.infrastructure.outbox.service;

import com.cardplatform.infrastructure.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default mock / logging event dispatcher that logs event dispatching to message channels.
 */
@Component
@ConditionalOnMissingBean(value = EventDispatcher.class, ignored = LoggingEventDispatcher.class)
public class LoggingEventDispatcher implements EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventDispatcher.class);

    @Override
    public void dispatch(OutboxEvent event) {
        log.info("Dispatched outbox event to message channel: id={}, aggregateType={}, aggregateId={}, eventType={}, payload={}",
                event.id(), event.aggregateType(), event.aggregateId(), event.eventType(), event.payload());
    }
}
