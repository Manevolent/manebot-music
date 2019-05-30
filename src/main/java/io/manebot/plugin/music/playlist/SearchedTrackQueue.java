package io.manebot.plugin.music.playlist;

import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.database.search.SearchOperator;
import io.manebot.database.search.SearchResult;

import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class SearchedTrackQueue implements TrackQueue {
    private static final int BATCH_SIZE = 32;

    private final Database database;
    private final Community community;
    private final Search search;

    private final List<Track> queue = new ArrayList<>(BATCH_SIZE);
    private SearchResult<Track> lastResult;

    public SearchedTrackQueue(Database database, Community community, Search search) {
        this.database = database;
        this.community = community;
        this.search = search.withPage(1).withOrders();
    }

    private SearchResult<Track> execute() throws SQLException {
        return Track.createSearch(database, community, (clause) -> {
            //noinspection unchecked
            clause.addPredicate(SearchOperator.MERGE, clause.getCriteriaBuilder().gt(clause.getRoot().get("length"), 0));
        }).random(search, BATCH_SIZE);
    }

    private Track get() {
        if (lastResult == null || queue.size() <= 0) {
            try {
                if (!queue.addAll((lastResult = execute()).getResults()) && queue.size() <= 0)
                    throw new NoSuchElementException();
            } catch (SQLException e) {
                throw new RuntimeException("Problem obtaining tracks for queue", e);
            }
        }

        return queue.get(0);
    }

    private Track scroll() {
        get();
        return queue.remove(0);
    }

    @Override
    public Track next() throws NoSuchElementException {
        return scroll();
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public Track peek() {
        try {
            return get();
        } catch (NoSuchElementException ex) {
            return null;
        }
    }

    @Override
    public long size() {
        try {
            get();
        } catch (NoSuchElementException ex) {
            return 0L;
        }

        return lastResult != null ? lastResult.getTotalResults() : 0L;
    }
}
