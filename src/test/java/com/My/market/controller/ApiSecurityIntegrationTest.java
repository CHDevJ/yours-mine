package com.My.market.controller;

import com.My.market.model.Board;
import com.My.market.model.User;
import com.My.market.repository.BoardRepository;
import com.My.market.repository.UserRepository;
import com.My.market.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /api/** 의 접근 제어 검증. 실제 서버를 띄워서 로그인 없이 / 다른 사용자로 / 소유자로 / 관리자로 호출해 본다.
 * (예전에는 /api/** 가 로그인 없이 열려 있어서 회원 목록(비밀번호 해시 포함) 조회와 남의 상품·계정 삭제가 가능했다)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiSecurityIntegrationTest {

    private static final String PASSWORD = "test-pass-1234";

    @LocalServerPort
    private int port;

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BoardRepository boardRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private User owner;
    private User stranger;
    private User admin;
    private Board board;
    private String ownerCookie;
    private String strangerCookie;
    private String adminCookie;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = register("it_api_owner_" + suffix);
        stranger = register("it_api_stranger_" + suffix);
        admin = register("it_api_admin_" + suffix);
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id) VALUES (?, 2)", admin.getId()); // ROLE_ADMIN

        Board newBoard = new Board();
        newBoard.setTitle("API 보안 테스트 상품");
        newBoard.setContent("내용");
        newBoard.setPrice("10000");
        newBoard.setUser(owner);
        board = boardRepository.save(newBoard);

        ownerCookie = login(owner.getUsername());
        strangerCookie = login(stranger.getUsername());
        adminCookie = login(admin.getUsername());
    }

    @AfterEach
    void tearDown() {
        deleteQuietly(owner); // 남아 있는 상품도 cascade 로 함께 지워진다
        deleteQuietly(stranger);
        deleteQuietly(admin);
    }

    // ---------- 로그인 없이는 아무것도 할 수 없다 ----------

    @Test
    void 로그인하지_않으면_회원_목록을_볼_수_없다() throws Exception {
        HttpResponse<String> response = send("GET", "/api/users", null, null);

        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("location").orElse("").contains("/account/login"));
        assertFalse(response.body().contains(owner.getUsername()));
    }

    @Test
    void 로그인하지_않으면_남의_상품과_계정을_삭제할_수_없다() throws Exception {
        HttpResponse<String> deleteBoard = send("DELETE", "/api/boards/" + board.getId(), null, null);
        HttpResponse<String> deleteUser = send("DELETE", "/api/users/" + owner.getId(), null, null);

        assertEquals(302, deleteBoard.statusCode());
        assertEquals(302, deleteUser.statusCode());
        assertTrue(boardRepository.findById(board.getId()).isPresent(), "상품이 삭제되면 안 된다");
        assertTrue(userRepository.findById(owner.getId()).isPresent(), "계정이 삭제되면 안 된다");
    }

    // ---------- 튜토리얼 CRUD 는 이제 없다 ----------

    @Test
    void 회원_조회_삭제_API는_로그인해도_더_이상_없다() throws Exception {
        HttpResponse<String> list = send("GET", "/api/users", strangerCookie, null);
        assertTrue(list.statusCode() >= 400, "회원 목록이 내려가면 안 된다. 실제: " + list.statusCode());
        assertFalse(list.body().contains(owner.getUsername()));

        HttpResponse<String> delete = send("DELETE", "/api/users/" + owner.getId(), strangerCookie, null);
        assertTrue(delete.statusCode() >= 400, "계정 삭제가 성공하면 안 된다. 실제: " + delete.statusCode());
        assertTrue(userRepository.findById(owner.getId()).isPresent());
    }

    @Test
    void 상품_조회_생성_수정_API도_더_이상_없다() throws Exception {
        assertTrue(send("GET", "/api/boards", strangerCookie, null).statusCode() >= 400);
        assertTrue(send("POST", "/api/boards", strangerCookie, null).statusCode() >= 400);
        assertTrue(send("PUT", "/api/boards/" + board.getId(), strangerCookie, null).statusCode() >= 400);
    }

    // ---------- 상품 삭제: 소유자만 ----------

    @Test
    void 남의_상품은_삭제할_수_없다() throws Exception {
        HttpResponse<String> response = send("DELETE", "/api/boards/" + board.getId(), strangerCookie, null);

        assertEquals(403, response.statusCode());
        assertTrue(boardRepository.findById(board.getId()).isPresent(), "남의 상품이 삭제되면 안 된다");
    }

    @Test
    void 소유자는_자기_상품을_삭제할_수_있다() throws Exception {
        HttpResponse<String> response = send("DELETE", "/api/boards/" + board.getId(), ownerCookie, null);

        assertEquals(200, response.statusCode());
        assertTrue(boardRepository.findById(board.getId()).isEmpty());
    }

    @Test
    void 없는_상품을_삭제하면_404이다() throws Exception {
        assertEquals(404, send("DELETE", "/api/boards/999999999", ownerCookie, null).statusCode());
    }

    // ---------- 관리자 삭제 ----------

    @Test
    void 관리자_삭제_경로는_관리자만_쓸_수_있다() throws Exception {
        HttpResponse<String> byStranger = send("DELETE", "/api/boards/admin/" + board.getId(), strangerCookie, null);
        assertEquals(403, byStranger.statusCode());
        assertTrue(boardRepository.findById(board.getId()).isPresent());

        HttpResponse<String> byAdmin = send("DELETE", "/api/boards/admin/" + board.getId(), adminCookie, null);
        assertEquals(200, byAdmin.statusCode());
        assertTrue(boardRepository.findById(board.getId()).isEmpty());
    }

    // ---------- 가입 화면의 아이디 중복 확인은 로그인 없이 동작 ----------

    @Test
    void 아이디_중복_확인은_로그인_없이_쓸_수_있다() throws Exception {
        // 이미 있는 아이디 -> 사용 불가(false)
        HttpResponse<String> taken = send("POST", "/api/users/ConfirmId", null, "username=" + owner.getUsername());
        assertEquals(200, taken.statusCode());
        assertEquals("false", taken.body());

        // 없는 아이디 -> 사용 가능(true)
        HttpResponse<String> free = send("POST", "/api/users/ConfirmId", null,
                "username=it_free_" + UUID.randomUUID().toString().substring(0, 8));
        assertEquals(200, free.statusCode());
        assertEquals("true", free.body());

        // 빈 값 / 값 누락 -> 오류 없이 사용 불가(false)
        assertEquals("false", send("POST", "/api/users/ConfirmId", null, "username=").body());
        assertEquals("false", send("POST", "/api/users/ConfirmId", null, "").body());
    }

    // ---------- 도우미 ----------

    private User register(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(PASSWORD);
        return userService.save(user); // 비밀번호 암호화 + 기본 권한(ROLE_USER) 부여
    }

    private void deleteQuietly(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        try {
            userRepository.deleteById(user.getId());
        } catch (Exception e) {
            System.err.println("테스트 데이터 정리 실패: " + user.getUsername() + " - " + e);
        }
    }

    private String login(String username) throws Exception {
        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8);
        HttpResponse<String> response = send("POST", "/account/login", null, form);

        String location = response.headers().firstValue("location").orElse("");
        assertFalse(location.contains("error"), "로그인 실패: " + location);
        return response.headers().allValues("set-cookie").stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new AssertionError("세션 쿠키를 받지 못했습니다"));
    }

    /** cookie 가 null 이면 로그인하지 않은 요청. formBody 가 있으면 폼 전송. */
    private HttpResponse<String> send(String method, String path, String cookie, String formBody) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (formBody != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded");
            request.method(method, HttpRequest.BodyPublishers.ofString(formBody));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
