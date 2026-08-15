package com.raspmonitor.rasp_monitor.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello(){
        return "hello";
    }
}

// hello 잘 작동된다. 