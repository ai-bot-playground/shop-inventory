package com.shop.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String type;

    @Column(name = "msg_key", nullable = false)
    private String msgKey;

    @Column(nullable = false)
    private String payload;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent create(String eventId, String type, String msgKey, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.eventId = eventId;
        e.type = type;
        e.msgKey = msgKey;
        e.payload = payload;
        return e;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getMsgKey() {
        return msgKey;
    }

    public String getPayload() {
        return payload;
    }

    public void markPublished() {
        this.publishedAt = OffsetDateTime.now();
    }
}
