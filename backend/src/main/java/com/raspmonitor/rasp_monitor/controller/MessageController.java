package com.raspmonitor.rasp_monitor.controller;

import com.raspmonitor.rasp_monitor.domain.Message;
import com.raspmonitor.rasp_monitor.dto.MessageRequest;
import com.raspmonitor.rasp_monitor.service.MessageService;
// 내가 구현한 라이브러리 불러오기 

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
// 이거는 기본 프레임워크 라이브러리 

import java.util.List;

@RestController
@RequestMapping("/messages")

public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Message create(@Valid @RequestBody MessageRequest request) {
        return messageService.create(request);
    }

    @GetMapping
    public List<Message> findAll() {
        return messageService.findAll();
    }

}
