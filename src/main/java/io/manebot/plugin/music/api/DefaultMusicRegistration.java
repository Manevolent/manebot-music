package io.manebot.plugin.music.api;

import io.manebot.plugin.Plugin;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.TrackRepository;
import io.manebot.plugin.music.repository.Repository;
import io.manebot.plugin.music.source.TrackSource;

import java.util.*;
import java.util.function.Function;

public class DefaultMusicRegistration implements MusicRegistration {
    private final Music music;
    private final Plugin plugin;
    private final Collection<TrackSource> trackSource;
    private final Map<Class<? extends Repository>, Function<TrackRepository, Repository>> repositories;

    public DefaultMusicRegistration(Music music, Plugin plugin,
                                    Collection<TrackSource> trackSource,
                                    Map<Class<? extends Repository>, Function<TrackRepository, Repository>> repositories) {
        this.music = music;
        this.plugin = plugin;
        this.trackSource = trackSource;
        this.repositories = repositories;
    }

    @Override
    public Music getMusic() {
        return music;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public Collection<TrackSource> getTrackSources() {
        return trackSource;
    }

    @Override
    public Map<Class<? extends Repository>, Function<TrackRepository, Repository>> getRepositoryConstructors() {
        return repositories;
    }

    public static class Builder implements MusicRegistration.Builder {
        private final Music music;
        private final Plugin plugin;
        private final Set<TrackSource> trackSource = new HashSet<>();
        private final Map<Class<? extends Repository>, Function<TrackRepository, Repository>> repositories =
                new HashMap<>();

        public Builder(Music music, Plugin plugin) {
            this.music = music;
            this.plugin = plugin;
        }

        @Override
        public Music getMusic() {
            return music;
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }

        @Override
        public MusicRegistration.Builder registerTrackSource(TrackSource trackSource) {
            this.trackSource.add(trackSource);
            return this;
        }

        @Override
        public MusicRegistration.Builder registerRepository(Class<? extends Repository> clazz,
                                                            Function<TrackRepository, Repository> function) {
            repositories.put(clazz, function);
            return this;
        }

        public DefaultMusicRegistration build () {
            return new DefaultMusicRegistration(
                    music,
                    plugin,
                    Collections.unmodifiableCollection(new LinkedList<>(trackSource)),
                    Collections.unmodifiableMap(repositories)
            );
        }
    }
}
