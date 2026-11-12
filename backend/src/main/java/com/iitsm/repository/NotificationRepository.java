package com.iitsm.repository;

import com.iitsm.domain.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    long countByUserIdAndReadFalse(Long userId);

    /** One page of a user's notifications; the order comes from the pageable. */
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    /** One page of a user's notifications that have not been read yet. */
    Page<Notification> findByUserIdAndReadFalse(Long userId, Pageable pageable);

    /** All of a user's unread notifications. */
    List<Notification> findByUserIdAndReadFalse(Long userId);
}
