package io.manebot.plugin.music.source;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.virtual.Virtual;
import org.apache.commons.io.IOUtils;

import java.io.*;

import java.net.*;

import java.net.http.HttpTimeoutException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
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
                new String[] {
                        executablePath, /* youtube-dl executable path */
                        "-4", /* force IPv4. YouTube is very strict about IPv6 */
                        "--no-warnings", /* warnings would flood our error stream */
                        "-j", /* require JSON metadata output; don't output the actual file (we do that ourselves) */
                        trackUrl.toExternalForm() /* track URL */
                }
        );

        class AsyncCopy implements Runnable {
            private final CompletableFuture<byte[]> future = new CompletableFuture<>();
            private final InputStream inputStream;

            private AsyncCopy(InputStream inputStream) {
                this.inputStream = inputStream;
            }

            @Override
            public void run() {
                ByteArrayOutputStream stdout_baos = new ByteArrayOutputStream();

                try {
                    IOUtils.copy(inputStream, stdout_baos);
                    future.complete(stdout_baos.toByteArray());
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }

            private InputStream complete() {
                return new ByteArrayInputStream(future.join());
            }
        }

        AsyncCopy stdout_copy, stderr_copy;
        Virtual.getInstance().create(stderr_copy = new AsyncCopy(process.getErrorStream())).start();
        Virtual.getInstance().create(stdout_copy = new AsyncCopy(process.getInputStream())).start();

        // Wait up to the specified timeout to obtain metadata.
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

        InputStream stdout = stdout_copy.complete();
        InputStream stderr = stderr_copy.complete();

        // A non-zero exit code will fail the download
        if (process.exitValue() != 0) {
            StringBuilder errorBuilder = new StringBuilder();
            if (stderr.available() > 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderr))) {
                    String s = null;

                    while (reader.ready()) {
                        String s_ = reader.readLine();
                        if (s == null) s = s_;
                        errorBuilder.append(s).append(" ");
                    }
                }

                throw new IOException(
                        trackUrl.toExternalForm(),
                        new RuntimeException(
                                "youtube-dl exited with code " + process.exitValue() + ": " + errorBuilder.toString()
                        )
                );
            } else {
                throw new IOException(
                        trackUrl.toExternalForm(),
                        new RuntimeException("youtube-dl exited with code " + process.exitValue())
                );
            }
        }

        return new JsonParser().parse(IOUtils.toString(stdout)).getAsJsonObject();
    }

    @Override
    public Result find(Community community, URL url) throws IOException, IllegalArgumentException {
        // force HTTP for caching and consistency reasons
        if (url.getProtocol().equals("https"))
            url = new URL("http", url.getHost(), url.getPort(), url.getFile());

        // download metadata
        JsonObject response = getJsonMetadata(url);

        // track url
        String urlString = response.has("url") ? response.get("url").getAsString() : url.toExternalForm();
        final URL realUrl;
        try {
            realUrl = new URI(urlString).toURL();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }

        // track title
        String title = response.has("fulltitle") ?
                response.get("fulltitle").getAsString() :
                response.get("title").getAsString();
        if (title == null || title.length() <= 0)
            throw new IOException("invalid track title: " + title);

        Double duration;

        if (response.has("duration"))
            duration = response.get("duration").getAsDouble();
        else
            duration = null; // pod-cast? live video?

        List<FormatOption> formatOptions = new LinkedList<>();

        if (!response.has("formats") || response.get("formats").isJsonNull()) {
            if (response.get("direct").getAsBoolean()) {
                FormatOption formatOption = new FormatOption(
                        url,
                        0L,
                        0D,
                        0D,
                        response.get("ext").getAsString(),
                        "direct",
                        null,
                        null
                );

                if (response.has("http_headers"))
                    response.getAsJsonObject("http_headers").entrySet()
                            .forEach(y -> formatOption.getHttpHeaders().put(y.getKey(), y.getValue().getAsString()));

                formatOptions.add(formatOption);
            } else {
                throw new IOException("JSON result has no \"formats\" property");
            }
        } else {
            response.get("formats").getAsJsonArray().forEach((x) -> {
                if (x == null || !x.isJsonObject()) return;

                JsonObject object = x.getAsJsonObject();
                if (!object.has("url") || object.get("url").isJsonNull()) return;

                try {
                    FormatOption formatOption = new FormatOption(
                            URI.create(object.get("url").getAsString().trim()).toURL(),
                            object.has("filesize") && !object.get("filesize").isJsonNull() ?
                                    object.get("filesize").getAsLong() : 0,
                            object.has("abr") && !object.get("abr").isJsonNull() ?
                                    object.get("abr").getAsDouble() : 0D,
                            object.has("vbr") && !object.get("vbr").isJsonNull() ?
                                    object.get("vbr").getAsDouble() : 0D,
                            object.has("ext") && !object.get("ext").isJsonNull() ?
                                    object.get("ext").getAsString().trim() : null,
                            object.has("format_note") && !object.get("format_note").isJsonNull() ?
                                    object.get("format_note").getAsString().trim() : null,
                            object.has("acodec") && !object.get("acodec").isJsonNull() ?
                                    object.get("acodec").getAsString().trim() : null,
                            object.has("vcodec") && !object.get("vcodec").isJsonNull() ?
                                    object.get("vcodec").getAsString().trim() : null
                    );

                    if (object.has("http_headers"))
                        object.getAsJsonObject("http_headers").entrySet()
                                .forEach(y -> formatOption.getHttpHeaders().put(y.getKey(), y.getValue().getAsString()));

                    formatOptions.add(formatOption);
                } catch (MalformedURLException e) {
                    // continue
                    return;
                }
            });
        }

        // Select the optimal format for acquisition
        FormatOption selectedFormat = null;
        if (formatOptions.size() == 1) {
            selectedFormat = formatOptions.get(0);
        } else if (formatOptions.size() > 1) {
            // We want the highest bitrate, but the lowest filesize.
            // YouTube throttles the DASH audio options so we totally exclude those from the results
            // Then, we look for options which explicity provide an audio codec and a bitrate for that codec
            // And then, we descend by abr, then ascend by the file size
            // First option is the video we want
            selectedFormat = formatOptions
                    .stream()
                    .filter(x -> x.getNote() == null || !x.getNote().equals("DASH audio"))
                    .filter(x -> x.getAudioBitrate() > 0).min(((Comparator<FormatOption>)
                            (a, b) -> Double.compare(b.getAudioBitrate(), a.getAudioBitrate()))
                            .thenComparingLong(FormatOption::getFilesize)
                    ).orElse(null);
        }
        if (selectedFormat == null)
            throw new IllegalArgumentException(
                    "JSON result offered no suitable format of " + formatOptions.size() + " options"
            );

        final FormatOption format = selectedFormat;

        return new DownloadResult(
                community,
                url,
                selectedFormat.extension,
                ResultPriority.LOW,
                (builder) -> {
                    // this code will set the attributes of a new track, if one is needed to recognize this URL.
                    builder.setName(title);
                    builder.setLength(duration);
                    builder.setUrl(realUrl);
                }
        ) {
            @Override
            public InputStream openDirect() throws IOException {
                HttpURLConnection urlConnection = (HttpURLConnection) format.getUrl().openConnection();
                urlConnection.setUseCaches(true);
                urlConnection.setInstanceFollowRedirects(true);
                urlConnection.setRequestMethod("GET");
                for (Map.Entry<String, String> header : format.getHttpHeaders().entrySet())
                    urlConnection.setRequestProperty(header.getKey(), header.getValue());
                int responseCode = urlConnection.getResponseCode();
                if (responseCode / 100 != 2)
                    throw new IOException(responseCode + ": " + urlConnection.getResponseMessage());
                return urlConnection.getInputStream();
            }
        };
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
