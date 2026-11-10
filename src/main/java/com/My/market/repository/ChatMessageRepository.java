package com.My.market.repository;

import com.My.market.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 최신 메시지부터 pageable 의 크기만큼 (최신 -> 과거 순서)
    @Query("select m from ChatMessage m join fetch m.sender where m.room.id = :roomId order by m.id desc")
    List<ChatMessage> findLatest(@Param("roomId") Long roomId, Pageable pageable);

    // beforeId 보다 오래된 메시지를 pageable 의 크기만큼 (최신 -> 과거 순서).
    // '몇 번째 페이지' 가 아니라 '이 번호보다 앞' 으로 자르는 커서 방식이라서, 그 사이 새 메시지가 와도 결과가 밀리지 않는다.
    @Query("select m from ChatMessage m join fetch m.sender where m.room.id = :roomId and m.id < :beforeId order by m.id desc")
    List<ChatMessage> findBefore(@Param("roomId") Long roomId, @Param("beforeId") Long beforeId, Pageable pageable);
}
