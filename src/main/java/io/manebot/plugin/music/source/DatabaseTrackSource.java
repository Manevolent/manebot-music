package io.manebot.plugin.music.source;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;

import java.io.IOException;
import java.io.InputStream;

import java.net.URL;
import java.util.Objects;
import java.util.stream.Stream;

public class DatabaseTrackSource implements TrackSource {
    private final Music music;

    public DatabaseTrackSource(Music music) {
        this.music = music;
    }

    @Override
    public String getName() {
        return "Database";
    }

    @Override
    public boolean canFind(URL url) {
        return true;
    }

    @Override
    public Result find(Community community, URL url) {
        return Stream.of(url.toExternalForm(), trimTrailingSlashes(url))
                .map(community::getTrack)
                .filter(Objects::nonNull)
                .map(track -> new LocalResult(track, ResultPriority.IDEAL))
                .findFirst().orElse(null);
    }

    private static class LocalResult extends Result {
        private LocalResult(Track track, ResultPriority priority) {
            super(track.getCommunity(), track.toURL(), track.getUUID(), priority, community -> track);
        }
    
        @Override
        public InputStream openConnection() throws IOException {
            return get().openRead();
        }

        @Override
        public boolean isLocal() {
            return true;
        }
    }

    private static String trimTrailingSlashes(URL url) {
        String urlString = url.toExternalForm();
        while (urlString.endsWith("/") && urlString.length() > 1) {
            urlString = urlString.substring(0, urlString.length() - 1);
        }
        return urlString;
    }
}
