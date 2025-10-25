package com.testehan.finana.controller;

import com.testehan.finana.model.UserStock;
import com.testehan.finana.model.UserStockStatus;
import com.testehan.finana.repository.UserStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/stocks")
public class UserStockController {

    private final UserStockRepository userStockRepository;

    @Autowired
    public UserStockController(UserStockRepository userStockRepository) {
        this.userStockRepository = userStockRepository;
    }

    @GetMapping
    public List<UserStock> getUserStocks(@PathVariable String userId) {
        return userStockRepository.findByUserId(userId);
    }

//    @PostMapping
//    public UserStock addUserStock(@PathVariable String userId, @RequestBody UserStock userStock) {
//        userStock.setUserId(userId);
//        return userStockRepository.save(userStock);
//    }

//    @PostMapping("/{stockId}/status/{status}")
//    public UserStock addUserStockWithStatus(@PathVariable String userId, @PathVariable String stockId, @PathVariable UserStockStatus status) {
//        UserStock userStock = new UserStock();
//        userStock.setUserId(userId);
//        userStock.setStockId(stockId);
//        userStock.setStatus(status);
//        return userStockRepository.save(userStock);
//    }

//    @PutMapping("/{stockId}")
//    public UserStock updateUserStock(@PathVariable String userId, @PathVariable String stockId, @RequestBody UserStock userStock) {
//        userStock.setUserId(userId);
//        userStock.setStockId(stockId);
//        return userStockRepository.save(userStock);
//    }

    @PutMapping("/{stockId}/status/{status}")
    public ResponseEntity<UserStock> updateUserStockStatus(@PathVariable String userId, @PathVariable String stockId, @PathVariable UserStockStatus status) {
        return userStockRepository.findByUserIdAndStockId(userId, stockId)
                .map(userStock -> {
                    userStock.setStatus(status);
                    return new ResponseEntity<>(userStockRepository.save(userStock), HttpStatus.OK);
                })
                .orElseGet(() -> {
                    UserStock newUserStock = new UserStock();
                    newUserStock.setUserId(userId);
                    newUserStock.setStockId(stockId);
                    newUserStock.setStatus(status);
                    return new ResponseEntity<>(userStockRepository.save(newUserStock), HttpStatus.CREATED);
                });
    }

    @GetMapping("/{stockId}/status")
    public ResponseEntity<UserStockStatus> getUserStockStatus(@PathVariable String userId, @PathVariable String stockId) {
        UserStockStatus status = userStockRepository.findByUserIdAndStockId(userId, stockId)
                .map(UserStock::getStatus)
                .orElse(UserStockStatus.NEW);
        return new ResponseEntity<>(status, HttpStatus.OK);
    }

    @DeleteMapping("/{stockId}")
    public void deleteUserStock(@PathVariable String userId, @PathVariable String stockId) {
        userStockRepository.deleteById(stockId);
    }
}
