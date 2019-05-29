package io.manebot.plugin.music.playlist;

import io.manebot.plugin.music.database.model.Track;

import java.util.*;

public class LoopedTrackQueue implements TrackQueue {
    private final List<Track> collection;
    private int index = 0;

    public LoopedTrackQueue(List<Track> collection) {
        this.collection = collection;
    }

    public LoopedTrackQueue(Track track) {
        this(Collections.singletonList(Objects.requireNonNull(track)));
    }

    private int nextIndex() {
        int index = this.index;
        index++;
        if (index >= collection.size())
            index = 0;
        return index;
    }

    @Override
    public Track next() throws NoSuchElementException {
        Track track = collection.get(index);
        index = nextIndex();
        return track;
    }

    @Override
    public boolean hasNext() {
        return collection.size() > 0;
    }

    @Override
    public Track peek() {
        return collection.get(nextIndex());
    }

    @Override
    public long size() {
        return -1;
    }
}
