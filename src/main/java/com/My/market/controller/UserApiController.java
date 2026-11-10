package com.My.market.controller;

import com.My.market.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 관련 API.
 *
 * 예전에는 튜토리얼에서 가져온 회원 CRUD(조회/생성/수정/삭제)가 여기에 있었다. 그런데 /api/** 가 로그인 없이
 * 열려 있어서, 누구나 전체 회원과 비밀번호 해시를 조회하고 남의 계정을 삭제할 수 있었다. 화면에서 쓰지 않아 제거했다.
 */
@RestController
@RequestMapping("/api")
public class UserApiController {

    @Autowired
    private MemberService memberService;

    /** 가입 화면의 아이디 중복 확인. 로그인 전에 호출되므로 보안 설정(WebSecurityConfig)에서 이 경로만 열어둔다. */
    @PostMapping("/users/ConfirmId")
    public ResponseEntity<Boolean> confirmId(String username) {
        boolean available = username != null
                && !username.trim().isEmpty()
                && !memberService.selectId(username);
        return new ResponseEntity<>(available, HttpStatus.OK);
    }
}
