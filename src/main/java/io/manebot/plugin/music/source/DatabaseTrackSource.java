package io.manebot.plugin.music.source;

import io.manebot.lambda.ThrowingFunction;
import io.manebot.plugin.audio.mixer.input.AudioProvider;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.function.Function;
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
                .map(track -> new LocalResult(
                        track,
                        ResultPriority.IDEAL
                ))
                .findFirst().orElse(null);
    }

    private class LocalResult extends Result {
        public LocalResult(Track track, ResultPriority priority) {
            this(track, priority,
                    (protocol) -> music.findRemoteTrack(track.getCommunity(), track.toURL()).openDirect(protocol));
        }

        public LocalResult(Track track,
                           ResultPriority priority,
                           ThrowingFunction<AudioProtocol, AudioProvider, IOException> open) {
            super(track.getCommunity(),
                    track.toURL(),
                    priority,
                    community -> track,
                    open,
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
        public boolean isLocal() {
            return true;
        }
    }
}
