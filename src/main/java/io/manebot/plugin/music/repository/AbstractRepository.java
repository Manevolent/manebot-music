package io.manebot.plugin.music.repository;

import io.manebot.plugin.music.database.model.TrackRepository;

import java.util.UUID;

public abstract class AbstractRepository implements Repository {
    private final TrackRepository repository;

    protected AbstractRepository(TrackRepository repository) {
        this.repository = repository;
    }

    @Override
    public TrackRepository getTrackRepository() {
        return repository;
    }

    public static abstract class AbstractResource implements Resource {
        private final Repository repository;
        private final UUID uuid;

        protected AbstractResource(Repository repository, UUID uuid) {
            this.repository = repository;
            this.uuid = uuid;
        }

        @Override
        public Repository getRepository() {
            return repository;
        }

        @Override
        public UUID getUUID() {
            return uuid;
        }
    }
}
