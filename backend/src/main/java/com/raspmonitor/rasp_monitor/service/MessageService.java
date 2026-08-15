package com.raspmonitor.rasp_monitor.service;


import com.raspmonitor.rasp_monitor.domain.Message;
import com.raspmonitor.rasp_monitor.dto.MessageRequest;
import com.raspmonitor.rasp_monitor.repository.MessageRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository; 

    public MessageService(MessageRepository messageRepository){
        this.messageRepository = messageRepository;
    }
    
    @Transactional
    public Message create(MessageRequest request){
        Message message = new Message(request.content());
        return messageRepository.save(message); 
    }

    @Transactional(readOnly = true)
    public List<Message> findAll() {
        return messageRepository.findAll();
    }
}
