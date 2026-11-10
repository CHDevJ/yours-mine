package com.My.market.service;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

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
        return toRoomDTO(findParticipantRoom(roomId, username), username);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDTO> findMyRooms(String username) {
        return chatRoomRepository.findMyRooms(username).stream()
                .map(room -> toRoomDTO(room, username))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(Long roomId, String username) {
        return chatRoomRepository.findWithParticipants(roomId)
                .map(room -> isMember(room, username))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getHistory(Long roomId, String username) {
        findParticipantRoom(roomId, username);
        return chatMessageRepository.findHistory(roomId).stream()
                .map(this::toMessageDTO)
                .toList();
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

    private ChatRoomDTO toRoomDTO(ChatRoom room, String me) {
        String sellerName = room.getBoard().getUser().getUsername();
        boolean iAmSeller = sellerName.equals(me);
        String otherName = iAmSeller ? room.getBuyer().getUsername() : sellerName;
        return new ChatRoomDTO(room.getId(), room.getBoard().getId(), room.getBoard().getTitle(), otherName, iAmSeller);
    }

    private ChatMessageDTO toMessageDTO(ChatMessage message) {
        return new ChatMessageDTO(
                ChatMessageDTO.MessageType.TALK,
                message.getRoom().getId(),
                message.getSender().getUsername(),
                message.getContent(),
                TIME_FORMAT.format(message.getSentAt()));
    }
}
