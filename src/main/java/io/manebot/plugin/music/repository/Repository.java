package io.manebot.plugin.music.repository;

import io.manebot.plugin.music.config.AudioDownloadFormat;
import io.manebot.plugin.music.database.model.TrackFile;
import io.manebot.plugin.music.database.model.TrackRepository;
import io.manebot.plugin.music.source.TrackSource;

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
     * Counts the total number of tracks in this repository.
     * @return
     */
    default long count() {
        return getTrackRepository().countFiles();
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
            return openRead(getFormat());
        }

        OutputStream openWrite(String format) throws IOException;

        default OutputStream openWrite() throws IOException {
            return openWrite(getRepository().getDownloadFormat().getContainerFormat());
        }

        void delete();
    }

    abstract class TrackFileResource implements Resource {
        private final Repository repository;
        private final UUID uuid;
        private final String format;

        public TrackFileResource(Repository repository, UUID uuid, String format) {
            this.repository = repository;
            this.uuid = uuid;
            this.format = format;
        }
    
        @Override
        public Repository getRepository() {
            return repository;
        }
    
        @Override
        public UUID getUUID() {
            return uuid;
        }

        @Override
        public String getFormat() {
            return format;
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
            return !getFile().exists() || getFile().canWrite();
        }

        @Override
        default InputStream openRead(String format) throws IOException {
            return new FileInputStream(getFile());
        }

        @Override
        default OutputStream openWrite(String format) throws IOException {
            // Automatically make the parent path leading to the file
            if (!getFile().getParentFile().exists() && !getFile().getParentFile().mkdirs())
                throw new IOException("mkdirs failed");

            return new FileOutputStream(getFile(), false); // false=overwrite
        }
    }
    
    static UUID toUUID(URL url) {
        return UUID.nameUUIDFromBytes(url.toExternalForm().getBytes());
    }

}
