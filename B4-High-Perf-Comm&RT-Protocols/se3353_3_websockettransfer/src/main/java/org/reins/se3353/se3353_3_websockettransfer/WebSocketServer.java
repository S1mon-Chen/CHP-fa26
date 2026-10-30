package org.reins.se3353.se3353_3_websockettransfer;

import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ServerEndpoint(value = "/transfer/{userId}")
@Component
public class WebSocketServer {

    public WebSocketServer() {
        //每当有一个连接，都会执行一次构造方法
        System.out.println("新的连接。。。");
    }

    private static final AtomicInteger COUNT = new AtomicInteger();

    private static final ConcurrentHashMap<String, Session> SESSIONS = new ConcurrentHashMap<>();

    public void sendMessage(Session toSession, String message) {
        if (toSession != null) {
            try {
                toSession.getBasicRemote().sendText(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("对方不在线");
        }
    }

    public void sendMessageToUser(String user, String message) {
        System.out.println(user);
        Session toSession = SESSIONS.get(user);
        sendMessage(toSession, message);
        System.out.println(message);
    }


    @OnMessage
    public void onMessage(String message) {
        System.out.println("服务器收到消息：" + message);
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        if (SESSIONS.get(userId) != null) {
            System.out.println("已经上线过了");
            return;
        }
        SESSIONS.put(userId.trim(), session);
        COUNT.incrementAndGet();
        System.out.println(userId + "上线了，当前在线人数：" + COUNT);

    }

    @OnClose
    public void onClose(@PathParam("userId") String userId) {
        SESSIONS.remove(userId);
        COUNT.decrementAndGet();
        System.out.println(userId + "下线了，当前在线人数：" + COUNT);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("发生错误");
        throwable.printStackTrace();
    }
}



