package com.My.market.service;

import com.My.market.DTO.BoardDTO;
import com.My.market.model.Board;
import com.My.market.model.BoardFile;
import com.My.market.model.BoardState;
import com.My.market.repository.BoardStateRepository;
import com.My.market.model.User;
import com.My.market.repository.BoardFileRepository;
import com.My.market.repository.BoardRepository;
import com.My.market.repository.UserRepository;
import jakarta.transaction.TransactionScoped;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.TestOnly;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Autowired
    private BoardStateRepository boardStateRepository;

    // 상품 이미지 저장 폴더 (application.properties 의 app.upload-dir)
    @Value("${app.upload-dir}")
    private String uploadDir;

    public void save(String username, BoardDTO boardDTO) throws IOException {
        User user = userRepository.findByUsername(username); //board의 계정 주 user_id 가져오기 위해 username으로 정보 가져오기
        boardDTO.setUser_id(user.getId()); //가져온 user 정보에서 user_id추출해서 입력받은 boardDTO에 write
        // 권한 검사는 업로드 파일을 디스크에 쓰기 전에 한다 (거부될 요청이 파일을 남기지 않도록)
        Long Fid = authorizeAndVerifyFileId(user, boardDTO);

        MultipartFile multipartFile = boardDTO.getMultipartFile(); //사진 파일 담기
        String originalFileName = multipartFile.getOriginalFilename(); //파일 이름 가져오기
        String storedFileName = System.currentTimeMillis() + "_" + originalFileName; //서버 저장용 이름 생성
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath); //업로드 폴더가 없으면 만들기 (없으면 transferTo에서 IOException 발생)
        File savePath = uploadPath.resolve(storedFileName).toFile(); //저장경로 설정
        multipartFile.transferTo(savePath); //해당 경로(위 경로)에 파일 저장

        Board board = Board.toSaveFile(boardDTO); //받은 boardDTO를 board 모델(엔티티)에 저장
        board.setUser(user); //해당 board와 user_id 가져온 user 맵핑
        boardRepository.save(board);
        Long savedId = board.getId();
        Board newBoard = boardRepository.findById(savedId).get();
        BoardFile boardFile = BoardFile.toBoardFile(Fid, originalFileName, storedFileName);
        boardFile.setBoard(newBoard);
        boardFileRepository.save(boardFile);
    }

    /**
     * 수정 요청(id 가 있는 요청)이면 존재하는 상품이어야 하고 소유자만 가능하다.
     * (없는 상품이면 IllegalArgumentException, 남의 상품이면 AccessDeniedException)
     *
     * 클라이언트가 보낸 사진 번호(Fid)는 '이 상품의 사진'일 때만 인정하고 아니면 무시한다.
     * 그대로 믿으면 남의 사진 정보를 덮어쓰거나 다른 상품으로 옮길 수 있다.
     */
    private Long authorizeAndVerifyFileId(User user, BoardDTO boardDTO) {
        Long boardId = boardDTO.getId();
        if (boardId == null) {
            return null; // 새 상품 등록: 사진 번호는 서버가 새로 부여한다
        }
        Board existing = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다."));
        if (!existing.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("본인 상품만 수정할 수 있습니다.");
        }
        Long requested = boardDTO.getFid();
        if (requested == null) {
            return null;
        }
        return boardFileRepository.findById(requested)
                .filter(file -> file.getBoard() != null && boardId.equals(file.getBoard().getId()))
                .map(BoardFile::getId)
                .orElse(null);
    }

    /** 판매 상태 변경(1=판매중, 0=판매완료). 상품 소유자만 가능하고, 상태 값은 0 또는 1 이어야 한다. */
    @Transactional
    public void changeSellState(Long boardId, Long state, String username) {
        if (state == null || (state != 0L && state != 1L)) {
            throw new IllegalArgumentException("판매 상태는 0 또는 1 이어야 합니다.");
        }
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다."));
        if (!board.getUser().getUsername().equals(username)) {
            throw new AccessDeniedException("본인 상품의 판매 상태만 바꿀 수 있습니다.");
        }
        BoardState boardState = BoardState.toBoardState(boardId, state);
        boardState.setBoard(board);
        boardStateRepository.save(boardState);
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
}
