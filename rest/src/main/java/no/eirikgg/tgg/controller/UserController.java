package no.eirikgg.tgg.controller;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import no.eirikgg.tgg.db.user.User;
import no.eirikgg.tgg.service.UserService;

public class UserController {

    private UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public void createUser(Context ctx) {
        User userRequest = ctx.bodyAsClass(User.class);
        User persistedUser = userService.createUser(userRequest);
        ctx.status(HttpStatus.OK).json(persistedUser);
    }
}
