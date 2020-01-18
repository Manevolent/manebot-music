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

        if (!file.isDirectory()) throw new IllegalArgumentException("root file repository path is a file");
        if (!file.exists()) if (!file.mkdirs()) throw new IllegalArgumentException("failed to create root repository path");

        this.directory = file;
    }

    @Override
    public Resource get(UUID uuid) throws IllegalArgumentException {
        return new ExistingResource(this, uuid, getTrackRepository().getFile(uuid));
    }

    @Override
    public Resource get(TrackSource.Result result) throws IllegalArgumentException, IOException {
        return get(result.getUUID());
    }

    private class ExistingResource extends TrackFileResource implements FileResource {
        private final File file;

        private ExistingResource(Repository repository, UUID uuid, TrackFile trackFile) {
            super(repository, uuid, trackFile == null ? repository.getDownloadFormat().getContainerFormat() : trackFile.getFormat());
            file = new File(directory, getUUID().toString().replace("-", File.separator));
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public OutputStream openWrite(String format) throws IOException {
            // Automatically make the parent path leading to the file
            File parent = getFile().getParentFile();
            if (!parent.exists() && !parent.mkdirs())
                throw new IOException("mkdirs failed: " + parent.getPath());
    
            File tmpFile = File.createTempFile(getUUID().toString() + "_", null, parent);
            OutputStream baseOutputStream = new FileOutputStream(tmpFile, false); // false=overwrite

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
                    
                    File destination = getFile();
                    if (destination.exists())
                        throw new IOException("destination file already exists: " + destination.getPath());
                    else if (!tmpFile.renameTo(destination))
                        throw new IOException("file rename failed: " + tmpFile.getPath() + " to " + destination.getPath());
                    
                    if (getTrackRepository().getFile(getUUID()) == null)
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
