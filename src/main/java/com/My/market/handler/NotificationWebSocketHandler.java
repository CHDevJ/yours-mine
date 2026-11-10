package com.My.market.handler;

import com.My.market.DTO.UnreadNotificationDTO;
import com.My.market.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 알림 전용 WebSocket (/ws/notify).
 * 로그인한 사용자의 모든 화면(탭)이 연결해 두고, 서버는 '그 사용자의 안 읽은 개수가 바뀔 때'
 * 현재 개수를 밀어준다. 클라이언트가 보내는 메시지는 없다.
 *
 * 한계: 연결 정보가 이 서버의 메모리에만 있으므로 서버가 여러 대가 되면 다른 서버에 붙은 사용자에게는
 * 알림이 가지 않는다. 그때는 Redis Pub/Sub 같은 브로커로 서버 간에 알림을 전달해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final String USERNAME_ATTR = "username";
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final ChatService chatService;

    // sessionId -> 동시 전송에 안전하게 감싼 세션
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // username -> 그 사용자가 열어둔 알림 소켓들 (탭이 여러 개일 수 있다)
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (session.getPrincipal() == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String username = session.getPrincipal().getName();
        session.getAttributes().put(USERNAME_ATTR, username);
        sessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT));
        userSessions.computeIfAbsent(username, name -> ConcurrentHashMap.newKeySet()).add(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        Object username = session.getAttributes().get(USERNAME_ATTR);
        if (username != null) {
            Set<String> ids = userSessions.get((String) username);
            if (ids != null) {
                ids.remove(session.getId()); // 빈 Set 은 지우지 않는다 (동시 접속과의 경쟁 조건 방지)
            }
        }
    }

    /**
     * username 의 안 읽은 개수가 바뀌었음을 그 사용자의 모든 알림 소켓에 알린다.
     * 열어둔 화면이 없으면 DB 조회도 하지 않는다. 알림 실패가 호출한 쪽(채팅 전송 등)에 영향을 주지 않도록
     * 예외는 여기서 삼킨다.
     */
    public void notifyUnread(String username, Long roomId) {
        try {
            Set<String> ids = userSessions.get(username);
            if (ids == null || ids.isEmpty()) {
                return;
            }
            Map<Long, Long> unread = chatService.unreadByRoom(username);
            long total = unread.values().stream().mapToLong(Long::longValue).sum();
            UnreadNotificationDTO dto = new UnreadNotificationDTO(roomId, unread.getOrDefault(roomId, 0L), total);
            TextMessage text = new TextMessage(objectMapper.writeValueAsString(dto));

            for (String id : ids) {
                WebSocketSession target = sessions.get(id);
                if (target != null && target.isOpen()) {
                    target.sendMessage(text);
                }
            }
        } catch (Exception e) {
            log.warn("안 읽은 메시지 알림 실패. user={}, roomId={}", username, roomId, e);
        }
    }
}
