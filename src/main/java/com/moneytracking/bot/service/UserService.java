package com.moneytracking.bot.service;

import com.moneytracking.bot.entity.User;
import com.moneytracking.bot.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User getOrCreateUser(Long telegramUserId, String username) {
        return userRepository.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramUserId(telegramUserId);
                    newUser.setUsername(username);
                    return userRepository.save(newUser);
                });
    }
}
