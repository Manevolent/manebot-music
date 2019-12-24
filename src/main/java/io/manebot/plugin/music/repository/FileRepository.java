package io.manebot.plugin.music.repository;

import io.manebot.plugin.music.database.model.TrackFile;
import io.manebot.plugin.music.database.model.TrackRepository;
import io.manebot.plugin.music.source.TrackSource;

import java.io.*;
import java.util.UUID;

public class FileRepository extends AbstractRepository {
    private final File directory;

    public FileRepository(TrackRepository repository) {
        super(repository);

        File file = new File(repository.getProperties().get("path").getAsString());

        if (!file.isDirectory()) throw new IllegalArgumentException("file");

        this.directory = file;
    }

    @Override
    public Resource get(UUID uuid) throws IllegalArgumentException {
        TrackFile trackFile = getTrackRepository().getFile(uuid);
        if (trackFile == null) return null;
        return new ExistingResource(this, trackFile);
    }

    @Override
    public Resource get(TrackSource.Result result) throws IllegalArgumentException, IOException {
        return get(result.getTrack().getUuid());
    }

    private class ExistingResource extends TrackFileResource implements FileResource {
        private final File file;

        private ExistingResource(Repository repository, TrackFile trackFile) {
            super(repository, trackFile);
            file = new File(directory, getUUID().toString().replace("-", File.separator));
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public OutputStream openWrite(String format) throws IOException {
            OutputStream baseOutputStream = super.openWrite(format);

            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    baseOutputStream.write(b);
                }

                @Override
                public void write(byte[] b, int offs, int len) throws IOException {
                    baseOutputStream.write(b, offs, len);
                }

                @Override
                public void close() throws IOException {
                    baseOutputStream.close();

                    // Create TrackFile instance for the next run of this
                    getTrackRepository().createFile(getTrackRepository(), getUUID(), format);
                }
            };
        }

        @Override
        public void delete() {
            if (!file.delete()) throw new IllegalStateException();
        }
    }
}
