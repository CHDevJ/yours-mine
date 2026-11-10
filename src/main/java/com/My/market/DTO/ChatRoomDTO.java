package com.My.market.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 화면에 보여줄 채팅방 정보 (보는 사람 기준) */
@Getter
@AllArgsConstructor
public class ChatRoomDTO {

    private Long roomId;
    private Long boardId;
    private String boardTitle;
    private String otherName; // 상대방 이름
    private boolean seller;   // 내가 판매자인지
    private long unreadCount; // 내가 아직 안 읽은 메시지 개수
}
