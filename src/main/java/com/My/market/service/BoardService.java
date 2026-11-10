package com.My.market.service;

import com.My.market.DTO.BoardDTO;
import com.My.market.model.Board;
import com.My.market.model.BoardFile;
import com.My.market.model.User;
import com.My.market.repository.BoardFileRepository;
import com.My.market.repository.BoardRepository;
import com.My.market.repository.UserRepository;
import jakarta.transaction.TransactionScoped;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.TestOnly;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class BoardService {

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private BoardFileRepository boardFileRepository;

    @Autowired
    private UserRepository userRepository;

    public void save(String username, BoardDTO boardDTO) throws IOException {
        User user = userRepository.findByUsername(username); //board의 계정 주 user_id 가져오기 위해 username으로 정보 가져오기
        boardDTO.setUser_id(user.getId()); //가져온 user 정보에서 user_id추출해서 입력받은 boardDTO에 write
        Long Fid = boardDTO.getFid();

        MultipartFile multipartFile = boardDTO.getMultipartFile(); //사진 파일 담기
        String originalFileName = multipartFile.getOriginalFilename(); //파일 이름 가져오기
        String storedFileName = System.currentTimeMillis() + "_" + originalFileName; //서버 저장용 이름 생성
        Files.createDirectories(Paths.get("C:/upload/")); //업로드 폴더가 없으면 만들기 (없으면 transferTo에서 IOException 발생)
        String savePath = "C:/upload/" + storedFileName; //저장경로 설정
        multipartFile.transferTo(new File(savePath)); //해당 경로(위 경로)에 파일 저장

        Board board = Board.toSaveFile(boardDTO); //받은 boardDTO를 board 모델(엔티티)에 저장
        board.setUser(user); //해당 board와 user_id 가져온 user 맵핑
        boardRepository.save(board);
        Long savedId = board.getId();
        Board newBoard = boardRepository.findById(savedId).get();
        BoardFile boardFile = BoardFile.toBoardFile(Fid, originalFileName, storedFileName);
        boardFile.setBoard(newBoard);
        boardFileRepository.save(boardFile);
    }

    /** 상품 삭제. 상품 소유자만 삭제할 수 있다 (없는 상품이면 IllegalArgumentException, 남의 상품이면 AccessDeniedException) */
    @Transactional
    public void deleteOwnBoard(Long id, String username) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다."));
        if (!board.getUser().getUsername().equals(username)) {
            throw new AccessDeniedException("본인 상품만 삭제할 수 있습니다.");
        }
        boardRepository.delete(board);
    }

    @Transactional
    public List<BoardDTO> findAll() {
        List<Board> boardList = boardRepository.findAll();
        List<BoardDTO> boardDTOList = new ArrayList<>();
        for(Board board: boardList) {
            boardDTOList.add(BoardDTO.toBoardDTO(board));
        }
        return boardDTOList;
    }

    @Transactional
    public BoardDTO findById(Long id) {
        Optional<Board> optionalBoardEntity = boardRepository.findById(id);
        if (optionalBoardEntity.isPresent()) {
            Board board = optionalBoardEntity.get();
            BoardDTO boardDTO = BoardDTO.toBoardDTO(board);
            return boardDTO;
        } else {
            return null;
        }
    }

    @Transactional
    public List<BoardDTO> findByUserId(Long id) {
        List<Board> boardIdList = boardRepository.findByUserId(id);
        List<BoardDTO> boardDTOIdList = new ArrayList<>();
        for(Board board: boardIdList) {
            boardDTOIdList.add(BoardDTO.toBoardDTO(board));
        }
        return boardDTOIdList;
    }
//    public void save(String username, Board board, BoardDTO boardDTO) throws IOException {
//        User user = userRepository.findByUsername(username);
//        board.setUser(user);
//
//        MultipartFile multipartFile = boardDTO.getMultipartFile();
//
////        if(multipartFile == null) {
////            boardRepository.save(board);//파일 가져오기
////        }
////        else {
//        String originalFilename = multipartFile.getOriginalFilename(); //파일 이름 가져오기
//        String storedFileName = System.currentTimeMillis() + "_" + originalFilename; //서버 저장용 이름 생성
//        String savePath = "C:/upload/" + storedFileName; //저장경로 설정
//        multipartFile.transferTo(new File(savePath)); //해당 경로(위 경로)에 파일 저장
//        Long savedId = boardRepository.save(board).getId();
//        Board boardId = boardRepository.findById(savedId).get();
//
//        BoardFile boardFile = BoardFile.toBoardFile(boardId, originalFilename, storedFileName);
//
//        boardFileRepository.save(boardFile);
////        }
//    }
}
