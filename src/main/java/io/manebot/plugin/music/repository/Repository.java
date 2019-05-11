package io.manebot.plugin.music.repository;

import io.manebot.plugin.music.config.AudioDownloadFormat;
import io.manebot.plugin.music.database.model.TrackFile;
import io.manebot.plugin.music.database.model.TrackRepository;
import io.manebot.plugin.music.track.TrackSource;

import java.io.*;

import java.net.URL;

import java.util.UUID;

public interface Repository {

    /**
     * Gets the database model backing this Repository instance.
     * @return TrackRepository instance.
     */
    TrackRepository getTrackRepository();

    /**
     * Gets the default native format of this repository.
     * @return Format.
     */
    default AudioDownloadFormat getDownloadFormat() {
        return getTrackRepository().getFormat();
    }

    /**
     * Opens the specified UUID on this repository.
     * @param uuid UUID to open.
     * @return resource corresponding to the specified UUID.
     * @throws IllegalArgumentException if the track specified is invalid for this repository.
     * @throws IOException if there is a problem accessing the resource.
     */
    Resource get(UUID uuid) throws IllegalArgumentException, IOException;

    /**
     * Opens the specified track source result on this repository.
     * @param result TrackSource result to open.
     * @return resource corresponding to the specified URL.
     * @throws IllegalArgumentException if the track specified is invalid for this repository.
     * @throws IOException if there is a problem accessing the track.
     */
    Resource get(TrackSource.Result result) throws IllegalArgumentException, IOException;

    interface Resource {
        Repository getRepository();

        UUID getUUID();

        boolean exists();

        boolean canWrite();

        String getFormat();

        InputStream openRead(String format) throws IOException;

        default InputStream openRead() throws IOException {
            return openRead(getRepository().getDownloadFormat().getContainerFormat());
        }

        OutputStream openWrite(String format) throws IOException;

        default OutputStream openWrite() throws IOException {
            return openWrite(getRepository().getDownloadFormat().getContainerFormat());
        }
    }

    abstract class TrackFileResource implements Resource {
        private final Repository repository;
        private final TrackFile file;

        public TrackFileResource(Repository repository, TrackFile file) {
            this.repository = repository;
            this.file = file;
        }

        @Override
        public String getFormat() {
            return file.getFormat();
        }

        @Override
        public Repository getRepository() {
            return repository;
        }

        @Override
        public UUID getUUID() {
            return file.getUuid();
        }

        @Override
        public InputStream openRead() throws IOException {
            return openRead(file.getFormat());
        }

        @Override
        public OutputStream openWrite(String format) throws IOException {
            return openWrite(file.getFormat());
        }
    }

    interface FileResource extends Resource {
        File getFile();

        @Override
        default boolean exists() {
            return getFile().exists();
        }

        @Override
        default boolean canWrite() {
            return (!getFile().exists() && getFile().getParentFile().canWrite()) || getFile().canWrite();
        }

        @Override
        default InputStream openRead(String format) throws IOException {
            return new FileInputStream(getFile());
        }

        @Override
        default OutputStream openWrite(String format) throws IOException {
            // Automatically make the parent path leading to the file
            if (!getFile().getParentFile().mkdirs())
                throw new IOException("mkdirs");

            return new FileOutputStream(getFile(), false); // false=overwrite
        }
    }

    static UUID toUUID(URL url) {
        return UUID.nameUUIDFromBytes(url.toExternalForm().getBytes());
    }

}
