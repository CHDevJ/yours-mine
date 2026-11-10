package com.My.market.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 알림 소켓(/ws/notify)으로 내려가는 '안 읽은 메시지 개수' 알림 */
@Getter
@AllArgsConstructor
public class UnreadNotificationDTO {

    private Long roomId;           // 어느 방의 변화인지
    private long roomUnreadCount;  // 그 방의 안 읽은 개수
    private long totalUnreadCount; // 내 전체 안 읽은 개수 (메뉴 배지용)
}
