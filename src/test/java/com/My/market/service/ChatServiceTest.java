package com.My.market.service;

import com.My.market.DTO.ChatHistoryDTO;
import com.My.market.DTO.ChatMessageDTO;
import com.My.market.DTO.ChatRoomDTO;
import com.My.market.model.Board;
import com.My.market.model.User;
import com.My.market.repository.BoardRepository;
import com.My.market.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 채팅 서비스의 핵심 규칙 검증. 각 테스트는 끝나면 롤백되어 DB 에 데이터가 남지 않는다. */
@SpringBootTest
@Transactional
class ChatServiceTest {

    @Autowired
    private ChatService chatService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BoardRepository boardRepository;

    private User seller;
    private User buyer;
    private User stranger;
    private Board board;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        seller = saveUser("seller_" + suffix);
        buyer = saveUser("buyer_" + suffix);
        stranger = saveUser("stranger_" + suffix);

        board = new Board();
        board.setTitle("테스트 상품");
        board.setContent("내용");
        board.setPrice("10000");
        board.setUser(seller);
        boardRepository.save(board);
    }

    private User saveUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("x");
        user.setEnabled(true);
        return userRepository.save(user);
    }

    @Test
    void 같은_상품_같은_구매자는_같은_방을_쓴다() {
        Long first = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        Long second = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        assertEquals(first, second);
    }

    @Test
    void 구매자가_다르면_방도_다르다() {
        Long buyerRoom = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        Long strangerRoom = chatService.getOrCreateRoom(board.getId(), stranger.getUsername());
        assertNotEquals(buyerRoom, strangerRoom);
    }

    @Test
    void 본인_상품에는_채팅을_걸_수_없다() {
        assertThrows(IllegalArgumentException.class,
                () -> chatService.getOrCreateRoom(board.getId(), seller.getUsername()));
    }

    @Test
    void 방의_참여자는_구매자와_판매자뿐이다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());

        assertTrue(chatService.isParticipant(roomId, buyer.getUsername()));
        assertTrue(chatService.isParticipant(roomId, seller.getUsername()));
        assertFalse(chatService.isParticipant(roomId, stranger.getUsername()));

        assertThrows(AccessDeniedException.class, () -> chatService.getRoom(roomId, stranger.getUsername()));
        assertThrows(AccessDeniedException.class, () -> chatService.getRecentMessages(roomId, stranger.getUsername()));
    }

    @Test
    void 존재하지_않는_방은_참여자가_아닌_것과_똑같이_거부한다() {
        assertFalse(chatService.isParticipant(-1L, buyer.getUsername()));
        assertThrows(AccessDeniedException.class, () -> chatService.getRoom(-1L, buyer.getUsername()));
    }

    @Test
    void 방_정보는_보는_사람_기준으로_내려간다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());

        ChatRoomDTO forBuyer = chatService.getRoom(roomId, buyer.getUsername());
        assertFalse(forBuyer.isSeller());
        assertEquals(seller.getUsername(), forBuyer.getOtherName());

        ChatRoomDTO forSeller = chatService.getRoom(roomId, seller.getUsername());
        assertTrue(forSeller.isSeller());
        assertEquals(buyer.getUsername(), forSeller.getOtherName());
    }

    @Test
    void 메시지는_저장되고_상대방도_이전_대화를_볼_수_있다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());

        ChatMessageDTO saved = chatService.saveMessage(roomId, buyer.getUsername(), "안녕하세요");
        assertEquals(buyer.getUsername(), saved.getSender());

        List<ChatMessageDTO> history = chatService.getRecentMessages(roomId, seller.getUsername()).getMessages();
        assertEquals(1, history.size());
        assertEquals("안녕하세요", history.get(0).getMsg());
        assertEquals(buyer.getUsername(), history.get(0).getSender());
    }

    @Test
    void 참여자가_아니면_메시지를_보낼_수_없다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        assertThrows(AccessDeniedException.class,
                () -> chatService.saveMessage(roomId, stranger.getUsername(), "끼어들기"));
    }

    @Test
    void 빈_메시지와_너무_긴_메시지는_거부한다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        assertThrows(IllegalArgumentException.class, () -> chatService.saveMessage(roomId, buyer.getUsername(), "   "));
        assertThrows(IllegalArgumentException.class, () -> chatService.saveMessage(roomId, buyer.getUsername(), null));
        assertThrows(IllegalArgumentException.class,
                () -> chatService.saveMessage(roomId, buyer.getUsername(), "a".repeat(1001)));
    }

    @Test
    void 내_채팅_목록에는_내가_참여한_방만_나온다() {
        chatService.getOrCreateRoom(board.getId(), buyer.getUsername());

        assertEquals(1, chatService.findMyRooms(buyer.getUsername()).size());
        assertEquals(1, chatService.findMyRooms(seller.getUsername()).size());
        assertTrue(chatService.findMyRooms(stranger.getUsername()).isEmpty());
    }

    // ---- 안 읽은 메시지 ----

    private long unreadOf(User user, Long roomId) {
        return chatService.findMyRooms(user.getUsername()).stream()
                .filter(room -> room.getRoomId().equals(roomId))
                .findFirst()
                .orElseThrow()
                .getUnreadCount();
    }

    @Test
    void 안_읽은_메시지는_상대방이_보낸_것만_세고_읽으면_줄어든다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        chatService.saveMessage(roomId, buyer.getUsername(), "하나");
        ChatMessageDTO second = chatService.saveMessage(roomId, buyer.getUsername(), "둘");

        assertEquals(2, unreadOf(seller, roomId));
        assertEquals(0, unreadOf(buyer, roomId)); // 내가 보낸 메시지는 안 읽음에 포함되지 않는다

        chatService.markRead(roomId, seller.getUsername(), second.getId());
        assertEquals(0, unreadOf(seller, roomId));
    }

    @Test
    void 읽은_번호는_뒤로_가지_않는다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        ChatMessageDTO first = chatService.saveMessage(roomId, buyer.getUsername(), "하나");
        chatService.saveMessage(roomId, buyer.getUsername(), "둘");
        ChatMessageDTO third = chatService.saveMessage(roomId, buyer.getUsername(), "셋");

        chatService.markRead(roomId, seller.getUsername(), third.getId());
        assertEquals(0, unreadOf(seller, roomId));

        // 오래된 번호로 다시 읽음 처리가 들어와도(순서가 뒤바뀐 요청) 이미 읽은 것이 '안 읽음'으로 되돌아가면 안 된다
        chatService.markRead(roomId, seller.getUsername(), first.getId());
        assertEquals(0, unreadOf(seller, roomId));

        chatService.saveMessage(roomId, buyer.getUsername(), "넷");
        assertEquals(1, unreadOf(seller, roomId));
    }

    @Test
    void 구매자와_판매자의_읽음은_따로_기록된다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        ChatMessageDTO fromBuyer = chatService.saveMessage(roomId, buyer.getUsername(), "구매자 메시지");
        chatService.saveMessage(roomId, seller.getUsername(), "판매자 답장");

        assertEquals(1, unreadOf(seller, roomId));
        assertEquals(1, unreadOf(buyer, roomId));

        chatService.markRead(roomId, seller.getUsername(), fromBuyer.getId());
        assertEquals(0, unreadOf(seller, roomId));
        assertEquals(1, unreadOf(buyer, roomId)); // 판매자가 읽어도 구매자의 안 읽음은 그대로
    }

    @Test
    void 참여자가_아니면_읽음_처리도_거부한다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        ChatMessageDTO message = chatService.saveMessage(roomId, buyer.getUsername(), "안녕");
        assertThrows(AccessDeniedException.class,
                () -> chatService.markRead(roomId, stranger.getUsername(), message.getId()));
    }

    @Test
    void 방에서_상대방_이름을_알려준다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());

        assertEquals(seller.getUsername(), chatService.findOtherParticipant(roomId, buyer.getUsername()));
        assertEquals(buyer.getUsername(), chatService.findOtherParticipant(roomId, seller.getUsername()));
        assertThrows(AccessDeniedException.class,
                () -> chatService.findOtherParticipant(roomId, stranger.getUsername()));
    }

    // ---- 대화 기록 페이징 ----

    private void sendMessages(Long roomId, String prefix, int from, int to) {
        for (int i = from; i <= to; i++) {
            chatService.saveMessage(roomId, buyer.getUsername(), prefix + i);
        }
    }

    private List<String> texts(ChatHistoryDTO page) {
        return page.getMessages().stream().map(ChatMessageDTO::getMsg).toList();
    }

    @Test
    void 처음에는_최근_대화만_오래된_것이_위로_오게_준다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        sendMessages(roomId, "m", 1, ChatService.PAGE_SIZE + 15); // 45개

        ChatHistoryDTO recent = chatService.getRecentMessages(roomId, seller.getUsername());

        assertEquals(ChatService.PAGE_SIZE, recent.getMessages().size());
        assertTrue(recent.isHasMore());
        assertEquals("m16", texts(recent).get(0));                       // 이 페이지에서 가장 오래된 것이 위
        assertEquals("m45", texts(recent).get(ChatService.PAGE_SIZE - 1)); // 가장 최근 것이 맨 아래
        for (int i = 1; i < recent.getMessages().size(); i++) {
            assertTrue(recent.getMessages().get(i - 1).getId() < recent.getMessages().get(i).getId(), "번호 오름차순이어야 한다");
        }
    }

    @Test
    void 커서로_이전_페이지를_이어_받으면_빠지거나_겹치는_메시지가_없다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        sendMessages(roomId, "m", 1, ChatService.PAGE_SIZE + 15); // 45개

        ChatHistoryDTO page1 = chatService.getRecentMessages(roomId, seller.getUsername());
        Long cursor = page1.getMessages().get(0).getId();
        ChatHistoryDTO page2 = chatService.getMessagesBefore(roomId, seller.getUsername(), cursor);

        assertEquals(15, page2.getMessages().size());
        assertFalse(page2.isHasMore()); // 더 이상 오래된 대화가 없다

        List<String> all = new ArrayList<>(texts(page2));
        all.addAll(texts(page1));
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= 45; i++) {
            expected.add("m" + i);
        }
        assertEquals(expected, all);
    }

    @Test
    void 딱_페이지_크기만큼이면_더_불러올_것이_없다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        sendMessages(roomId, "m", 1, ChatService.PAGE_SIZE); // 정확히 30개

        ChatHistoryDTO recent = chatService.getRecentMessages(roomId, seller.getUsername());

        assertEquals(ChatService.PAGE_SIZE, recent.getMessages().size());
        assertFalse(recent.isHasMore());
    }

    @Test
    void 한_개_더_있으면_더_불러올_것이_있다고_알려준다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        sendMessages(roomId, "m", 1, ChatService.PAGE_SIZE + 1); // 31개

        ChatHistoryDTO recent = chatService.getRecentMessages(roomId, seller.getUsername());

        assertEquals(ChatService.PAGE_SIZE, recent.getMessages().size());
        assertTrue(recent.isHasMore());
        assertEquals("m2", texts(recent).get(0)); // m1 만 남았다
    }

    @Test
    void 대화가_없으면_빈_페이지이고_더_불러올_것도_없다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());

        ChatHistoryDTO recent = chatService.getRecentMessages(roomId, seller.getUsername());

        assertTrue(recent.getMessages().isEmpty());
        assertFalse(recent.isHasMore());
    }

    @Test
    void 이전_대화를_불러오는_사이_새_메시지가_와도_페이지가_밀리지_않는다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        sendMessages(roomId, "m", 1, ChatService.PAGE_SIZE + 15); // 45개
        ChatHistoryDTO page1 = chatService.getRecentMessages(roomId, seller.getUsername());
        Long cursor = page1.getMessages().get(0).getId();

        sendMessages(roomId, "new", 1, 5); // 이전 대화를 요청하기 전에 새 메시지 5개가 도착

        // '몇 번째 페이지' 방식이었다면 5개가 밀려서 m11~m15 가 빠지거나 중복되었을 것이다
        ChatHistoryDTO page2 = chatService.getMessagesBefore(roomId, seller.getUsername(), cursor);
        assertEquals(15, page2.getMessages().size());
        assertEquals("m1", texts(page2).get(0));
        assertEquals("m15", texts(page2).get(14));
    }

    @Test
    void 참여자가_아니면_이전_대화도_볼_수_없다() {
        Long roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        ChatMessageDTO message = chatService.saveMessage(roomId, buyer.getUsername(), "비밀 대화");

        assertThrows(AccessDeniedException.class,
                () -> chatService.getMessagesBefore(roomId, stranger.getUsername(), message.getId() + 1));
    }

    @Test
    void 전체_안_읽은_개수는_모든_방의_합이다() {
        Board second = new Board();
        second.setTitle("두번째 상품");
        second.setContent("내용");
        second.setPrice("20000");
        second.setUser(seller);
        boardRepository.save(second);

        Long room1 = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());
        Long room2 = chatService.getOrCreateRoom(second.getId(), buyer.getUsername());
        chatService.saveMessage(room1, buyer.getUsername(), "a");
        chatService.saveMessage(room1, buyer.getUsername(), "b");
        chatService.saveMessage(room2, buyer.getUsername(), "c");

        assertEquals(3, chatService.totalUnread(seller.getUsername()));
        assertEquals(2, chatService.unreadByRoom(seller.getUsername()).get(room1));
        assertEquals(1, chatService.unreadByRoom(seller.getUsername()).get(room2));
        assertEquals(0, chatService.totalUnread(buyer.getUsername()));
    }
}
