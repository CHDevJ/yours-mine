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
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class BoardState {
    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long State;

    @ManyToOne
    @JoinColumn(name = "board_id")
    @JsonIgnore
    private Board board;

    public static BoardState toBoardState(Long id, Long num) {
        BoardState boardState = new BoardState();
        boardState.setId(id);
        boardState.setState(num);
        return boardState;
    }
}

//package com.My.market.model;
//
//import com.fasterxml.jackson.annotation.JsonIgnore;
//import jakarta.persistence.*;
//        import jakarta.persistence.Entity;
//import lombok.Data;
//
//import jakarta.validation.constraints.NotNull;
//import jakarta.validation.constraints.Size;
//
//@Entity
//@Data
//public class Board {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @NotNull
//    @Size(min=2, max=30, message = "제목은 2자 이상 30자 이하입니다.")
//    private String title;
//    private String content;
//
//    @ManyToOne
//    @JoinColumn(name = "user_id")
//    @JsonIgnore
//    private User user;
//}
