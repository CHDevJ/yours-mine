package com.My.market.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 설정(WebSecurityConfig) 자체를 잠그는 테스트.
 *
 * 실제 서버를 띄우는 테스트는 존재하지 않는 경로도 에러 처리 과정(/error)에서 로그인 화면으로 보내져서,
 * 누군가 설정을 실수로 다시 열어도 구분이 안 된다. MockMvc 는 그 에러 처리를 거치지 않으므로
 * '이 경로가 인증을 요구하는가' 만 정확히 검사할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiAccessRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 로그인_없이_회원_API에_접근하면_로그인_화면으로_보낸다() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/account/login"));
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/account/login"));
    }

    @Test
    void 로그인_없이_상품_API에_접근하면_로그인_화면으로_보낸다() throws Exception {
        mockMvc.perform(get("/api/boards"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/account/login"));
        mockMvc.perform(delete("/api/boards/1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/account/login"));
        mockMvc.perform(delete("/api/boards/admin/1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/account/login"));
    }

    @Test
    void 가입_화면의_아이디_중복_확인만_로그인_없이_열려_있다() throws Exception {
        mockMvc.perform(post("/api/users/ConfirmId").param("username", ""))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }
}
