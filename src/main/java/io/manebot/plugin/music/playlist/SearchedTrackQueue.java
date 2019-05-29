package io.manebot.plugin.music.playlist;

import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.plugin.music.database.model.Track;

import java.util.NoSuchElementException;

public class SearchedTrackQueue implements TrackQueue {
    private final Database database;
    private final Search search;

    public SearchedTrackQueue(Database database, Search search) {
        this.database = database;
        this.search = search;
    }

    @Override
    public Track next() throws NoSuchElementException {
        return null;
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public Track peek() {
        return null;
    }

    @Override
    public long size() {
        return 0;
    }
}
