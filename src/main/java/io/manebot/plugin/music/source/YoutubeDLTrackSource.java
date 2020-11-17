package io.manebot.plugin.music.source;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.manebot.plugin.audio.mixer.input.*;
import io.manebot.plugin.music.*;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.virtual.Virtual;
import org.apache.commons.io.IOUtils;

import java.io.*;

import java.net.*;

import java.net.http.HttpTimeoutException;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class YoutubeDLTrackSource implements TrackSource {
    private static final Set<String> live_protocols = new LinkedHashSet<>();
    static {
        live_protocols.add("m3u8");

        // https://github.com/ytdl-org/youtube-dl/pull/8643
        live_protocols.add("m3u8_native");
    }
    
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 1024;
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
    private JsonObject getJsonMetadata(URL trackUrl) throws TrackDownloadException {
        Process process;
        
        try {
            process = Runtime.getRuntime().exec(
                    new String[] {
                            executablePath, /* youtube-dl executable path */
                            "-4", /* force IPv4. YouTube is very strict about IPv6 */
                            "--no-warnings", /* warnings would flood our error stream */
                            "-j", /* require JSON metadata output; don't output the actual file (we do that ourselves) */
                            trackUrl.toExternalForm() /* track URL */
                    }
            );
        } catch (IOException e) {
            throw new TrackDownloadException("problem opening youtube-dl process", e);
        }
    
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
                throw new TrackDownloadException(new HttpTimeoutException("youtube-dl process timed out after " + timeoutSeconds + " seconds"));
            }
        } catch (InterruptedException | TrackDownloadException e) {
            throw new TrackDownloadException("interrupted waiting for youtube-dl process", e);
        }

        InputStream stdout = stdout_copy.complete();
        InputStream stderr = stderr_copy.complete();

        // A non-zero exit code will fail the download
        if (process.exitValue() != 0) {
            StringBuilder errorBuilder = new StringBuilder();
            
            int available;
            try {
                available = stderr.available();
            } catch (IOException e) {
                available = 0;
            }
    
            if (available > 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderr))) {
                    String s = null;

                    while (reader.ready()) {
                        String s_ = reader.readLine();
                        if (s == null) s = s_;
                        errorBuilder.append(s).append(" ");
                    }
                } catch (IOException e) {
                    throw new TrackDownloadException("youtube-dl exited with code " + process.exitValue(), e);
                }
    
                throw new TrackDownloadException("youtube-dl exited with code " + process.exitValue() + ": " + errorBuilder.toString());
            } else {
                throw new TrackDownloadException("youtube-dl exited with code " + process.exitValue());
            }
        }
    
        String jsonString;
        try {
            jsonString = IOUtils.toString(stdout);
        } catch (IOException e) {
            throw new TrackDownloadException("problem parsing youtube-dl output", e);
        }
    
        return new JsonParser().parse(jsonString).getAsJsonObject();
    }

    @Override
    public Result find(Community community, URL url) throws TrackDownloadException {
        // force HTTP for caching and consistency reasons
        if (url.getProtocol().equals("https")) {
            try {
                url = new URL("http", url.getHost(), url.getPort(), url.getFile());
            } catch (MalformedURLException e) {
                throw new TrackDownloadException(e);
            }
        }

        // download metadata
        JsonObject response = getJsonMetadata(url);

        String extractor = response.has("extractor") ?
                response.get("extractor").getAsString() : "unknown";
    
        // track url
        String urlString = response.has("webpage_url") ?
                response.get("webpage_url").getAsString() : url.toExternalForm();
    
        final URI realUri = URI.create(urlString);
        final URL friendlyUrl;
        try {
            friendlyUrl = realUri.toURL();
        } catch (MalformedURLException e) {
            throw new TrackDownloadException(e);
        }
    
        // track title
        String title = response.has("fulltitle") ?
                response.get("fulltitle").getAsString() :
                response.get("title").getAsString();
        
        if (title == null || title.length() <= 0)
            title = url.getPath();

        Double duration;

        if (response.has("duration") && response.get("duration").getAsDouble() > 0)
            duration = response.get("duration").getAsDouble();
        else
            duration = null; // pod-cast? live video?

        List<FormatOption> formatOptions = new LinkedList<>();
        if (!response.has("formats") || response.get("formats").isJsonNull()) {
            if (response.get("direct").getAsBoolean()) {
                FormatOption formatOption = new FormatOption(
                        Integer.MAX_VALUE, false, realUri, 0L, 0D, 0D,
                                response.get("ext").getAsString(), "direct", null, null, DEFAULT_BUFFER_SIZE
                );
                if (response.has("http_headers"))
                    response.getAsJsonObject("http_headers").entrySet()
                            .forEach(y -> formatOption.getHttpHeaders().put(y.getKey(), y.getValue().getAsString()));

                formatOptions.add(formatOption);
            } else {
                throw new TrackDownloadException("JSON result has no \"formats\" property, and media is not direct");
            }
        } else {
            response.get("formats").getAsJsonArray().forEach((x) -> {
                if (x == null || !x.isJsonObject()) return;

                JsonObject object = x.getAsJsonObject();
                if (!object.has("url") || object.get("url").isJsonNull()) return;

                String downloadUrl = object.get("url").getAsString();
                URI downloadUri = URI.create(downloadUrl);
                try {
                    downloadUri.toURL();
                } catch (MalformedURLException e) {
                    Logger.getGlobal().log(Level.WARNING, "Problem parsing URL " + downloadUrl, e);
                    return;
                }

                String format;
                if (object.has("ext") && !object.get("ext").isJsonNull())
                    format = object.get("ext").getAsString().trim().toLowerCase();
                else
                    format = null;
                
                String protocol;
                if (object.has("protocol") && !object.get("protocol").isJsonNull())
                    protocol = object.get("protocol").getAsString().trim().toLowerCase();
                else
                    protocol = null;
                
                int bufferSize = DEFAULT_BUFFER_SIZE;
                if (object.has("downloader_options")) {
                    JsonObject downloaderOptions = object.get("downloader_options").getAsJsonObject();
                    if (downloaderOptions.has("http_chunk_size")) {
                        bufferSize = downloaderOptions.get("http_chunk_size").getAsInt();
                    }
                }

                int preference;
                if (object.has("preference") && !object.get("preference").isJsonNull())
                    preference = object.get("preference").getAsInt();
                else
                    preference = Integer.MAX_VALUE;

                FormatOption formatOption = new FormatOption(
                        preference,
                        protocol != null && live_protocols.contains(protocol),
                        downloadUri,
                        object.has("filesize") && !object.get("filesize").isJsonNull() ?
                                object.get("filesize").getAsLong() : 0,
                        object.has("abr") && !object.get("abr").isJsonNull() ?
                                object.get("abr").getAsDouble() : 0D,
                        object.has("vbr") && !object.get("vbr").isJsonNull() ?
                                object.get("vbr").getAsDouble() : 0D,
                        format,
                        object.has("format_note") && !object.get("format_note").isJsonNull() ?
                                object.get("format_note").getAsString().trim() : null,
                        object.has("acodec") && !object.get("acodec").isJsonNull() ?
                                object.get("acodec").getAsString().trim() : null,
                        object.has("vcodec") && !object.get("vcodec").isJsonNull() ?
                                object.get("vcodec").getAsString().trim() : null,
                        bufferSize
                );

                if (object.has("http_headers"))
                    object.getAsJsonObject("http_headers").entrySet()
                            .forEach(y -> formatOption.getHttpHeaders().put(y.getKey(), y.getValue().getAsString()));

                formatOptions.add(formatOption);
            });
        }

        // Select the optimal format for acquisition
        FormatOption selectedFormat;

        if (formatOptions.size() == 1) {
            selectedFormat = formatOptions.get(0);
        } else if (formatOptions.size() > 1) {
            if (formatOptions.stream().anyMatch(format -> format.getAudioBitrate() > 0)) {
                // We want the highest bitrate, but the lowest filesize.
                // YouTube throttles the DASH audio options so we totally exclude those from the results
                // Then, we look for options which explicitly provide an audio codec and a bitrate for that codec
                // And then, we descend by abr, then ascend by the file size
                // First option is the video we want
                selectedFormat = formatOptions.stream()
                        .filter(x -> !extractor.equalsIgnoreCase("youtube") ||
                                x.getNote() == null || !x.getNote().equals("DASH audio"))
                        .filter(x -> x.getAudioBitrate() > 0)
                        .min(((Comparator<FormatOption>)
                                (a, b) -> Double.compare(b.getAudioBitrate(), a.getAudioBitrate()))
                                .thenComparingLong(FormatOption::getFilesize)
                        ).orElse(null);
            } else {
                // Best effort, just pick the first one sorted by "preference" value as a last resort
                selectedFormat = formatOptions
                        .stream()
                        .min(Comparator.comparingInt(FormatOption::getPreference))
                        .orElse(null);
            }
        } else {
            throw new IllegalArgumentException("youtube-dl (" + extractor + ") offered no formats");
        }
        
        if (selectedFormat == null)
            throw new IllegalArgumentException("youtube-dl (" + extractor + ") offered no suitable choice from " +
                    formatOptions.size() + " format(s)");

        final FormatOption format = selectedFormat;
        return new DownloadResult(
                community, friendlyUrl,
                ResultPriority.LOW,
                (builder) -> {
                    // this code will set the attributes of a new track to recognize this URL if needed
                    builder.setName(title);
                    builder.setLength(duration);
                    builder.setUrl(friendlyUrl);
                }) {
            @Override
            public AudioProvider openProvider(AudioProtocol protocol) throws IOException {
                if (format.isLive()) {
                    return protocol.openProvider(format.getUri(), format.getBufferSize());
                } else {
                    return protocol.openProvider(openConnection(), format.getBufferSize());
                }
            }
            
            @Override
            public InputStream openConnection() throws IOException {
                return new RangedInputStream(format.getUri().toURL(), format.getHttpHeaders(), format.getBufferSize());
            }
        };
    }

    /**
     * Represents a download option. These are obtained from youtube-dl and used to determine the best bitrate
     * to efficiency ratio for quickly downloading tracks with youtube-dl with the least byte overhead.
     */
    private static class FormatOption {
        private final int preference;
        private final boolean live;
        private final double audio_bitrate;
        private final double video_bitrate;
        private final long filesize;
        private final double audio_efficiency;
        private final double video_efficiency;
        private final URI uri;
        private final String format,note,audioCodec,videoCodec;
        private final int bufferSize;
        private final Map<String, String> httpHeaders = new HashMap<>();

        FormatOption(int preference, boolean live, URI uri, long filesize, double audio_bitrate, double video_bitrate,
                     String format, String note, String audioCodec,
                     String videoCodec,
                     int bufferSize) {
            this.preference = preference;
            this.live = live;
            this.audio_bitrate = audio_bitrate;
            this.video_bitrate = video_bitrate;

            this.filesize = filesize;
            this.format = format;
            this.audioCodec = audioCodec;
            this.videoCodec = videoCodec;
            this.bufferSize = bufferSize;
    
            if (filesize <= 0) {
                this.audio_efficiency = this.video_efficiency = 0;
            } else {
                this.audio_efficiency = audio_bitrate / (filesize * 8);
                this.video_efficiency = video_bitrate / (filesize * 8);
            }

            this.uri = uri;
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
        public URI getUri() {
            return uri;
        }
        public String getFormat() {
            return format;
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
        public int getBufferSize() {
            return bufferSize;
        }
    
        public boolean isLive() {
            return live;
        }

        public int getPreference() {
            return preference;
        }
    }
}
