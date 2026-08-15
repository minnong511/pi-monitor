package com.raspmonitor.rasp_monitor.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "messages")
public class Message {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; 

    private String content; 

    protected Message() {}

    // 생성자는 LOMBOK으로 처리하자 
    public Message(String content) {
        this.content= content;
    }

    public Long getId() {
        return id; 
    }

    public String getContent() {
        return content; 
    }
    
}
