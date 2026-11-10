package com.My.market.service;

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
        assertThrows(AccessDeniedException.class, () -> chatService.getHistory(roomId, stranger.getUsername()));
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

        List<ChatMessageDTO> history = chatService.getHistory(roomId, seller.getUsername());
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
}
