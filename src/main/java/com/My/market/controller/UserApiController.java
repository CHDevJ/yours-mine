package com.My.market.controller;

import com.My.market.mapper.UserMapper;
import com.My.market.model.Board;
import com.My.market.model.QUser;
import com.My.market.model.User;
import com.My.market.repository.UserRepository;
import com.My.market.service.MemberService;
import com.My.market.service.UserService;
import com.querydsl.core.types.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Slf4j
public class UserApiController {

    @Autowired
    private UserRepository repository;

    @Autowired
    private UserService userService;

    @Autowired
    private MemberService memberService;


    @Autowired
    private UserMapper userMapper;

    @GetMapping("/users")
    Iterable<User> all(@RequestParam(required = false) String method, @RequestParam(required = false) String text) {
        //required 기본이 true인데 이땐 method값 안주면 오류남
        Iterable<User> users = null;
        if("query".equals(method)) {
            users = repository.findByUsernameQuery(text);
        } else if("nativeQuery".equals(method)) {
            users = repository.findByUsernameNativeQuery(text);
        } else if("querydsl".equals(method)) {
            QUser user = QUser.user;
            Predicate predicate = user.username.contains(text);
            users = repository.findAll(predicate);
        } else if("querydslCustom".equals(method)) {
            users = repository.findByUsernameCustom(text);
        } else if("jdbc".equals(method)) {
            users = repository.findByUsernameJdbc(text);
        } else if("mybatis".equals(method)) {
            users = userMapper.getUsers(text);
        }
        else {
            users = repository.findAll();
        }
        return users;
    }
    // end::get-aggregate-root[]
    @PostMapping("/users")
    User newUser(@RequestBody User newUser) {
        return repository.save(newUser);
    }

    // Single item

    @GetMapping("/users/{id}")
    User one(@PathVariable Long id) {

        return repository.findById(id).orElseThrow(null);
    }

    @PutMapping("/users/{id}")
    User replaceUser(@RequestBody User newUser, @PathVariable Long id) {

        return repository.findById(id)
                .map(user -> {
//                    user.setTitle(newUser.getTitle());
//                    user.setContent(newUser.getContent());
//                    user.setBoards(newUser.getBoards());
                    user.getBoards().clear();
                    user.getBoards().addAll(newUser.getBoards());
                    for(Board board : user.getBoards()) {
                        board.setUser(user);
                    }
                    return repository.save(user);
                })
                .orElseGet(() -> {
                    return repository.save(newUser);
                });
    }

    @DeleteMapping("/users/{id}")
    void deleteUser(@PathVariable Long id) {
        repository.deleteById(id);
    }

    @PostMapping("/users/ConfirmId")
    @ResponseBody
    public ResponseEntity<Boolean> confirmId(String username) {
        boolean result = true;

        if(username.trim().isEmpty()) {
            result = false;
        } else {
            if (memberService.selectId(username)) {
                result = false;
            } else {
                result = true;
            }
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

}