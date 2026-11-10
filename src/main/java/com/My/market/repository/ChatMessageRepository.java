package com.My.market.repository;

import com.My.market.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("select m from ChatMessage m join fetch m.sender where m.room.id = :roomId order by m.id asc")
    List<ChatMessage> findHistory(@Param("roomId") Long roomId);
}
