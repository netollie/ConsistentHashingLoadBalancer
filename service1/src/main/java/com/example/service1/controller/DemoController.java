package com.example.service1.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
    @RequestMapping("/load")
    public String demo() {
        return "data service[8080]";
    }
}
