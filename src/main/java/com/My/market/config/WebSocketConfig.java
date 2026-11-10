package com.My.market.config;

import com.My.market.handler.ChatWebSocketHandler;
import com.My.market.handler.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;


@Configuration
@RequiredArgsConstructor
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final NotificationWebSocketHandler notificationWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // setAllowedOrigins("*") 를 쓰지 않는다: 기본값(같은 출처만 허용)이어야
        // 다른 사이트의 페이지가 로그인된 사용자의 쿠키로 이 소켓에 접속하는 것을 막을 수 있다.
        registry.addHandler(chatWebSocketHandler, "/ws/chat");
        registry.addHandler(notificationWebSocketHandler, "/ws/notify");
    }
}
