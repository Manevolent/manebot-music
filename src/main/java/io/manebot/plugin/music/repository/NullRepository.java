package io.manebot.plugin.music.repository;

import io.manebot.plugin.music.source.TrackSource;

import java.io.*;
import java.util.UUID;

public class NullRepository extends AbstractRepository {
    public NullRepository() {
        super(null);
    }

    @Override
    public Resource get(UUID uuid) throws IllegalArgumentException, IOException {
        return new NullResource(this, uuid);
    }

    @Override
    public Resource get(TrackSource.Result result) throws IllegalArgumentException, IOException {
        return new NullResource(this, Repository.toUUID(result.getUrl()));
    }

    private class NullResource extends AbstractResource {
        private NullResource(Repository repository, UUID uuid) {
            super(repository, uuid);
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public boolean canWrite() {
            return false;
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public InputStream openRead(String format) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public OutputStream openWrite(String format) throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}
