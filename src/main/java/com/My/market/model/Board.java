package com.My.market.model;

import com.My.market.DTO.BoardDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Min;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Getter
@Setter
public class Board {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Size(min=2, max=30, message = "상품명은 2자 이상 30자 이하입니다.")
    private String title;
    private String content;

    @NotNull(message = "가격을 설정해 주세요.")
    @Min(value = 2, message = "가격을 설정해 주세요.")
    private String price;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @OneToMany(mappedBy = "board", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BoardFile> boardFiles = new ArrayList<>();

    @OneToMany(mappedBy = "board", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LikeState> likeStates = new ArrayList<>();

    @OneToOne(mappedBy = "board", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    private BoardState boardState;

    // 상품을 삭제하면 이 상품의 채팅방(과 대화)도 함께 삭제된다. 안 그러면 chat_room 의 외래키 때문에 삭제가 실패한다.
    @OneToMany(mappedBy = "board", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ChatRoom> chatRooms = new ArrayList<>();

    public static Board toSaveFile(BoardDTO boardDTO) {
        Board board = new Board();
        board.setId((boardDTO.getId()));
        board.setTitle(boardDTO.getTitle());
        board.setContent(boardDTO.getContent());
        board.setPrice(boardDTO.getPrice());
        return board;
    }
}
