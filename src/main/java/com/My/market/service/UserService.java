package com.My.market.service;

import com.My.market.model.Board;
import com.My.market.model.Role;
import com.My.market.model.User;
import com.My.market.repository.BoardRepository;
import com.My.market.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = {Exception.class})
    public User save(User user) {
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        user.setEnabled(true);
        //비밀번호 암호화 및 사용자 정보 저장

        Role role = new Role();
        role.setId(1l);
        user.getRoles().add(role);
        //권한 세팅

        User savedUser = userRepository.save(user);

        return savedUser;
    }

}
