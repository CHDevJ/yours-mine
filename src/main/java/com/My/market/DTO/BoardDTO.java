package com.My.market.DTO;

import com.My.market.model.Board;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor // 기본생성자
@AllArgsConstructor // 모든 필드를 매개변수로 하는 생성자
public class BoardDTO {
    private Long id;
    private Long user_id;
    private String title;
    private String content;
    private String price;

    private MultipartFile multipartFile; // save.html -> Controller 파일 담는 용도
    private String originalFileName; // 원본 파일 이름
    private String storedFileName; // 서버 저장용 파일 이름
    private Long Fid;
    private String Username;

    public BoardDTO(Long id, Long user_id, String title, String price) {
        this.id = id;
        this.user_id = user_id;
        this.title = title;
        this.price = price;
    }

    public static BoardDTO toBoardDTO(Board board) {
        BoardDTO boardDTO = new BoardDTO();
        boardDTO.setFid(board.getBoardFiles().get(0).getId());
        boardDTO.setId(board.getId());
        boardDTO.setUser_id(board.getUser().getId());
        boardDTO.setTitle(board.getTitle());
        boardDTO.setContent(board.getContent());
        boardDTO.setPrice(board.getPrice());
        boardDTO.setOriginalFileName(board.getBoardFiles().get(0).getOriginalFileName());
        boardDTO.setStoredFileName(board.getBoardFiles().get(0).getStoredFileName());

        return boardDTO;
    }

    public static BoardDTO toBoardDTOPage(final Board board) {
        return BoardDTO.builder()
                .id(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .Username(board.getUser().getUsername())
                .user_id(board.getUser().getId())
                .price(board.getPrice())
                .originalFileName(board.getBoardFiles().get(0).getOriginalFileName())
                .storedFileName(board.getBoardFiles().get(0).getStoredFileName())
                .build();
    }

}