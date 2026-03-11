package com.testehan.finana.service;

import com.testehan.finana.model.user.User;
import com.testehan.finana.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class UserCreditService {

    private static final Logger logger = LoggerFactory.getLogger(UserCreditService.class);

    private final UserRepository userRepository;

    public UserCreditService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public BigDecimal getCredit(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .map(User::getCredit)
                .orElse(BigDecimal.ZERO);
    }

    public boolean hasEnoughCredit(String userEmail, BigDecimal amount) {
        Optional<User> userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isEmpty()) {
            return false;
        }
        BigDecimal currentCredit = userOpt.get().getCredit();
        return currentCredit != null && currentCredit.compareTo(amount) >= 0;
    }

    public boolean hasAnyCredit(String userEmail) {
        return hasEnoughCredit(userEmail, new BigDecimal("0.01"));
    }

    public void deductCredit(String userEmail, BigDecimal amount) {
        Optional<User> userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isEmpty()) {
            logger.warn("Cannot deduct credit: user not found for email {}", userEmail);
            return;
        }

        User user = userOpt.get();
        BigDecimal currentCredit = user.getCredit();
        if (currentCredit == null) {
            currentCredit = BigDecimal.ZERO;
        }

        BigDecimal newCredit = currentCredit.subtract(amount);
        if (newCredit.compareTo(BigDecimal.ZERO) < 0) {
            newCredit = BigDecimal.ZERO;
        }
        user.setCredit(newCredit);
        userRepository.save(user);

        logger.info("Deducted ${} from user {}. New balance: ${}", amount, userEmail, newCredit);
    }

    public void refundCredit(String userEmail, BigDecimal amount) {
        Optional<User> userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isEmpty()) {
            logger.warn("Cannot refund credit: user not found for email {}", userEmail);
            return;
        }

        User user = userOpt.get();
        BigDecimal currentCredit = user.getCredit();
        if (currentCredit == null) {
            currentCredit = BigDecimal.ZERO;
        }

        BigDecimal newCredit = currentCredit.add(amount);
        user.setCredit(newCredit);
        userRepository.save(user);

        logger.info("Refunded ${} to user {}. New balance: ${}", amount, userEmail, newCredit);
    }
}