package com.iitsm.service;

import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.user.User;
import com.iitsm.dto.MarkAllReadResponse;
import com.iitsm.dto.NotificationResponse;
import com.iitsm.dto.PageResponse;
import com.iitsm.dto.ResponseMapper;
import com.iitsm.dto.UnreadCountResponse;
import com.iitsm.exception.ResourceNotFoundException;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.repository.NotificationRepository;
import com.iitsm.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** A user's own notifications, for the controller. Nobody can see or change anyone else's. */
@Service
@Transactional
public class NotificationFacade {

    private final CurrentUserService currentUser;
    private final NotificationRepository notifications;
    private final ResponseMapper mapper;

    public NotificationFacade(CurrentUserService currentUser, NotificationRepository notifications, ResponseMapper mapper) {
        this.currentUser = currentUser;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    /** One page of the caller's notifications, newest first, optionally only the unread ones. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(boolean unreadOnly, Pageable requested) {
        Long userId = currentUser.requireUser().getId();
        Pageable pageable = PageRequests.newestFirst(requested);
        Page<Notification> page = unreadOnly
                ? notifications.findByUserIdAndReadFalse(userId, pageable)
                : notifications.findByUserId(userId, pageable);
        return PageResponse.from(page, mapper::toNotification);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(notifications.countByUserIdAndReadFalse(currentUser.requireUser().getId()));
    }

    /**
     * Marks one notification as read. Marking one that is already read changes nothing.
     *
     * @throws ResourceNotFoundException       if there is no such notification
     * @throws UnauthorizedOperationException  if it belongs to someone else
     */
    public NotificationResponse markRead(Long id) {
        User user = currentUser.requireUser();
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification " + id + " not found"));
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedOperationException();
        }
        notification.markRead();
        return mapper.toNotification(notifications.save(notification));
    }

    /** Marks all of the caller's unread notifications as read. */
    public MarkAllReadResponse markAllRead() {
        List<Notification> unread = notifications.findByUserIdAndReadFalse(currentUser.requireUser().getId());
        unread.forEach(Notification::markRead);
        notifications.saveAll(unread);
        return new MarkAllReadResponse(unread.size());
    }
}
