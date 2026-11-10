package com.My.market.controller;

import com.My.market.service.BoardService;
import com.My.market.repository.BoardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 상품 삭제 API. 모든 /api/** 는 로그인이 필요하다 (WebSecurityConfig).
 *
 * 예전에는 튜토리얼에서 가져온 상품 CRUD(조회/생성/수정)와, 소유자 확인 없는 삭제가 있었다.
 * 화면에서 쓰지 않는 것은 제거하고, 삭제는 소유자(또는 관리자 전용 경로)만 가능하게 바꿨다.
 */
@RestController
@RequestMapping("/api")
public class BoardApiController {

    @Autowired
    private BoardRepository repository;

    @Autowired
    private BoardService boardService;

    /** 내 상품 삭제. 남의 상품이면 403 */
    @DeleteMapping("/boards/{id}")
    void deleteBoard(@PathVariable Long id, Authentication authentication) {
        try {
            boardService.deleteOwnBoard(id, authentication.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /** 관리자는 아무 상품이나 삭제할 수 있다 */
    @Secured("ROLE_ADMIN")
    @DeleteMapping("/boards/admin/{id}")
    void deleteBoardAdmin(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
