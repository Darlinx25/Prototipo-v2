// context.java
package com.ioteste.control;

import java.time.LocalDateTime;

public class Context {
    private LocalDateTime currentTime;

    public Context(LocalDateTime currentTime) {
        this.currentTime = currentTime;
    }

    public LocalDateTime getCurrentTime() {
        return currentTime;
    }
}