package com.My.market.controller;

import com.My.market.DTO.BoardDTO;
import com.My.market.model.*;
import com.My.market.repository.BoardRepository;
import com.My.market.repository.BoardStateRepository;
import com.My.market.repository.LikeStateRepository;
import com.My.market.repository.UserRepository;
import com.My.market.service.BoardService;
import com.My.market.service.UserService;
import com.My.market.validator.BoardValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Controller
@RequestMapping("/board")
public class BoardController {

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private BoardStateRepository boardStateRepository;

    @Autowired
    private LikeStateRepository likeStateRepository;

    @Autowired
    private BoardService boardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private BoardValidator boardValidator;

    @GetMapping("/list")
    public String list(Model model, @PageableDefault(size = 4) Pageable pageable,
                       @RequestParam(required = false, defaultValue = "") String searchText) {

        Page<Board> boards = boardRepository.findByTitle(searchText, pageable);
        Page<BoardDTO> boardDTOs = boards.map(BoardDTO::toBoardDTOPage);
        int startPage = Math.max(1, boards.getPageable().getPageNumber() - 4);
        int endPage = Math.min(boards.getTotalPages(), boards.getPageable().getPageNumber() + 4);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("boards", boardDTOs);
        return "board/list";
    }

    @GetMapping("/form")
    public String form(Model model, @RequestParam(required = false) Long id, BoardDTO boardDTO) {
        if(id == null) {
            model.addAttribute("board", new BoardDTO());
        } else {
//            Board board = Board.toSaveFile(boardDTO);
            Board board = boardRepository.findByBoardId(id);
            boardDTO = BoardDTO.toBoardDTO(board);
            model.addAttribute("board", boardDTO);
        }
        return "board/form";
    }

    @PostMapping("/form")
    public String postform(@ModelAttribute BoardDTO boardDTO, @Valid Board board, BindingResult bindingResult, Authentication authentication) throws IOException {
        boardValidator.validate(board, bindingResult);
        if (bindingResult.hasErrors()) {
            return "board/form";
        }
        String username = authentication.getName();
        boardService.save(username, boardDTO);

        return "redirect:/board/list";
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable("id") Long id, Model model, HttpServletRequest request) {
        boolean isOwner = false;
        boolean isSell = false;
        boolean isLike = false;

        String Uid = request.getRemoteUser();
        Long u_id = userRepository.findByUid(Uid);
        Board newBoard = boardRepository.findById(id).get();
        User newUser = userRepository.findById(u_id).get();

        BoardDTO boardDTO = boardService.findById(id);
        model.addAttribute("boardDetail", boardDTO);

        Optional<LikeState> likeState = likeStateRepository.findByUIdBId(u_id, id);
        if(likeState.isPresent())
        {
            LikeState newlikeState = likeStateRepository.findByUIdAndBId(u_id, id);
            if(newlikeState.getState() == 0)
            {
                isLike = false; //찜 안누름
            }
            else if(newlikeState.getState() == 1)
            {
                isLike = true; //찜 누름
            }
        }
        else {
            LikeState likeState1 = new LikeState();
            likeState1.setId(id);
            likeState1.setBoard(newBoard);
            likeState1.setUser(newUser);
            likeState1.setState(0L);
            likeStateRepository.save(likeState1);
        }
        model.addAttribute("isLike", isLike);

        Optional<BoardState> boardState = boardStateRepository.findById(id);
        if(boardState.isPresent())
        {
            if (boardState.get().getState() == 0) {
                isSell = true; //판매 완료
            } else if (boardState.get().getState() == 1) {
                isSell = false;
            }
        }
        else
        {
            BoardState boardState1 = new BoardState();
            boardState1.setId(id);
            boardState1.setBoard(newBoard);
            boardState1.setState(1L);
            boardStateRepository.save(boardState1);
        }
        model.addAttribute("isSell", isSell);

        if (u_id != null){
            if(u_id.equals(boardDTO.getUser_id())){
                isOwner = true;
            }
        }
        model.addAttribute("isOwner", isOwner);

        return "board/detail";
    }

    @RequestMapping(value = "/SellState.ajax", method = RequestMethod.POST)
    @ResponseBody
    public void sellState(@RequestParam("num") Long num, @RequestParam("Bid") Long id)
    {
        Board newBoard = boardRepository.findById(id).get();

        BoardState boardState = BoardState.toBoardState(id, num);
        boardState.setBoard(newBoard);
        boardStateRepository.save(boardState);
    }

    @RequestMapping(value = "/LikeState.ajax", method = RequestMethod.POST)
    @ResponseBody
    public void likeState(@RequestParam("num") Long num, @RequestParam("Bid") Long id, HttpServletRequest request)
    {
        String Uid = request.getRemoteUser();
        Long u_id = userRepository.findByUid(Uid); //유저 아이디 가져옴
        System.out.println(u_id);
        Board newBoard = boardRepository.findById(id).get(); //보드 id 가져옴
        User newUser = userRepository.findById(u_id).get();

        LikeState likeState = LikeState.toLike(id, num);
        likeState.setUser(newUser);
        likeState.setBoard(newBoard);

        likeStateRepository.save(likeState);
    }
}
