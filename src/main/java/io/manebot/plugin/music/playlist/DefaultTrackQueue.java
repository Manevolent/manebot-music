package io.manebot.plugin.music.playlist;

import io.manebot.plugin.music.database.model.Track;

import java.util.NoSuchElementException;
import java.util.Queue;

public class DefaultTrackQueue implements TrackQueue {
    private final Queue<Track> queue;

    public DefaultTrackQueue(Queue<Track> queue) {
        this.queue = queue;
    }

    @Override
    public Track next() throws NoSuchElementException {
        return queue.remove();
    }

    @Override
    public boolean hasNext() {
        return !queue.isEmpty();
    }

    @Override
    public Track peek() {
        return queue.peek();
    }

    @Override
    public long size() {
        return queue.size();
    }
}
