package com.My.market.controller;

import com.My.market.model.Board;
import com.My.market.model.BoardFile;
import com.My.market.model.BoardState;
import com.My.market.model.User;
import com.My.market.repository.BoardFileRepository;
import com.My.market.repository.BoardRepository;
import com.My.market.repository.BoardStateRepository;
import com.My.market.repository.UserRepository;
import com.My.market.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 상품 수정 / 판매 상태 변경의 권한 검증.
 * 로그인만 하면 남의 상품을 고치거나 남의 상품 상태를 바꿀 수 있었던 문제(권한 검사 누락)를 실제 서버로 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BoardAuthorizationIntegrationTest {

    private static final String PASSWORD = "test-pass-1234";
    private static final String UPLOAD_DIR = "C:/upload";

    @LocalServerPort
    private int port;

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BoardRepository boardRepository;
    @Autowired
    private BoardFileRepository boardFileRepository;
    @Autowired
    private BoardStateRepository boardStateRepository;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private String suffix;
    private User owner;
    private User stranger;
    private Board board;          // owner 의 상품
    private BoardFile ownerFile;  // owner 상품의 사진
    private String ownerCookie;
    private String strangerCookie;

    @BeforeEach
    void setUp() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        owner = register("it_bd_owner_" + suffix);
        stranger = register("it_bd_stranger_" + suffix);

        Board newBoard = new Board();
        newBoard.setTitle("원래 제목");
        newBoard.setContent("원래 설명");
        newBoard.setPrice("10000");
        newBoard.setUser(owner);
        board = boardRepository.save(newBoard);

        BoardFile file = BoardFile.toBoardFile(null, "victim.png", "stored_victim.png");
        file.setBoard(board);
        ownerFile = boardFileRepository.save(file);

        BoardState state = BoardState.toBoardState(board.getId(), 1L); // 1 = 판매중
        state.setBoard(board);
        boardStateRepository.save(state);

        ownerCookie = login(owner.getUsername());
        strangerCookie = login(stranger.getUsername());
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteQuietly(owner); // 상품, 사진, 상태가 cascade 로 함께 지워진다
        deleteQuietly(stranger);
        // 테스트가 업로드 폴더에 남긴 파일 정리
        Path dir = Path.of(UPLOAD_DIR);
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().contains("it_" + suffix)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // 정리 실패는 무시 (다음 실행에 영향 없음)
                    }
                });
            }
        }
    }

    private Optional<Long> sellStateOf(Long boardId) {
        return boardStateRepository.findById(boardId).map(BoardState::getState);
    }

    private boolean uploadedFileExists(String namePart) throws Exception {
        Path dir = Path.of(UPLOAD_DIR);
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(p -> p.getFileName().toString().contains(namePart));
        }
    }

    // ---------- 판매 상태 변경 ----------

    @Test
    void 남의_상품의_판매_상태는_바꿀_수_없다() throws Exception {
        HttpResponse<String> response = postForm("/board/SellState.ajax", strangerCookie,
                "num=0&Bid=" + board.getId());

        assertEquals(403, response.statusCode());
        assertEquals(Optional.of(1L), sellStateOf(board.getId()), "남이 판매완료로 바꾸면 안 된다");
    }

    @Test
    void 소유자는_판매_상태를_바꿀_수_있다() throws Exception {
        assertEquals(200, postForm("/board/SellState.ajax", ownerCookie, "num=0&Bid=" + board.getId()).statusCode());
        assertEquals(Optional.of(0L), sellStateOf(board.getId()));

        assertEquals(200, postForm("/board/SellState.ajax", ownerCookie, "num=1&Bid=" + board.getId()).statusCode());
        assertEquals(Optional.of(1L), sellStateOf(board.getId()));
    }

    @Test
    void 판매_상태는_0_또는_1만_가능하다() throws Exception {
        HttpResponse<String> response = postForm("/board/SellState.ajax", ownerCookie, "num=7&Bid=" + board.getId());

        assertEquals(400, response.statusCode());
        assertEquals(Optional.of(1L), sellStateOf(board.getId()));
    }

    // ---------- 상품 수정 ----------

    @Test
    void 남의_상품은_수정할_수_없고_내용과_소유자도_바뀌지_않는다() throws Exception {
        HttpResponse<String> response = postBoardForm(strangerCookie, boardFields(board.getId(), ownerFile.getId(),
                "해킹된 제목"), "it_" + suffix + "_hack.png");

        assertEquals(403, response.statusCode());
        Board after = boardRepository.findById(board.getId()).orElseThrow();
        assertEquals("원래 제목", after.getTitle(), "남이 제목을 바꾸면 안 된다");
        assertEquals(owner.getId(), after.getUser().getId(), "상품의 소유자가 바뀌면 안 된다");
        assertFalse(uploadedFileExists("it_" + suffix + "_hack"), "거부된 요청이 업로드 파일을 남기면 안 된다");
    }

    @Test
    void 소유자는_자기_상품을_수정할_수_있다() throws Exception {
        HttpResponse<String> response = postBoardForm(ownerCookie, boardFields(board.getId(), ownerFile.getId(),
                "수정된 제목"), "it_" + suffix + "_edit.png");

        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("location").orElse("").contains("/board/list"));
        Board after = boardRepository.findById(board.getId()).orElseThrow();
        assertEquals("수정된 제목", after.getTitle());
        assertEquals(owner.getId(), after.getUser().getId());
    }

    @Test
    void 없는_상품_번호로_수정을_요청하면_거부한다() throws Exception {
        long before = boardRepository.count();

        HttpResponse<String> response = postBoardForm(ownerCookie, boardFields(999_999_999L, null, "유령 상품"),
                "it_" + suffix + "_ghost.png");

        assertEquals(404, response.statusCode());
        assertEquals(before, boardRepository.count(), "새 상품이 만들어지면 안 된다");
        assertFalse(uploadedFileExists("it_" + suffix + "_ghost"), "거부된 요청이 업로드 파일을 남기면 안 된다");
    }

    @Test
    void 남의_사진_번호를_끼워_보내도_그_사진은_바뀌지_않는다() throws Exception {
        // 새 상품을 등록하는 요청(id 없음)에 남의 사진 번호(Fid)를 끼워 넣는다
        postBoardForm(strangerCookie, boardFields(null, ownerFile.getId(), "내 새 상품"), "it_" + suffix + "_new.png");

        BoardFile after = boardFileRepository.findById(ownerFile.getId()).orElseThrow();
        assertEquals("victim.png", after.getOriginalFileName(), "남의 사진 정보가 덮어써지면 안 된다");
        assertEquals(board.getId(), after.getBoard().getId(), "남의 사진이 다른 상품으로 옮겨지면 안 된다");
    }

    // ---------- 상품 등록 / 수정 화면 ----------

    @Test
    void 새_상품_등록은_정상_동작한다() throws Exception {
        String title = "신규_" + suffix;

        HttpResponse<String> response = postBoardForm(ownerCookie, boardFields(null, null, title),
                "it_" + suffix + "_create.png");

        assertEquals(302, response.statusCode());
        Board created = boardRepository.findAll().stream()
                .filter(b -> title.equals(b.getTitle())).findFirst().orElseThrow();
        assertEquals(owner.getId(), created.getUser().getId());
    }

    @Test
    void 남의_상품의_수정_화면은_열리지_않는다() throws Exception {
        assertEquals(403, get("/board/form?id=" + board.getId(), strangerCookie).statusCode());
        assertEquals(200, get("/board/form?id=" + board.getId(), ownerCookie).statusCode());
    }

    // ---------- 도우미 ----------

    private Map<String, String> boardFields(Long id, Long fid, String title) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (id != null) {
            fields.put("id", String.valueOf(id));
        }
        if (fid != null) {
            fields.put("Fid", String.valueOf(fid));
        }
        fields.put("title", title);
        fields.put("content", "설명입니다");
        fields.put("price", "5000");
        return fields;
    }

    private User register(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(PASSWORD);
        return userService.save(user);
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
        HttpResponse<String> response = postForm("/account/login", null, form);
        String location = response.headers().firstValue("location").orElse("");
        assertFalse(location.contains("error"), "로그인 실패: " + location);
        return response.headers().allValues("set-cookie").stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new AssertionError("세션 쿠키를 받지 못했습니다"));
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String cookie, String formBody) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 상품 등록/수정 폼(multipart)을 그대로 흉내 내서 보낸다 */
    private HttpResponse<String> postBoardForm(String cookie, Map<String, String> fields, String uploadFileName)
            throws Exception {
        String boundary = "----it" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                    + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"multipartFile\"; filename=\""
                + uploadFileName + "\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("fake-image-bytes".getBytes(StandardCharsets.UTF_8));
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/board/form"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
