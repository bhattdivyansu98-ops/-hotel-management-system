package com.divyanshu.hotel.dao;

import com.divyanshu.hotel.domain.User;

import java.util.List;
import java.util.Optional;

public interface UserDao {

    User insert(User user);

    Optional<User> findById(long id);

    Optional<User> findByUsername(String username);

    List<User> findAll();

    long count();
}
