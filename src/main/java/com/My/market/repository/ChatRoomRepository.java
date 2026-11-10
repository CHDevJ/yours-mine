package com.My.market.repository;

import com.My.market.model.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 방별 '안 읽은 메시지' 개수: 내가 보낸 게 아니고, 내가 마지막으로 읽은 번호보다 큰 메시지.
    // 결과 행: [roomId, count]. 안 읽은 메시지가 없는 방은 행 자체가 없다.
    @Query("select r.id, count(m) from ChatMessage m join m.room r "
            + "where (r.buyer.username = :username or r.board.user.username = :username) "
            + "and m.sender.username <> :username "
            + "and m.id > (case when r.buyer.username = :username "
            + "then coalesce(r.buyerLastReadId, 0L) else coalesce(r.sellerLastReadId, 0L) end) "
            + "group by r.id")
    List<Object[]> countUnreadByRoom(@Param("username") String username);

    // 읽은 번호는 앞으로만 간다. 조건을 UPDATE 문 안에 넣어서, 동시에 두 요청이 와도 번호가 뒤로 가지 않는다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ChatRoom r set r.buyerLastReadId = :messageId "
            + "where r.id = :roomId and (r.buyerLastReadId is null or r.buyerLastReadId < :messageId)")
    int updateBuyerLastRead(@Param("roomId") Long roomId, @Param("messageId") Long messageId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ChatRoom r set r.sellerLastReadId = :messageId "
            + "where r.id = :roomId and (r.sellerLastReadId is null or r.sellerLastReadId < :messageId)")
    int updateSellerLastRead(@Param("roomId") Long roomId, @Param("messageId") Long messageId);
}
