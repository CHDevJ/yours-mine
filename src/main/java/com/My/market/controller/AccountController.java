package com.My.market.controller;

import com.My.market.DTO.BoardDTO;
import com.My.market.model.Board;
import com.My.market.model.User;
import com.My.market.repository.BoardRepository;
import com.My.market.repository.UserRepository;
import com.My.market.service.BoardService;
import com.My.market.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/account")
public class AccountController {

    @Autowired
    private UserService userService;

    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/login")
    public String login() {
        return "account/login";
    }

    @GetMapping("/register")
    public String register() {
        return "account/register";
    }

    @PostMapping("/register")
    public String register(User user) {
        userService.save(user);
        return "redirect:/home";
    }

    @GetMapping("/mypage")
    public String mypage(Model model, HttpServletRequest request, @PageableDefault(size = 4) Pageable pageable,
                         @RequestParam(required = false, defaultValue = "") String searchText) {

        String Uid = request.getRemoteUser();
        Long id = userRepository.findByUid(Uid);

        Page<Board> boards = boardRepository.findByIdTitle(id, searchText, pageable);
        Page<BoardDTO> boardDTOs = boards.map(BoardDTO::toBoardDTOPage);
        int startPage = Math.max(1, boards.getPageable().getPageNumber() - 4);
        int endPage = Math.min(boards.getTotalPages(), boards.getPageable().getPageNumber() + 4);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("boards", boardDTOs);

        return "account/mypage";
    }

    @GetMapping("/pick")
    public String pick(Model model, HttpServletRequest request, @PageableDefault(size = 4) Pageable pageable) {

        String Uid = request.getRemoteUser();
        Long uid = userRepository.findByUid(Uid);
        Long num = 1L;

        Page<Board> boards = boardRepository.findBoardsByUserIdAndState(uid, num, pageable);
        Page<BoardDTO> boardDTOs = boards.map(BoardDTO::toBoardDTOPage);
        int startPage = Math.max(1, boards.getPageable().getPageNumber() - 4);
        int endPage = Math.min(boards.getTotalPages(), boards.getPageable().getPageNumber() + 4);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("boards", boardDTOs);

        return "account/pick";
    }
}
