package com.My.market.handler;

import com.My.market.model.Board;
import com.My.market.model.User;
import com.My.market.repository.BoardRepository;
import com.My.market.repository.ChatMessageRepository;
import com.My.market.repository.ChatRoomRepository;
import com.My.market.repository.UserRepository;
import com.My.market.service.ChatService;
import com.My.market.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.PageRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 실제 서버를 띄워서 로그인 -> WebSocket 접속까지 end-to-end 로 검증한다.
 * 서버 쓰레드가 데이터를 봐야 하므로 트랜잭션 롤백을 쓰지 않고 실제로 저장한 뒤 @AfterEach 에서 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTest {

    private static final String PASSWORD = "test-pass-1234";
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BoardRepository boardRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ChatService chatService;
    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final List<Client> clients = new ArrayList<>();

    private User seller;
    private User buyer;
    private User stranger;
    private Board board;
    private Long roomId;
    private String sellerCookie;
    private String buyerCookie;
    private String strangerCookie;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        seller = register("it_seller_" + suffix);
        buyer = register("it_buyer_" + suffix);
        stranger = register("it_stranger_" + suffix);

        Board newBoard = new Board();
        newBoard.setTitle("통합테스트 상품");
        newBoard.setContent("내용");
        newBoard.setPrice("10000");
        newBoard.setUser(seller);
        board = boardRepository.save(newBoard);

        roomId = chatService.getOrCreateRoom(board.getId(), buyer.getUsername());

        sellerCookie = login(seller.getUsername());
        buyerCookie = login(buyer.getUsername());
        strangerCookie = login(stranger.getUsername());
    }

    @AfterEach
    void tearDown() {
        clients.forEach(Client::abort);
        // 판매자를 지우면 상품 -> 채팅방 -> 메시지가 cascade 로 함께 지워진다
        deleteQuietly(seller);
        deleteQuietly(buyer);
        deleteQuietly(stranger);
    }

    // ---------- 접속 보안 ----------

    @Test
    void 로그인하지_않으면_소켓에_접속할_수_없다() {
        assertThrows(CompletionException.class, () -> connect("/ws/chat", null, null));
        assertThrows(CompletionException.class, () -> connect("/ws/notify", null, null));
    }

    @Test
    void 다른_출처의_페이지에서는_로그인된_쿠키로도_접속할_수_없다() {
        assertThrows(CompletionException.class, () -> connect("/ws/chat", buyerCookie, "http://evil.example"));
        assertThrows(CompletionException.class, () -> connect("/ws/notify", buyerCookie, "http://evil.example"));
    }

    @Test
    void 참여자가_아니면_방에_입장할_수_없고_연결이_끊긴다() throws Exception {
        Client intruder = connect("/ws/chat", strangerCookie, null);
        intruder.send(Map.of("type", "ENTER", "roomId", roomId));

        assertEquals(1008, intruder.closeCode.get(WAIT_SECONDS, TimeUnit.SECONDS)); // POLICY_VIOLATION
    }

    // ---------- 채팅 ----------

    @Test
    void 메시지는_방_안의_참여자에게_전달되고_보낸_사람은_서버가_정한다() throws Exception {
        Client buyerChat = enterRoom(buyerCookie);
        Client sellerChat = enterRoom(sellerCookie);

        // 클라이언트가 sender 를 속여서 보내도 무시되어야 한다
        buyerChat.send(Map.of("type", "TALK", "roomId", roomId, "msg", "안녕하세요", "sender", "hacker"));

        JsonNode received = sellerChat.nextOfType("TALK");
        assertEquals(buyer.getUsername(), received.get("sender").asText());
        assertEquals("안녕하세요", received.get("msg").asText());
        assertNotNull(received.get("id"));

        // DB 에도 저장되었다
        assertEquals(1, chatService.getRecentMessages(roomId, seller.getUsername()).getMessages().size());
    }

    @Test
    void 입장하지_않은_방으로_보낸_메시지는_저장되지_않는다() throws Exception {
        Client buyerChat = connect("/ws/chat", buyerCookie, null); // ENTER 하지 않음
        buyerChat.send(Map.of("type", "TALK", "roomId", roomId, "msg", "몰래 보내기"));

        Thread.sleep(700);
        assertTrue(chatService.getRecentMessages(roomId, buyer.getUsername()).getMessages().isEmpty());
    }

    // ---------- 안 읽은 메시지 알림 ----------

    @Test
    void 방에_없는_상대에게는_안_읽은_개수가_알림_소켓으로_온다() throws Exception {
        Client sellerNotify = connectNotify(sellerCookie); // 판매자는 방에 없고 다른 화면만 열어둔 상태
        Client buyerChat = enterRoom(buyerCookie);

        buyerChat.send(Map.of("type", "TALK", "roomId", roomId, "msg", "첫 메시지"));
        JsonNode first = sellerNotify.next();
        assertEquals(roomId, first.get("roomId").asLong());
        assertEquals(1, first.get("roomUnreadCount").asLong());
        assertEquals(1, first.get("totalUnreadCount").asLong());

        buyerChat.send(Map.of("type", "TALK", "roomId", roomId, "msg", "두번째"));
        JsonNode second = sellerNotify.next();
        assertEquals(2, second.get("roomUnreadCount").asLong());
        assertEquals(2, second.get("totalUnreadCount").asLong());
    }

    @Test
    void 방을_보고_있는_상대에게는_알림_대신_읽음_처리된다() throws Exception {
        Client sellerNotify = connectNotify(sellerCookie);
        Client sellerChat = enterRoom(sellerCookie); // 판매자가 방을 보고 있는 중
        Client buyerChat = enterRoom(buyerCookie);

        buyerChat.send(Map.of("type", "TALK", "roomId", roomId, "msg", "보고 있는데 보냄"));

        assertEquals("보고 있는데 보냄", sellerChat.nextOfType("TALK").get("msg").asText());

        // 알림은 '안 읽은 개수가 0' 으로 한 번만 온다 (다른 탭의 배지를 맞추기 위해)
        JsonNode notify = sellerNotify.next();
        assertEquals(0, notify.get("roomUnreadCount").asLong());
        assertEquals(0, notify.get("totalUnreadCount").asLong());
        sellerNotify.expectSilence(600);
        assertEquals(0, chatService.totalUnread(seller.getUsername()));
    }

    // ---------- 상품 삭제 ----------

    @Test
    void 상품을_삭제하면_채팅방과_대화도_함께_삭제된다() {
        chatService.saveMessage(roomId, buyer.getUsername(), "삭제될 대화");
        assertFalse(chatMessageRepository.findLatest(roomId, PageRequest.of(0, 10)).isEmpty());

        boardRepository.deleteById(board.getId());

        assertTrue(chatRoomRepository.findById(roomId).isEmpty());
        assertTrue(chatMessageRepository.findLatest(roomId, PageRequest.of(0, 10)).isEmpty());
    }

    // ---------- 화면(Thymeleaf)과 HTTP API ----------

    @Test
    void 채팅_목록_화면은_방별_배지와_알림_스크립트를_그려준다() throws Exception {
        chatService.saveMessage(roomId, buyer.getUsername(), "안 읽은 메시지");

        HttpResponse<String> response = get("/chat/chatList", sellerCookie);

        assertEquals(200, response.statusCode());
        String html = response.body();
        assertTrue(html.contains("data-room-id=\"" + roomId + "\""), "방 행에 data-room-id 가 있어야 한다");
        assertTrue(html.contains("unread-badge"), "방별 안 읽은 배지가 있어야 한다");
        assertTrue(html.contains("통합테스트 상품"), "상품명이 보여야 한다");
        assertTrue(html.contains("chat-unread-badge"), "메뉴의 전체 배지 요소가 있어야 한다");
        assertTrue(html.contains("/ws/notify"), "로그인한 화면에는 알림 소켓 스크립트가 들어가야 한다");
    }

    @Test
    void 로그인하지_않은_화면에는_알림_스크립트가_들어가지_않는다() throws Exception {
        HttpResponse<String> response = get("/account/login", null);

        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("/ws/notify"));
    }

    @Test
    void 전체_안_읽은_개수_API는_내_개수만_돌려준다() throws Exception {
        chatService.saveMessage(roomId, buyer.getUsername(), "하나");
        chatService.saveMessage(roomId, buyer.getUsername(), "둘");

        assertEquals("{\"total\":2}", get("/chat/unread", sellerCookie).body()); // 판매자 입장에서는 2개
        assertEquals("{\"total\":0}", get("/chat/unread", buyerCookie).body());  // 보낸 사람은 0개
    }

    @Test
    void 채팅방_화면은_참여자에게만_열리고_열면_읽음_처리된다() throws Exception {
        chatService.saveMessage(roomId, buyer.getUsername(), "읽어야 할 메시지");

        assertEquals(403, get("/chat/room/" + roomId, strangerCookie).statusCode());

        HttpResponse<String> response = get("/chat/room/" + roomId, sellerCookie);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("읽어야 할 메시지"), "이전 대화가 화면에 그려져야 한다");
        assertEquals(0, chatService.totalUnread(seller.getUsername()), "방을 열었으니 읽음 처리되어야 한다");
    }

    @Test
    void 이전_대화_API는_커서_앞의_대화를_돌려주고_비참여자는_거부한다() throws Exception {
        for (int i = 1; i <= ChatService.PAGE_SIZE + 5; i++) { // 35개
            chatService.saveMessage(roomId, buyer.getUsername(), "m" + i);
        }
        Long cursor = chatService.getRecentMessages(roomId, seller.getUsername()).getMessages().get(0).getId();

        HttpResponse<String> response = get("/chat/room/" + roomId + "/messages?before=" + cursor, sellerCookie);

        assertEquals(200, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals(5, body.get("messages").size());
        assertEquals("m1", body.get("messages").get(0).get("msg").asText());
        assertEquals("m5", body.get("messages").get(4).get("msg").asText());
        assertFalse(body.get("hasMore").asBoolean());

        assertEquals(403, get("/chat/room/" + roomId + "/messages?before=" + cursor, strangerCookie).statusCode());
        assertEquals(400, get("/chat/room/" + roomId + "/messages", sellerCookie).statusCode()); // before 누락
    }

    @Test
    void 채팅방_화면은_최근_대화만_그리고_더_불러올_것이_있는지_알려준다() throws Exception {
        for (int i = 1; i <= ChatService.PAGE_SIZE + 5; i++) { // 35개 -> 화면에는 m6 ~ m35
            chatService.saveMessage(roomId, buyer.getUsername(), "m" + i);
        }

        String html = get("/chat/room/" + roomId, sellerCookie).body();

        assertTrue(html.contains(">m35<"), "가장 최근 대화는 보여야 한다");
        assertTrue(html.contains(">m6<"), "이 페이지의 가장 오래된 대화는 보여야 한다");
        assertFalse(html.contains(">m5<"), "그보다 오래된 대화는 처음에 그리지 않는다");
        assertTrue(html.contains("let hasMore = true"), "더 불러올 대화가 있다고 알려야 한다");
    }

    @Test
    void 대화가_적으면_더_불러오기를_켜지_않는다() throws Exception {
        chatService.saveMessage(roomId, buyer.getUsername(), "하나");

        String html = get("/chat/room/" + roomId, sellerCookie).body();

        assertTrue(html.contains("let hasMore = false"));
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
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

    /** 실제 로그인 폼을 제출해서 세션 쿠키를 받는다 */
    private String login(String username) throws Exception {
        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/account/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        String location = response.headers().firstValue("location").orElse("");
        assertFalse(location.contains("error"), "로그인 실패: " + location);
        return response.headers().allValues("set-cookie").stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new AssertionError("세션 쿠키를 받지 못했습니다"));
    }

    private Client connect(String path, String cookie, String origin) {
        Client client = new Client();
        WebSocket.Builder builder = http.newWebSocketBuilder();
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (origin != null) {
            builder.header("Origin", origin);
        }
        client.ws = builder.buildAsync(URI.create("ws://localhost:" + port + path), client).join();
        clients.add(client);
        return client;
    }

    private Client connectNotify(String cookie) throws Exception {
        Client client = connect("/ws/notify", cookie, null);
        Thread.sleep(300); // 서버가 연결을 등록할 시간을 준다
        return client;
    }

    /** 채팅 소켓에 접속해서 방에 입장하고, 입장 알림을 받을 때까지 기다린다 */
    private Client enterRoom(String cookie) throws Exception {
        Client client = connect("/ws/chat", cookie, null);
        client.send(Map.of("type", "ENTER", "roomId", roomId));
        client.nextOfType("ENTER");
        return client;
    }

    private class Client implements WebSocket.Listener {
        private final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
        private final StringBuilder buffer = new StringBuilder();
        private WebSocket ws;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                try {
                    messages.add(objectMapper.readTree(buffer.toString()));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeCode.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closeCode.completeExceptionally(error);
        }

        void send(Object payload) throws Exception {
            ws.sendText(objectMapper.writeValueAsString(payload), true).join();
        }

        JsonNode next() throws Exception {
            JsonNode message = messages.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(message, "기다렸지만 메시지가 오지 않았습니다");
            return message;
        }

        JsonNode nextOfType(String type) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (System.nanoTime() < deadline) {
                JsonNode message = messages.poll(200, TimeUnit.MILLISECONDS);
                if (message != null && type.equals(message.path("type").asText())) {
                    return message;
                }
            }
            fail(type + " 메시지를 받지 못했습니다");
            return null;
        }

        void expectSilence(long millis) throws Exception {
            JsonNode extra = messages.poll(millis, TimeUnit.MILLISECONDS);
            assertNull(extra, "오지 않아야 할 메시지가 왔습니다: " + extra);
        }

        void abort() {
            if (ws != null) {
                ws.abort();
            }
        }
    }
}
