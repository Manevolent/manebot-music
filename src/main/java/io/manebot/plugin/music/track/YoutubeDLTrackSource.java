package io.manebot.plugin.music.track;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.manebot.plugin.music.database.model.Community;

import java.io.BufferedReader;
import java.io.IOException;

import java.io.InputStreamReader;
import java.net.URL;

import java.net.http.HttpTimeoutException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class YoutubeDLTrackSource implements TrackSource {
    private final String executablePath;
    private final int timeoutSeconds;

    public YoutubeDLTrackSource(String executablePath, int timeoutSeconds) {
        this.executablePath = executablePath;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String getName() {
        return "youtube-dl";
    }

    @Override
    public boolean canFind(URL url) {
        return true;
    }

    /**
     * Grabs all metadata from the specified video in JSON format using youtube-dl
     * @param trackUrl Video's URL
     * @return JSON metadata
     * @throws IOException
     */
    private JsonObject getJsonMetadata(URL trackUrl) throws IOException {
        Process process = Runtime.getRuntime().exec(
                new String[]{
                        executablePath,
                        "-4",
                        "--no-warnings",
                        "-j",
                        trackUrl.toExternalForm()
                }
        );

        // Wait up to the specified timeout to obtain this metadata.
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                if (process.isAlive()) process.destroyForcibly(); // don't leak the process

                throw new IOException(
                        trackUrl.toExternalForm(),
                        new HttpTimeoutException("youtube-dl process timed out after " + timeoutSeconds + " seconds")
                );
            }
        } catch (InterruptedException e) {
            throw new IOException("interrupted waiting for youtube-dl process", e);
        }

        // A non-zero exit code will fail the download
        if (process.exitValue() != 0) {
            StringBuilder errorBuilder = new StringBuilder();
            if (process.getErrorStream().available() > 0) {
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String s = null;

                    while (reader.ready()) {
                        String s_ = reader.readLine();
                        if (s == null) s = s_;
                        errorBuilder.append(s).append(" ");
                    }
                }
            }

            throw new IOException(
                    trackUrl.toExternalForm(),
                    new RuntimeException(
                            "youtube-dl exited with code " + process.exitValue() + ": " +
                            errorBuilder.toString())
            );
        }

        // Read the output stream
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            return (JsonObject) new JsonParser().parse(builder.toString());
        }
    }



    @Override
    public Result find(Community community, URL url) throws IOException, IllegalArgumentException {

    }

    /**
     * Represents a download option. These are obtained from youtube-dl and used to determine the best bitrate
     * to efficiency ratio for quickly downloading tracks with youtube-dl with the least byte overhead.
     */
    private static class FormatOption {
        private final double audio_bitrate;
        private final double video_bitrate;
        private final long filesize;
        private final double audio_efficiency;
        private final double video_efficiency;
        private final URL url;
        private final String extension,note,audioCodec,videoCodec;
        private final Map<String, String> httpHeaders = new HashMap<>();

        FormatOption(URL url,
                     long filesize,
                     double audio_bitrate, double video_bitrate,
                     String extension, String note,
                     String audioCodec, String videoCodec) {
            this.audio_bitrate = audio_bitrate;
            this.video_bitrate = video_bitrate;

            this.filesize = filesize;
            this.extension = extension;
            this.audioCodec = audioCodec;
            this.videoCodec = videoCodec;

            if (filesize <= 0) {
                this.audio_efficiency = this.video_efficiency = 0;
            } else {
                this.audio_efficiency = audio_bitrate / (filesize * 8);
                this.video_efficiency = video_bitrate / (filesize * 8);
            }

            this.url = url;
            this.note = note;
        }

        public double getAudioBitrate() {
            return audio_bitrate;
        }
        public double getVideoBitrate() {
            return video_bitrate;
        }
        public double getAudioEfficiency() {
            return audio_efficiency;
        }
        public double getVideoEfficiency() {
            return video_efficiency;
        }
        public long getFilesize() {
            return filesize;
        }
        public URL getUrl() {
            return url;
        }
        public String getExtension() {
            return extension;
        }
        public String getNote() {
            return note;
        }
        public String getAudioCodec() {
            return audioCodec;
        }
        public String getVideoCodec() {
            return videoCodec;
        }
        public Map<String, String> getHttpHeaders() {
            return httpHeaders;
        }
    }
}
