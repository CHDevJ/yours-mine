package com.My.market.service;

import com.My.market.DTO.ChatHistoryDTO;
import com.My.market.DTO.ChatMessageDTO;
import com.My.market.DTO.ChatRoomDTO;
import com.My.market.model.Board;
import com.My.market.model.ChatMessage;
import com.My.market.model.ChatRoom;
import com.My.market.model.User;
import com.My.market.repository.BoardRepository;
import com.My.market.repository.ChatMessageRepository;
import com.My.market.repository.ChatRoomRepository;
import com.My.market.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    public static final int PAGE_SIZE = 30; // 한 번에 불러오는 대화 개수
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    /**
     * (상품, 구매자) 방을 찾고, 없으면 만든다.
     * 일부러 @Transactional 을 붙이지 않았다: 두 요청이 동시에 같은 방을 만들면 unique 제약에 걸려
     * 예외가 나는데, 하나의 트랜잭션 안에서는 그 뒤에 조회를 이어갈 수 없기 때문이다.
     */
    public Long getOrCreateRoom(Long boardId, String username) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다."));
        User buyer = userRepository.findByUsername(username);
        if (buyer == null) {
            throw new AccessDeniedException("사용자를 찾을 수 없습니다.");
        }
        if (board.getUser().getId().equals(buyer.getId())) {
            throw new IllegalArgumentException("본인 상품에는 채팅을 걸 수 없습니다.");
        }
        return chatRoomRepository.findByBoardIdAndBuyerId(boardId, buyer.getId())
                .orElseGet(() -> createRoom(board, buyer))
                .getId();
    }

    private ChatRoom createRoom(Board board, User buyer) {
        ChatRoom room = new ChatRoom();
        room.setBoard(board);
        room.setBuyer(buyer);
        room.setCreatedAt(LocalDateTime.now());
        try {
            return chatRoomRepository.saveAndFlush(room);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 방을 만든 경우: 먼저 만들어진 방을 그대로 사용한다
            return chatRoomRepository.findByBoardIdAndBuyerId(board.getId(), buyer.getId())
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public ChatRoomDTO getRoom(Long roomId, String username) {
        return toRoomDTO(findParticipantRoom(roomId, username), username, 0L);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDTO> findMyRooms(String username) {
        Map<Long, Long> unread = unreadByRoom(username);
        return chatRoomRepository.findMyRooms(username).stream()
                .map(room -> toRoomDTO(room, username, unread.getOrDefault(room.getId(), 0L)))
                .toList();
    }

    /** 방 번호 -> 그 방의 안 읽은 메시지 개수. 안 읽은 게 없는 방은 들어 있지 않다. */
    @Transactional(readOnly = true)
    public Map<Long, Long> unreadByRoom(String username) {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : chatRoomRepository.countUnreadByRoom(username)) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }

    /** 내 전체 안 읽은 메시지 개수 (메뉴 배지) */
    @Transactional(readOnly = true)
    public long totalUnread(String username) {
        return unreadByRoom(username).values().stream().mapToLong(Long::longValue).sum();
    }

    /** 방에서 username 의 상대방 이름 (구매자라면 판매자, 판매자라면 구매자) */
    @Transactional(readOnly = true)
    public String findOtherParticipant(Long roomId, String username) {
        ChatRoom room = findParticipantRoom(roomId, username);
        String sellerName = room.getBoard().getUser().getUsername();
        return sellerName.equals(username) ? room.getBuyer().getUsername() : sellerName;
    }

    /**
     * username 이 messageId 번 메시지까지 읽었다고 기록한다. 이미 더 뒤까지 읽었다면 아무 일도 하지 않는다.
     * (번호가 앞으로만 가는 것은 UPDATE 문의 조건이 보장한다)
     */
    @Transactional
    public void markRead(Long roomId, String username, Long messageId) {
        if (messageId == null) {
            return;
        }
        ChatRoom room = findParticipantRoom(roomId, username);
        if (room.getBuyer().getUsername().equals(username)) {
            chatRoomRepository.updateBuyerLastRead(roomId, messageId);
        } else {
            chatRoomRepository.updateSellerLastRead(roomId, messageId);
        }
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(Long roomId, String username) {
        return chatRoomRepository.findWithParticipants(roomId)
                .map(room -> isMember(room, username))
                .orElse(false);
    }

    /** 채팅방을 열 때 보여줄 가장 최근 대화 PAGE_SIZE 개 */
    @Transactional(readOnly = true)
    public ChatHistoryDTO getRecentMessages(Long roomId, String username) {
        findParticipantRoom(roomId, username);
        // 한 개 더 조회해서, 더 오래된 대화가 남아 있는지를 별도의 count 쿼리 없이 알아낸다
        return toHistory(chatMessageRepository.findLatest(roomId, PageRequest.of(0, PAGE_SIZE + 1)));
    }

    /** beforeId 번 메시지보다 오래된 대화 PAGE_SIZE 개 (위로 스크롤해서 더 불러올 때) */
    @Transactional(readOnly = true)
    public ChatHistoryDTO getMessagesBefore(Long roomId, String username, Long beforeId) {
        findParticipantRoom(roomId, username);
        return toHistory(chatMessageRepository.findBefore(roomId, beforeId, PageRequest.of(0, PAGE_SIZE + 1)));
    }

    // 조회 결과는 최신 -> 과거 순서다. 화면에는 오래된 것이 위에 와야 하므로 뒤집어서 돌려준다.
    private ChatHistoryDTO toHistory(List<ChatMessage> newestFirst) {
        boolean hasMore = newestFirst.size() > PAGE_SIZE;
        List<ChatMessage> page = hasMore ? newestFirst.subList(0, PAGE_SIZE) : newestFirst;
        List<ChatMessageDTO> oldestFirst = new ArrayList<>(page.stream().map(this::toMessageDTO).toList());
        Collections.reverse(oldestFirst);
        return new ChatHistoryDTO(oldestFirst, hasMore);
    }

    @Transactional
    public ChatMessageDTO saveMessage(Long roomId, String username, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("빈 메시지는 보낼 수 없습니다.");
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("메시지는 " + MAX_MESSAGE_LENGTH + "자 이하여야 합니다.");
        }
        ChatRoom room = findParticipantRoom(roomId, username);
        User sender = userRepository.findByUsername(username);

        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSender(sender);
        message.setContent(content);
        message.setSentAt(LocalDateTime.now());
        chatMessageRepository.save(message);

        return toMessageDTO(message);
    }

    // 방이 없는 경우와 참여자가 아닌 경우를 같은 메시지로 처리한다 (방의 존재 여부를 알려주지 않기 위해)
    private ChatRoom findParticipantRoom(Long roomId, String username) {
        ChatRoom room = chatRoomRepository.findWithParticipants(roomId)
                .orElseThrow(() -> new AccessDeniedException("접근할 수 없는 채팅방입니다."));
        if (!isMember(room, username)) {
            throw new AccessDeniedException("접근할 수 없는 채팅방입니다.");
        }
        return room;
    }

    private boolean isMember(ChatRoom room, String username) {
        return room.getBuyer().getUsername().equals(username)
                || room.getBoard().getUser().getUsername().equals(username);
    }

    private ChatRoomDTO toRoomDTO(ChatRoom room, String me, long unreadCount) {
        String sellerName = room.getBoard().getUser().getUsername();
        boolean iAmSeller = sellerName.equals(me);
        String otherName = iAmSeller ? room.getBuyer().getUsername() : sellerName;
        return new ChatRoomDTO(room.getId(), room.getBoard().getId(), room.getBoard().getTitle(),
                otherName, iAmSeller, unreadCount);
    }

    private ChatMessageDTO toMessageDTO(ChatMessage message) {
        return new ChatMessageDTO(
                message.getId(),
                ChatMessageDTO.MessageType.TALK,
                message.getRoom().getId(),
                message.getSender().getUsername(),
                message.getContent(),
                TIME_FORMAT.format(message.getSentAt()));
    }
}
