package com.My.market.repository;

import com.My.market.model.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByBoardIdAndBuyerId(Long boardId, Long buyerId);

    // 참여자(구매자, 판매자) 확인에 필요한 연관 엔티티를 한 번에 가져온다
    @Query("select r from ChatRoom r join fetch r.board b join fetch b.user seller join fetch r.buyer buyer where r.id = :id")
    Optional<ChatRoom> findWithParticipants(@Param("id") Long id);

    // 내가 구매자이거나 판매자인 방
    @Query("select r from ChatRoom r join fetch r.board b join fetch b.user seller join fetch r.buyer buyer "
            + "where buyer.username = :username or seller.username = :username order by r.id desc")
    List<ChatRoom> findMyRooms(@Param("username") String username);
}
