package com.My.market.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WebSocket 으로 주고받는 메시지.
 * 클라이언트 -> 서버: type, roomId, msg 만 사용한다. sender 는 보내도 무시하고 서버가 로그인 정보로 채운다.
 * 서버 -> 클라이언트: type, roomId, sender, msg, sentAt
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    public enum MessageType {
        ENTER, TALK, QUIT
    }

    private Long id; // 저장된 메시지 번호 (입장/퇴장 알림에는 없음)
    private MessageType type;
    private Long roomId;
    private String sender;
    private String msg;
    private String sentAt;
}
