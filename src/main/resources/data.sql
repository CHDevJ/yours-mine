-- 회원가입(UserService.save)이 role id=1 을 가정하므로 기본 권한을 미리 넣어둔다.
-- INSERT IGNORE: 앱을 여러 번 재시작해도 중복 오류가 나지 않는다.
INSERT IGNORE INTO role (id, name) VALUES (1, 'ROLE_USER');
INSERT IGNORE INTO role (id, name) VALUES (2, 'ROLE_ADMIN');
