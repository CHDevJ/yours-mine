package com.My.market.controller;

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
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    /** 내가 구매자이거나 판매자인 채팅방 목록 */
    @GetMapping("/chatList")
    public String chatList(Model model, Authentication authentication) {
        model.addAttribute("rooms", chatService.findMyRooms(authentication.getName()));
        return "chat/chatList";
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
        model.addAttribute("history", chatService.getHistory(roomId, username));
        model.addAttribute("me", username);
        return "chat/chatRoom";
    }
}
