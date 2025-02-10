package com.dataflow.dataflowsystem.generator.service;

import com.dataflow.dataflowsystem.generator.handler.WebSocketHandler;
import com.dataflow.model.DataRecordMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketHandlerTest {

    @Mock
    private WebSocketSession session;

    private WebSocketHandler webSocketHandler;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        webSocketHandler = new WebSocketHandler(new ObjectMapper());
        webSocketHandler.getSessions().add(session);
    }

    @Test
    void whenConnectionEstablished_thenSessionIsAdded() {
        assertEquals(1, webSocketHandler.getSessions().size());
    }

    @Test
    void whenConnectionClosed_thenSessionIsRemoved() throws Exception {
        webSocketHandler.afterConnectionClosed(session, null);
        assertEquals(0, webSocketHandler.getSessions().size());
    }

    @Test
    void whenSendBatch_thenMessagesAreSent() throws Exception {
        when(session.isOpen()).thenReturn(true);
        DataRecordMessage message = new DataRecordMessage(System.currentTimeMillis(), 42, "AB");

        webSocketHandler.sendBatch(List.of(message));

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void whenSessionClosed_thenNotSent() throws IOException {
        when(session.isOpen()).thenReturn(false);

        webSocketHandler.sendBatch(List.of(new DataRecordMessage()));

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void whenTransportError_thenSessionIsRemoved() {
        webSocketHandler.handleTransportError(session, new RuntimeException("Error"));
        assertFalse(webSocketHandler.getSessions().contains(session));
    }
}
