package com.My.market.controller;

import com.My.market.DTO.ChatMessageDTO;
import com.My.market.handler.NotificationWebSocketHandler;
import com.My.market.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;
    private final NotificationWebSocketHandler notificationHandler;

    /** 내가 구매자이거나 판매자인 채팅방 목록 */
    @GetMapping("/chatList")
    public String chatList(Model model, Authentication authentication) {
        model.addAttribute("rooms", chatService.findMyRooms(authentication.getName()));
        return "chat/chatList";
    }

    /** 메뉴 배지용: 내 전체 안 읽은 메시지 개수. 화면이 열릴 때와 알림 소켓이 다시 연결될 때 가져간다. */
    @GetMapping("/unread")
    @ResponseBody
    public Map<String, Long> unread(Authentication authentication) {
        return Map.of("total", chatService.totalUnread(authentication.getName()));
    }

    /** 상품 상세의 '연락하기' : (상품, 구매자) 방을 만들거나 기존 방으로 이동 */
    @PostMapping("/start")
    public String start(@RequestParam Long boardId, Authentication authentication) {
        try {
            Long roomId = chatService.getOrCreateRoom(boardId, authentication.getName());
            return "redirect:/chat/room/" + roomId;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 채팅방 화면. 참여자가 아니면 서비스에서 AccessDeniedException -> 403 */
    @GetMapping("/room/{roomId}")
    public String room(@PathVariable Long roomId, Model model, Authentication authentication) {
        String username = authentication.getName();
        model.addAttribute("room", chatService.getRoom(roomId, username));
        List<ChatMessageDTO> history = chatService.getHistory(roomId, username);
        model.addAttribute("history", history);
        model.addAttribute("me", username);

        // 방금 화면에 보여준 마지막 메시지까지 읽은 것으로 기록한다
        if (!history.isEmpty()) {
            chatService.markRead(roomId, username, history.get(history.size() - 1).getId());
            notificationHandler.notifyUnread(username, roomId); // 다른 탭에 열려 있는 목록/메뉴 배지도 갱신
        }
        return "chat/chatRoom";
    }
}
