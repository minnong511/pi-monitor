package com.raspmonitor.rasp_monitor.repository;

import com.raspmonitor.rasp_monitor.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

}

// JpaRepository가 INSERT, SELECT, DELETE 등의 기본 DB 작업을 자동으로 구현