package io.manebot.plugin.music;

import com.github.manevolent.ffmpeg4j.FFmpeg;
import com.github.manevolent.ffmpeg4j.FFmpegException;
import com.github.manevolent.ffmpeg4j.FFmpegIO;
import com.github.manevolent.ffmpeg4j.FFmpegInput;
import com.github.manevolent.ffmpeg4j.filter.audio.AudioFilter;
import com.github.manevolent.ffmpeg4j.filter.audio.AudioFilterNone;
import com.github.manevolent.ffmpeg4j.filter.audio.FFmpegAudioResampleFilter;
import com.github.manevolent.ffmpeg4j.filter.video.VideoFilterNone;
import com.github.manevolent.ffmpeg4j.source.AudioSourceSubstream;
import com.github.manevolent.ffmpeg4j.source.MediaSourceSubstream;
import com.github.manevolent.ffmpeg4j.stream.output.FFmpegTargetStream;
import com.github.manevolent.ffmpeg4j.stream.source.FFmpegSourceStream;
import com.github.manevolent.ffmpeg4j.transcoder.Transcoder;

import io.manebot.command.CommandSender;
import io.manebot.conversation.Conversation;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginReference;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.player.AudioPlayer;
import io.manebot.plugin.audio.player.FFmpegAudioPlayer;
import io.manebot.plugin.audio.player.TransitionedAudioPlayer;
import io.manebot.plugin.music.api.DefaultMusicRegistration;
import io.manebot.plugin.music.api.MusicRegistration;
import io.manebot.plugin.music.config.AudioDownloadFormat;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.MusicManager;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.plugin.music.repository.FileRepository;
import io.manebot.plugin.music.repository.NullRepository;
import io.manebot.plugin.music.repository.Repository;
import io.manebot.plugin.music.source.DatabaseTrackSource;
import io.manebot.plugin.music.source.TrackSource;
import io.manebot.plugin.music.source.YoutubeDLTrackSource;
import io.manebot.plugin.music.util.SplittableInputStream;
import io.manebot.user.User;
import io.manebot.user.UserType;
import io.manebot.virtual.Virtual;
import io.manebot.virtual.VirtualProcess;
import org.apache.commons.io.IOUtils;
import org.bytedeco.javacpp.avformat;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Music implements PluginReference {
    private final Plugin plugin;
    private final MusicManager musicManager;
    private final DatabaseTrackSource localTrackSource;
    private final Audio audio;

    private final Map<Plugin, MusicRegistration> registrations = new LinkedHashMap<>();
    private final Map<Conversation, TrackAssociation> playingTracks = new LinkedHashMap<>();

    public Music(Plugin plugin, MusicManager manager, Audio audio) {
        this.plugin = plugin;
        this.musicManager = manager;
        this.localTrackSource = new DatabaseTrackSource(this);
        this.audio = audio;

        // Default implementation
        createRegistration(plugin, builder -> {
            builder.registerRepository(FileRepository.class, FileRepository::new);
            builder.registerRepository(NullRepository.class, trackRepository -> new NullRepository());
            builder.registerTrackSource(new YoutubeDLTrackSource(
                    plugin.getProperty("youtube-dl", "youtube-dl"),
                    Integer.parseInt(plugin.getProperty("timeout", "30"))
            ));
        });
    }

    /**
     * Permanently creates a music registration for a specific plugin.
     * @param plugin plugin to create the registration for.
     * @param builder builder constructing this registration.
     * @return MusicRegistration instance.
     */
    public MusicRegistration createRegistration(Plugin plugin, Consumer<MusicRegistration.Builder> builder) {
        if (registrations.containsKey(plugin))
            throw new IllegalArgumentException("plugin", new IllegalStateException("already registered"));

        DefaultMusicRegistration.Builder inst = new DefaultMusicRegistration.Builder(this, plugin);
        builder.accept(inst);

        MusicRegistration registration;
        registrations.put(plugin, registration = inst.build());

        return registration;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public MusicManager getMusicManager() {
        return musicManager;
    }

    public TrackSource getLocalTrackSource() {
        return localTrackSource;
    }

    public TrackSource.Result findRemoteTrack(Community community, URL url) throws IOException {
        return registrations.values().stream()
                .flatMap(registration -> registration.getTrackSources().stream())
                .map(
                        trackSource -> {
                            try {
                                return trackSource.find(community, url);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                ).filter(Objects::nonNull).max(
                        new Comparator<TrackSource.Result>() {
                            @Override
                            public int compare(TrackSource.Result a, TrackSource.Result b) {
                                return Integer.compare(a.getPriority().getOrdinal(), b.getPriority().getOrdinal());
                            }
                        }.reversed().thenComparingInt(x -> x.isLocal() ? 1 : 0)
                ).orElseThrow(() -> new IllegalArgumentException(
                        "url",
                        new Exception("track not found: " + url.toExternalForm())
                ));
    }

    public TrackSource.Result findTrack(Community community, URL url) throws IOException {
        Objects.requireNonNull(community);

        TrackSource.Result databaseResult = getLocalTrackSource().find(community, url);
        if (databaseResult != null && databaseResult.get().exists())
            return databaseResult;

        return findRemoteTrack(community, url);
    }

    public Track play(CommandSender sender, Community community, URL url) throws IOException, FFmpegException {
        return play(sender, community, url, (track) -> {});
    }

    public Track play(CommandSender sender, Community community, URL url, Consumer<Track> onFadeOut)
            throws IOException, FFmpegException {
        // Get the conversation associated with the command sender, the audio channel in turn associated with that chat,
        // and later lock it for this operation.
        final User user = sender.getUser();
        final Conversation conversation = sender.getConversation();
        AudioChannel channel = audio.getChannel(conversation);
        if (channel == null)
            throw new IllegalStateException("There is no audio channel associated with this conversation.");

        // Find track in the universe. Preferentially pulls a local database track, otherwise searches using
        // registered track sources.
        TrackSource.Result result = findTrack(community, url);

        // Get the track. This may actually create a track, if needed.
        Track track = result.getTrack();

        // Get the resource associated with this track. The resource is the local/cached copy of the track, which in
        // most cases won't exist. If it does exist, it is usually on a specific file directory, NFS share, etc.
        Repository.Resource resource = result.get();

        // Open a direct stream to the resource.
        // This is a somewhat complicated process. We must check to see if the resource exists. If it doesn't exist on
        // the Repository associated with the "community" parameter, we should cache it only if that Repository supports
        // writing to this Track's associated resource. If it does exist, we shouldn't save it to the cache twice.
        InputStream direct;
        String format;
        if (resource.exists()) {
            direct = resource.openRead();
            format = resource.getFormat();
        } else {
            direct = result.openDirect();
            format = result.getFormat();
        }

        // (attempt to) get input format from FFmpeg
        avformat.AVInputFormat inputFormat = FFmpeg.getInputFormatByName(format);

        // If we are saving this to the cache, we should wrap/shadow the "direct" stream with an async copy stream.
        // If the length is undefined, we cannot cache this track.
        if (!resource.exists() && resource.canWrite() && track.getLength() != null) {
            SplittableInputStream splitter = new SplittableInputStream(direct);
            direct = splitter;

            final InputStream split = splitter.split();
            final OutputStream target = resource.openWrite();
            CompletableFuture<Boolean> future = new CompletableFuture<>();

            Runnable copyRunnable = () -> {
                FFmpegInput input = null;
                FFmpegIO output = null;

                try {
                    AudioDownloadFormat downloadFormat = resource.getRepository().getDownloadFormat();
                    if (downloadFormat != null) {
                        FFmpegSourceStream sourceStream;

                        try {
                            input = new FFmpegInput(split);
                            sourceStream = input.open(inputFormat);
                            sourceStream.registerStreams();
                        } catch (Exception ex) {
                            throw new FFmpegException(ex);
                        }

                        try {
                            output = FFmpegIO.openOutputStream(target, FFmpegIO.DEFAULT_BUFFER_SIZE);
                        } catch (Exception ex) {
                            throw new FFmpegException(ex);
                        }

                        FFmpegTargetStream targetStream = new FFmpegTargetStream(
                                downloadFormat.getContainerFormat(),
                                output,
                                new FFmpegTargetStream.FFmpegNativeOutput()
                        );

                        AudioSourceSubstream defaultAudioSubstream =
                                (AudioSourceSubstream)
                                        sourceStream.getSubstreams().stream()
                                                .filter(x -> x instanceof AudioSourceSubstream)
                                                .findFirst().orElse(null);

                        AudioFilter audioFilter = new AudioFilterNone();
                        if (defaultAudioSubstream != null &&
                                downloadFormat.getAudioCodec() != null &&
                                downloadFormat.getAudioBitrate() > 0) {
                            targetStream.registerAudioSubstream(
                                    downloadFormat.getAudioCodec(),
                                    downloadFormat.getAudioFormat().toFFmpeg(),
                                    Collections.singletonMap("b", Integer.toString(downloadFormat.getAudioBitrate()))
                            );

                            if (!downloadFormat.getAudioFormat().toFFmpeg().equals(defaultAudioSubstream.getFormat())) {
                                audioFilter = new FFmpegAudioResampleFilter(
                                        defaultAudioSubstream.getFormat(),
                                        downloadFormat.getAudioFormat().toFFmpeg(),
                                        FFmpegAudioResampleFilter.DEFAULT_BUFFER_SIZE
                                );
                            }
                        }

                        for (MediaSourceSubstream substream : sourceStream.getSubstreams()) {
                            if (substream != defaultAudioSubstream) substream.setDecoding(false);
                        }

                        try {
                            new Transcoder(
                                    sourceStream,
                                    targetStream,
                                    audioFilter,
                                    new VideoFilterNone(), /* ignored */
                                    2D
                            ).transcode();
                        } catch (EOFException eof) {
                            // ignore
                        }

                        future.complete(true);
                    } else {
                        // Download format doesn't specify a specific target format; simple copy
                        IOUtils.copy(split, target);
                    }
                } catch (Throwable e) {
                    try {
                        if (input != null) input.close();
                    } catch (Exception e2) {
                        e.addSuppressed(e2);
                    }

                    try {
                        if (output != null) output.close();
                    } catch (Exception e2) {
                        e.addSuppressed(e2);
                    }

                    try {
                        if (target != null) target.close();
                    } catch (Exception e2) {
                        e.addSuppressed(e2);
                    }

                    try {
                        resource.delete();
                    } catch (Exception e2) {
                        e.addSuppressed(e2);
                    }

                    future.completeExceptionally(e);
                }
            };

            VirtualProcess currentProcess = Virtual.getInstance().currentProcess();
            if (currentProcess != null)
                currentProcess.newThreadFactory().newThread(copyRunnable).start();
            else
                new Thread(copyRunnable).start();
        }

        // Use FFmpeg4j to open the "direct" input stream and stream the file from the preferred source.
        AudioPlayer basePlayer = FFmpegAudioPlayer.open(
                AudioPlayer.Type.BLOCKING,
                Virtual.getInstance().currentUser(),
                inputFormat,
                direct,
                channel.getMixer().getBufferSize()
        );

        final TrackAssociation association = new TrackAssociation(track, channel, basePlayer);

        try {
            try (AudioChannel.Ownership ownership = channel.obtainChannel(sender.getPlatformUser().getAssociation())) {
                AudioPlayer player = new TransitionedAudioPlayer(
                        basePlayer,
                        track.getLength() == null ? Double.MAX_VALUE : track.getLength(),
                        track.getLength() == null ? 3D : Math.min(track.getLength() / 4D, 3D),
                        new TransitionedAudioPlayer.Callback() {
                            @Override
                            public void onFadeIn() {
                                playingTracks.put(conversation, association);
                            }

                            @Override
                            public void onFadeOut() {
                                playingTracks.remove(conversation, association);
                                onFadeOut.accept(track);
                            }

                            @Override
                            public void onFinished(double timePlayedInSeconds) {
                                double end = System.currentTimeMillis() / 1000D;

                                // ensure this is called
                                playingTracks.remove(conversation, association);

                                if (user.getType() == UserType.ANONYMOUS) return;

                                // clamp time played
                                if (track.getLength() != null)
                                    timePlayedInSeconds = Math.max(
                                            0D,
                                            Math.min(timePlayedInSeconds, track.getLength())
                                    );

                                double start = end - timePlayedInSeconds;
                                track.addPlayAsync(conversation, user, start, end);
                            }
                        }
                );

                channel.addPlayer(player);
            }
        } catch (Exception e) {
            throw new IOException(e);
        }

        return track;
    }

    @Override
    public void load(Plugin.Future plugin) {
        
    }

    @Override
    public void unload(Plugin.Future plugin) {
        new ArrayList<>(playingTracks.values()).forEach(association -> {
            try {
                association.getPlayer().stop();
            } catch (Exception e) {
                // ignore
            }
        });
    }

    private class TrackAssociation {
        private final Track track;
        private final AudioChannel channel;
        private final AudioPlayer player;

        private TrackAssociation(Track track, AudioChannel channel, AudioPlayer player) {
            this.track = track;
            this.channel = channel;
            this.player = player;
        }

        public Track getTrack() {
            return track;
        }

        public AudioChannel getChannel() {
            return channel;
        }

        public AudioPlayer getPlayer() {
            return player;
        }
    }
}
