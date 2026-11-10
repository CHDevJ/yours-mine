package com.My.market.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** 대화 기록 한 페이지 */
@Getter
@AllArgsConstructor
public class ChatHistoryDTO {

    private List<ChatMessageDTO> messages; // 오래된 것 -> 최신 순서
    private boolean hasMore;               // 이 페이지보다 더 오래된 대화가 남아 있는지
}
