package io.manebot.plugin.music.source;

import io.manebot.plugin.music.Music;
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
    public Result find(Community community, URL url) throws IOException, IllegalArgumentException {
        return Stream.of(community)
                .map(c -> c.getTrack(url))
                .filter(Objects::nonNull)
                .map(track -> new LocalResult(track, ResultPriority.IDEAL))
                .findFirst().orElse(null);
    }

    private class LocalResult extends Result {
        public LocalResult(Track track, ResultPriority priority) {
            super(track.getCommunity(),
                    track.toURL(),
                    priority,
                    community -> track,
                    sender -> {/* Silent */});
        }

        @Override
        public String getFormat() {
            try {
                return get().getFormat();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public InputStream openDirect() throws IOException {
            return music.findRemoteTrack(getCommunity(), getUrl()).openDirect();
        }

        @Override
        public boolean isLocal() {
            return true;
        }
    }
}
