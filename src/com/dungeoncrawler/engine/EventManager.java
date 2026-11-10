package com.dungeoncrawler.engine;

import java.util.LinkedList;
import java.util.List;

public class EventManager {
    public enum EventType {
        SYSTEM,
        LOOT,
        DAMAGE,
        COMBAT
    }

    public static class EventLog {
        public final String message;
        public final EventType type;

        public EventLog(String message, EventType type) {
            this.message = message;
            this.type = type;
        }
    }

    private final LinkedList<EventLog> recentLogs = new LinkedList<>();
    private static final int MAX_LOGS = 4;

    public void publishEvent(String message, EventType type) {
        recentLogs.addLast(new EventLog(message, type));
        if (recentLogs.size() > MAX_LOGS) {
            recentLogs.removeFirst();
        }
    }

    public List<EventLog> getRecentLogs() {
        return recentLogs;
    }

    public void clear() {
        recentLogs.clear();
    }
}
