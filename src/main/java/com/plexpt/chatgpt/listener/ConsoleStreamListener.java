package com.plexpt.chatgpt.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * 控制台测试
 * Console Stream Test Listener
 *
 * @author plexpt
 */
@Slf4j
public class ConsoleStreamListener extends AbstractStreamListener {

    private static List<String> messages = new ArrayList<>();

    public List<String> getMessages() {
        return messages;
    }

    public void clearMessages() {
        messages.clear();
    }
    @Override
    public void onMsg(String message) {
        messages.add(message);
        System.out.print(message);
    }

    @Override
    public void onError(Throwable throwable, String response) {

    }

    @Override
    public void setOnComplate(Consumer<String> onComplate) {
        super.setOnComplate(onComplate);
    }

    @Override
    public Consumer<String> getOnComplate() {
        return super.getOnComplate();
    }
}