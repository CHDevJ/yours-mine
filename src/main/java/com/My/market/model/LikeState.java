package com.My.market.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class LikeState {

    @Id

    private Long id;

    private Long State;

    @ManyToOne
    @JoinColumn(name = "board_id")
    @JsonIgnore
    private Board board;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    public static LikeState toLike(Long id, Long num) {
        LikeState like = new LikeState();
        like.setId(id);
        like.setState(num);
        return like;
    }
}