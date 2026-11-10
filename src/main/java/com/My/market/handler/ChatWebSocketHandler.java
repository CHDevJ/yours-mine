package com.My.market.handler;

import com.My.market.DTO.ChatMessageDTO;
import com.My.market.DTO.ChatMessageDTO.MessageType;
import com.My.market.service.ChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채팅 WebSocket 핸들러.
 * - 사용자 식별: 서버가 로그인 정보(session.getPrincipal)에서 꺼낸다. 클라이언트가 보낸 sender 는 믿지 않는다.
 * - 입장(ENTER) 시 그 방의 참여자(구매자/판매자)인지 검증한다.
 * - 입장하지 않은 방으로는 메시지를 보낼 수 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String ROOM_ID_ATTR = "roomId";
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final ChatService chatService;

    // sessionId -> 동시 전송에 안전하게 감싼 세션
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // roomId -> 그 방에 입장해 있는 sessionId 들
    private final Map<Long, Set<String>> roomSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getPrincipal() == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        // 같은 세션에 여러 스레드가 동시에 sendMessage 하면 예외가 나므로 데코레이터로 감싼다
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Principal principal = session.getPrincipal();
        if (principal == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String username = principal.getName();

        ChatMessageDTO dto;
        try {
            dto = objectMapper.readValue(message.getPayload(), ChatMessageDTO.class);
        } catch (JsonProcessingException e) {
            log.warn("잘못된 메시지 형식. user={}", username);
            return;
        }
        if (dto.getType() == null || dto.getRoomId() == null) {
            return;
        }

        switch (dto.getType()) {
            case ENTER -> enter(session, username, dto.getRoomId());
            case TALK -> talk(session, username, dto);
            case QUIT -> quit(session, username);
        }
    }

    private void enter(WebSocketSession session, String username, Long roomId) throws IOException {
        if (!chatService.isParticipant(roomId, username)) {
            log.warn("채팅방 접근 거부. roomId={}, user={}", roomId, username);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        leaveCurrentRoom(session); // 이미 다른 방에 있었다면 먼저 나간다
        roomSessions.computeIfAbsent(roomId, id -> ConcurrentHashMap.newKeySet()).add(session.getId());
        session.getAttributes().put(ROOM_ID_ATTR, roomId);
        broadcast(roomId, notice(MessageType.ENTER, roomId, username + "님이 입장했습니다."));
    }

    private void talk(WebSocketSession session, String username, ChatMessageDTO dto) {
        Long joinedRoomId = currentRoomId(session);
        if (joinedRoomId == null || !joinedRoomId.equals(dto.getRoomId())) {
            log.warn("입장하지 않은 방으로 전송 시도. user={}, roomId={}", username, dto.getRoomId());
            return;
        }
        ChatMessageDTO saved;
        try {
            saved = chatService.saveMessage(joinedRoomId, username, dto.getMsg());
        } catch (IllegalArgumentException | AccessDeniedException e) {
            log.warn("메시지 저장 거부. user={}, reason={}", username, e.getMessage());
            return;
        }
        broadcast(joinedRoomId, saved);
    }

    private void quit(WebSocketSession session, String username) {
        Long roomId = currentRoomId(session);
        if (roomId == null) {
            return;
        }
        leaveCurrentRoom(session);
        broadcast(roomId, notice(MessageType.QUIT, roomId, username + "님이 퇴장했습니다."));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // QUIT 없이 탭을 닫거나 네트워크가 끊겨도 호출된다. 여기서 정리하지 않으면 닫힌 세션이 방에 남는다.
        sessions.remove(session.getId());
        leaveCurrentRoom(session);
    }

    private Long currentRoomId(WebSocketSession session) {
        return (Long) session.getAttributes().get(ROOM_ID_ATTR);
    }

    private void leaveCurrentRoom(WebSocketSession session) {
        Long roomId = (Long) session.getAttributes().remove(ROOM_ID_ATTR);
        if (roomId == null) {
            return;
        }
        Set<String> ids = roomSessions.get(roomId);
        if (ids != null) {
            ids.remove(session.getId()); // 빈 Set 은 지우지 않는다 (동시 입장과의 경쟁 조건 방지)
        }
    }

    private ChatMessageDTO notice(MessageType type, Long roomId, String text) {
        return new ChatMessageDTO(type, roomId, null, text, null);
    }

    // 한 명의 전송 실패가 다른 사람에게 영향을 주지 않도록 각각 try/catch 한다
    private void broadcast(Long roomId, ChatMessageDTO dto) {
        Set<String> ids = roomSessions.get(roomId);
        if (ids == null) {
            return;
        }
        TextMessage text;
        try {
            text = new TextMessage(objectMapper.writeValueAsString(dto));
        } catch (JsonProcessingException e) {
            log.error("메시지 직렬화 실패. roomId={}", roomId, e);
            return;
        }
        for (String id : ids) {
            WebSocketSession target = sessions.get(id);
            if (target == null || !target.isOpen()) {
                continue;
            }
            try {
                target.sendMessage(text);
            } catch (Exception e) {
                log.warn("메시지 전송 실패. sessionId={}", id, e);
            }
        }
    }
}
