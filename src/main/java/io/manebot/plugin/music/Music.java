package io.manebot.plugin.music;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.*;
import io.manebot.chat.Chat;
import io.manebot.chat.TextStyle;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandAccessException;
import io.manebot.conversation.Conversation;
import io.manebot.database.Database;
import io.manebot.database.model.Platform;
import io.manebot.database.search.Search;
import io.manebot.database.search.SearchResult;
import io.manebot.event.Event;
import io.manebot.platform.PlatformUser;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginReference;
import io.manebot.plugin.audio.*;
import io.manebot.plugin.audio.channel.AudioChannel;

import io.manebot.plugin.audio.mixer.input.AudioProvider;
import io.manebot.plugin.audio.mixer.input.ResampledAudioProvider;
import io.manebot.plugin.audio.mixer.output.*;
import io.manebot.plugin.audio.player.AudioPlayer;

import io.manebot.plugin.audio.player.TransitionedAudioPlayer;
import io.manebot.plugin.audio.resample.*;
import io.manebot.plugin.music.api.DefaultMusicRegistration;
import io.manebot.plugin.music.api.MusicRegistration;
import io.manebot.plugin.music.config.*;
import io.manebot.plugin.music.config.AudioFormat;
import io.manebot.plugin.music.database.model.*;
import io.manebot.plugin.music.event.playlist.*;
import io.manebot.plugin.music.event.track.*;
import io.manebot.plugin.music.playlist.*;
import io.manebot.plugin.music.repository.FileRepository;
import io.manebot.plugin.music.repository.NullRepository;
import io.manebot.plugin.music.repository.Repository;
import io.manebot.plugin.music.source.*;
import io.manebot.security.Permission;
import io.manebot.tuple.Pair;
import io.manebot.user.User;
import io.manebot.user.UserAssociation;
import io.manebot.user.UserType;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Music implements PluginReference {
    private static final int transcodeBufferSize = 32768;

    private final Plugin plugin;
    private final Database database;
    private final MusicManager musicManager;
    private final DatabaseTrackSource localTrackSource;
    private final Audio audio;

    private final AudioProtocol protocol = new FFmpegAudioProtocol(10D);
    private final ResamplerFactory resamplerFactory = new FFmpegResampler.FFmpegResamplerFactory();

    private final Map<Plugin, MusicRegistration> registrations = new LinkedHashMap<>();
    private final Map<AudioChannel, Play> playingTracks = new LinkedHashMap<>();
    private final Map<AudioChannel, Playlist> playlists = new LinkedHashMap<>();
    private final Map<AudioChannel, BlockingQueue<Pair<UserAssociation, Track>>> queues = new LinkedHashMap<>();

    private final ExecutorService cacheExecutor;
    private final Map<Track, Future<Repository.Resource>> downloads = new LinkedHashMap<>();

    public Music(Plugin plugin, Database database, MusicManager manager, Audio audio) {
        this.plugin = plugin;
        this.database = database;
        this.musicManager = manager;
        this.localTrackSource = new DatabaseTrackSource(this);
        this.audio = audio;

        int concurrentDownloads = Integer.parseInt(plugin.getProperty("concurrentDownloads", "-1"));
        if (concurrentDownloads > 0)
            this.cacheExecutor = Executors.newFixedThreadPool(concurrentDownloads, new ThreadFactoryBuilder().setPriority(Thread.MIN_PRIORITY).build());
        else if (concurrentDownloads == -1)
            this.cacheExecutor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setPriority(Thread.MIN_PRIORITY).build());
        else
            this.cacheExecutor = null;

        // Default implementation
        createRegistration(plugin, builder -> {
            builder.registerRepository(FileRepository.class, FileRepository::new);
            builder.registerRepository(NullRepository.class, NullRepository::new);
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

    public Audio getAudio() {
        return audio;
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

    public Collection<Playlist> getPlaylists() {
        return Collections.unmodifiableCollection(playlists.values());
    }

    public Stream<Playlist> getPlaylists(PlatformUser user) {
        return getPlaylists().stream()
                .filter(x -> x.getUser().getPlatformUser() != null)
                .filter(x -> x.getUser().getPlatformUser().equals(user));
    }

    public Stream<Playlist> getPlaylists(User user) {
        return getPlaylists().stream()
                .filter(x -> x.getUser().getUser() != null)
                .filter(x -> x.getUser().getUser().equals(user));
    }

    public Stream<Playlist> getPlaylists(Platform platform) {
        return getPlaylists().stream()
                .filter(x -> x.getChannel().getPlatform().equals(platform));
    }

    public Playlist getPlaylist(CommandSender sender) {
        if (sender == null) return null;
        return playlists.get(audio.getChannel(sender));
    }

    public Playlist getPlaylist(Conversation conversation) {
        if (conversation == null) return null;
        return playlists.get(audio.getChannel(conversation));
    }

    public Playlist getPlaylist(Chat chat) {
        if (chat == null) return null;
        return playlists.get(audio.getChannel(chat));
    }

    public Playlist getPlaylist(AudioChannel channel) {
        if (channel == null) return null;
        return playlists.get(channel);
    }

    public BlockingQueue<Pair<UserAssociation, Track>> getQueue(CommandSender sender) {
        if (sender == null) return null;
        return getQueue(sender.getConversation());
    }

    public BlockingQueue<Pair<UserAssociation, Track>> getQueue(Conversation conversation) {
        if (conversation == null) return null;
        return getQueue(conversation.getChat());
    }

    public BlockingQueue<Pair<UserAssociation, Track>> getQueue(Chat chat) {
        if (chat == null) return null;
        return getQueue(audio.getChannel(chat));
    }

    public BlockingQueue<Pair<UserAssociation, Track>> getQueue(AudioChannel channel) {
        if (channel == null) return null;
        synchronized (queues) {
            return queues.computeIfAbsent(channel,
                    (ignored) -> new ArrayBlockingQueue<>(channel.getMaximumQueueSize()));
        }
    }

    /**
     * Gets the last (or currently playing) track on the conversation provided.
     * @param conversation conversation.
     * @return Track instance if one was recently played, or null otherwise.
     */
    public Track getPlayedTrack(Conversation conversation) {
        AudioChannel channel = audio.getChannel(conversation);

        if (channel != null) {
            Play association = playingTracks.get(channel);
            if (association != null) {
                return association.getTrack();
            }
        }

        return musicManager.getLastPlayed(conversation);
    }

    public TrackSource.Result findRemoteTrack(Community community, URL url) throws IllegalArgumentException {
        Objects.requireNonNull(community);

        return registrations.values().stream()
                .flatMap(registration -> registration.getTrackSources().stream())
                .map(trackSource -> {
                    try {
                        return trackSource.find(community, url);
                    } catch (TrackDownloadException e) {
                        throw new IllegalArgumentException(e.getMessage());
                    }
                })
                .filter(Objects::nonNull)
                .sorted(
                        new Comparator<TrackSource.Result>() {
                            @Override
                            public int compare(TrackSource.Result a, TrackSource.Result b) {
                                return Integer.compare(a.getPriority().getOrdinal(), b.getPriority().getOrdinal());
                            }
                        }.reversed().thenComparingInt(x -> x.isLocal() ? 0 : 1)
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "url",
                                new Exception("track not found: " + url.toExternalForm())
                        )
                );
    }

    public TrackSource.Result findLocalTrack(Community community, URL url) throws IOException, TrackDownloadException {
        Objects.requireNonNull(community);

        TrackSource.Result databaseResult = getLocalTrackSource().find(community, url);
        if (databaseResult != null) {
            Repository.Resource resource = databaseResult.get();
            if (resource.exists())
                return databaseResult;
            else {
                if (!(resource.getRepository() instanceof NullRepository))
                    getPlugin().getLogger().log(Level.WARNING, "Database result exists for track " + databaseResult.getUrl() + ", but no resource " +
                                    "exists in repository " + resource.getRepository().getClass().getName() + ": " + resource.toString());
                return null;
            }
        } else return null;
    }

    public TrackSource.Result findTrack(Community community, URL url) throws IOException, TrackDownloadException {
        TrackSource.Result result = findLocalTrack(community, url);
        if (result == null)
            result = findRemoteTrack(community, url);
        return result;
    }

    public Community getCommunity(Chat chat) {
        if (chat == null) return null;
        io.manebot.chat.Community platformCommunity = chat.getCommunity();
        if (platformCommunity == null) return null;
        return musicManager.getCommunityByPlatformSpecificId((Platform) chat.getPlatform(), platformCommunity.getId());
    }

    public Community getCommunity(Conversation conversation) {
        if (conversation == null) return null;
        return getCommunity(conversation.getChat());
    }

    public Community getCommunity(CommandSender sender) {
        return getCommunity(sender.getChat());
    }

    public int stop(UserAssociation userAssociation, AudioChannel channel) {
        List<AudioPlayer> audioPlayers = channel.getPlayers();
        boolean override = Permission.hasPermission("audio.stop.all");

        int stopped = 0;

        // Stop playlist(s)
        Playlist playlist = getPlaylist(channel);
        if (playlist != null) {
            int playing = playlist.getPlayers().size();
            if ((playlist.getUser() == null ||
                    playlist.getUser().getUser().equals(userAssociation.getUser()) ||
                    override) && playlist.setRunning(false)) {
                stopped += playing;
            }
        }

        // Stop players
        int skipped = 0;
        for (AudioPlayer player : audioPlayers) {
            if (!player.isPlaying()) continue;

            if (player.getOwner() == null || player.getOwner().equals(userAssociation.getUser()) || override) {
                if (player.kill()) stopped++;
            } else {
                skipped ++;
            }
        }

        return stopped;
    }

    public Playlist startPlaylist(UserAssociation userAssociation,
                                  Conversation conversation,
                                  Consumer<Playlist.Builder> consumer) {
        Objects.requireNonNull(userAssociation);

        Community community = getCommunity(conversation);
        if (community == null)
            throw new IllegalArgumentException("There is no music community associated with this conversation.");

        AudioChannel channel = audio.getChannel(conversation);
        if (channel == null)
            throw new IllegalArgumentException("There is no audio channel associated with this conversation.");

        PlaylistBuilder builder = new PlaylistBuilder(
                community,
                conversation,
                channel
        );

        builder.setUser(userAssociation);

        consumer.accept(builder);

        builder.addListener(new Playlist.Listener() {
            @Override
            public void onStarted(Playlist playlist) {
                getPlugin().getBot().getEventDispatcher().execute(new PlaylistStartedEvent(this, Music.this, playlist));
                
                if (playlist.getUser() != null)
                    playlist.getConversation().getChat().sendFormattedMessage(
                            textBuilder ->
                                    textBuilder.append(
                                            playlist.getUser().getUser().getDisplayName(),
                                            EnumSet.of(TextStyle.BOLD)
                                    ).append(" has started a playlist in this conversation.")
                    );
            }

            @Override
            public void onTrackChanged(Playlist playlist, Track track) {
                getPlugin().getBot().getEventDispatcher().execute(new PlaylistTrackChangedEvent(this, Music.this, playlist, track));
                
                playlist.getConversation().getChat().sendFormattedMessage(
                        textBuilder -> textBuilder.append("(Playing \"" + track.getName() + "\")")
                );
            }

            @Override
            public void onTransferred(Playlist playlist, UserAssociation a, UserAssociation b) {
                getPlugin().getBot().getEventDispatcher().execute(new PlaylistTransferredEvent(this, Music.this, playlist, a, b));
                
                if (a != null && b != null)
                    playlist.getConversation().getChat().sendFormattedMessage(
                            textBuilder ->
                                    textBuilder.append(
                                            a.getUser().getDisplayName() + "'s",
                                            EnumSet.of(TextStyle.BOLD)
                                    ).append(" playlist has ben transferred to ").append(
                                            a.getUser().getDisplayName(),
                                            EnumSet.of(TextStyle.BOLD)
                                    ).append(".")
                    );
            }

            @Override
            public void onStopped(Playlist playlist) {
                playlists.remove(playlist.getChannel(), playlist);
    
                getPlugin().getBot().getEventDispatcher().execute(new PlaylistEndedEvent(this, Music.this, playlist));
                
                if (playlist.getUser() != null)
                    playlist.getConversation().getChat().sendFormattedMessage(
                            textBuilder ->
                                    textBuilder.append(
                                            playlist.getUser().getUser().getDisplayName() + "'s",
                                            EnumSet.of(TextStyle.BOLD)
                                    ).append(" playlist has ended.")
                    );
            }
        });

        Playlist playlist = builder.create();

        try (AudioChannel.Ownership ignored = channel.obtainChannel(userAssociation)) {
            stop(userAssociation, channel);

            if (getPlaylist(channel) != null)
                throw new CommandAccessException("There is another playlist running on this channel.");

            playlist.setRunning(true);

            playlists.put(channel, playlist);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return playlist;
    }

    public Play play(UserAssociation user, Conversation conversation, Consumer<Play.Builder> consumer)
            throws IllegalArgumentException, IOException {
        Community community = getCommunity(conversation);
        if (community == null)
            throw new IllegalArgumentException("There is no music community associated with this conversation.");

        return play(user, conversation, community, consumer);
    }

    public Play play(UserAssociation user, Conversation conversation, Community community,
                     Consumer<Play.Builder> consumer)
            throws IllegalArgumentException, IOException {
        AudioChannel channel = audio.getChannel(conversation);
        if (channel == null)
            throw new IllegalArgumentException("There is no audio channel associated with this conversation.");

        PlayBuilder builder = new PlayBuilder(user, community, channel, conversation);
        consumer.accept(builder);
        return builder.play();
    }

    @Override
    public void load(Plugin.Future plugin) {
        
    }

    @Override
    public void unload(Plugin.Future plugin) {
        new ArrayList<>(getPlaylists()).forEach(playlist -> playlist.setRunning(false));

        new ArrayList<>(playingTracks.values()).forEach(association -> {
            try {
                association.getPlayer().kill();
            } catch (Exception e) {
                // ignore
            }
        });
    }

    public AudioProtocol getProtocol() {
        return protocol;
    }

    public ResamplerFactory getResamplerFactory() {
        return resamplerFactory;
    }

    private class PlayBuilder implements Play.Builder {
        private final UserAssociation userAssociation;
        private final Community community;
        private final AudioChannel channel;
        private final Conversation conversation;
        private TrackSource.Result result = null;
        private BiConsumer<Track, Track> fadeOut = (track, nextTrack) -> {};

        /**
         * If the track should be cached in the repository it is retrieved on.
         * This is usually the repository associated with the community, which is in turn associated with the
         * conversation where the download is taking place. This is effectively the main flag that allows track
         * caching in the bot.
         */
        private boolean caching = true;

        /**
         * If the track can be downloaded from its remote URI as part of the retrieval process.
         */
        private boolean canDownload = true;

        private Play.Behavior behavior = Play.Behavior.EXCLUSIVE;

        private PlayBuilder(UserAssociation userAssociation,
                            Community community,
                            AudioChannel channel,
                            Conversation conversation) {
            this.userAssociation = userAssociation;
            this.community = community;
            this.channel = channel;
            this.conversation = conversation;
        }

        @Override
        public UserAssociation getUser() {
            return userAssociation;
        }

        @Override
        public Community getCommunity() {
            return community;
        }

        @Override
        public Conversation getConversation() {
            return conversation;
        }

        @Override
        public AudioChannel getChannel() {
            return channel;
        }

        @Override
        public Play.Builder setTrack(Function<Play.TrackSelector, TrackSource.Result> selector)
                throws IllegalArgumentException {
            this.result = Objects.requireNonNull(selector).apply(new TrackSelector(getCommunity()));
            return this;
        }

        @Override
        public Play.Builder setCanDownload(boolean download) {
            this.canDownload = download;
            return this;
        }

        @Override
        public Play.Builder setShouldCache(boolean caching) {
            this.caching = caching;
            return this;
        }

        @Override
        public Play.Builder setBehavior(Play.Behavior behavior) {
            this.behavior = Objects.requireNonNull(behavior);
            return this;
        }

        @Override
        public Play.Builder setFadeOut(BiConsumer<Track, Track> fadeOut) {
            this.fadeOut = fadeOut;
            return this;
        }

        private Play play() throws IOException {
            Objects.requireNonNull(userAssociation, "User association is required");
            Objects.requireNonNull(result, "Track is required");

            // Get the track. This may actually create a track, if needed.
            Track track = result.getTrack();

            // Get the resource associated with this track. The resource is the local/cached copy of the track, which in
            // most cases won't exist. If it does exist, it is usually on a specific file directory, NFS share, etc.
            Repository.Resource resource = result.get();

            // Open a direct stream to the resource.
            // This is a somewhat complicated process. We must check to see if the resource exists. If it doesn't exist on
            // the Repository associated with the "community" parameter, we should cache it only if that Repository supports
            // writing to this Track's associated resource. If it does exist, we shouldn't save it to the cache twice.
            AudioProtocol protocol = getProtocol();
            AudioProvider provider;

            final Future<Repository.Resource> cacheFuture;

            if (result.isLocal() && resource.exists()) {
                provider = protocol.openProvider(resource.openRead());
                cacheFuture = null;
            } else if (!canDownload)
                throw new IllegalArgumentException("cannot stream track: streaming/downloading was not allowed");
            else {
                provider = result.openProvider(protocol);

                // Attempt to cache the track as well.
                if (caching && resource.canWrite() &&
                        (track.getLength() != null && track.getLength() > 0) && cacheExecutor != null) {
                    cacheFuture = cacheAsync(resource, track);
                } else {
                    cacheFuture = null;
                }
            }

            try {
                // Resample on an as-needed basis
                if (provider.getChannels() != getChannel().getMixer().getAudioChannels() ||
                        provider.getSampleRate() != getChannel().getMixer().getAudioSampleRate()) {
                    provider = new ResampledAudioProvider(
                            provider,
                            getChannel().getMixer().getBufferSize(),
                            resamplerFactory.create(
                                    provider.getFormat(),
                                    getChannel().getMixer().getAudioFormat(),
                                    getChannel().getMixer().getBufferSize()
                            )
                    );
                }

                // Define a uniform constructor routine to create an instance of a Play.
                Function<AudioPlayer, Play> playConstructor = (player) ->
                        new Play(Music.this, track, channel, conversation, player, behavior, player != null, cacheFuture);

                // Observing the behavior of the requested playback, handle playing the Track now or at a future time.
                try (AudioChannel.Ownership ignored = channel.obtainChannel(userAssociation)) {
                    if (behavior == null) {
                        behavior = Play.Behavior.QUEUED;
                    }

                    if (behavior == Play.Behavior.EXCLUSIVE) {
                        stop(userAssociation, channel);
                    }

                    if (behavior == Play.Behavior.EXCLUSIVE || behavior == Play.Behavior.PASSIVE ||
                            (behavior == Play.Behavior.QUEUED && channel.isIdle())) {
                        // Any case where we can immediately begin playback
                        // Create an audio player based on a provider and the track to play, and associate its future
                        // back to the Music singleton so we can track when it ends in this class.
                        AudioPlayer player = createPlayer(provider, track);
                        Play play = playConstructor.apply(player);
                        player.getFuture().thenRun(() -> playingTracks.remove(channel, play));

                        channel.addPlayer(player);
                        playingTracks.put(channel, play);

                        return play;
                    } else if (behavior == Play.Behavior.QUEUED) {
                        // Any case where we should instead enqueue playback of this track
                        getQueue(channel).add(new Pair<>(userAssociation, track));
                        return playConstructor.apply(null);
                    } else {
                        throw new UnsupportedOperationException(behavior.name());
                    }
                }
            } catch (IllegalArgumentException | IllegalStateException exposed) {
                throw exposed;
            } catch (Throwable e) {
                // Prevent leak:
                try {
                    provider.close();
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }

                throw new IOException("Problem playing track", e);
            }
        }

        private Future<Repository.Resource> cacheAsync(Repository.Resource resource, Track track) {
            synchronized (downloads) {
                Future<Repository.Resource> future = downloads.get(track);
                if (future == null) {
                    if (cacheExecutor == null) {
                        throw new NullPointerException("cacheExecutor");
                    }

                    future = cacheExecutor.submit(() -> cache(resource, track));
                    downloads.put(track, future);
                    return future;
                } else {
                    return future;
                }
            }
        }

        private Repository.Resource cache(Repository.Resource resource, Track track) throws IOException {
            try {
                plugin.getLogger().fine(track.getUrlString() + ": transcoding to " +
                        resource.getRepository().getClass() + "...");

                try (AudioProvider cachedAudioProvider = result.openProvider(protocol)) {
                    AudioDownloadFormat downloadFormat = resource.getRepository().getDownloadFormat();
                    AudioFormat targetFormat = AudioFormat.from(cachedAudioProvider.getFormat());

                    try (Resampler resampler = protocol.openResampler(targetFormat, downloadFormat.getAudioFormat(),
                            transcodeBufferSize)) {
                        try (AudioConsumer cachedAudioConsumer =
                                     protocol.openConsumer(resource.openWrite(), downloadFormat)) {
                            // Set up resampling buffers
                            float[] in_buffer = new float[transcodeBufferSize];
                            int maxOutputBufferSize = (int) Math.ceil(transcodeBufferSize * (1d / resampler.getScale()));
                            float[] out_buffer = new float[maxOutputBufferSize];

                            boolean eof = false;

                            long resampled = 0;
                            int len;

                            // Copy the samples to the consumer (file, NAS object, etc.) we're flushing to
                            try {
                                while ((len = cachedAudioProvider.read(in_buffer, 0, in_buffer.length)) > 0) {
                                    len = resampler.resample(in_buffer, len, out_buffer, out_buffer.length);
                                    cachedAudioConsumer.write(out_buffer, len);
                                    resampled += len;
                                }
                            } catch (EOFException ignored) {
                                // Ignore; EOFs are expected in the audio sample stream
                                eof = true;
                            }

                            // Flush the resampler to get any remaining samples out of the buffer
                            len = resampler.flush(out_buffer, out_buffer.length);
                            if (len > 0)
                                cachedAudioConsumer.write(out_buffer, len);

                            // Look for the special case where we break out of the while loop when len <= 0
                            if (!eof) {
                                throw new IOException("resampled " + resampled +
                                        " samples but did not encounter expected EOF.");
                            }
                        }
                    }
                }

                TrackRepository trackRepository = resource.getRepository().getTrackRepository();
                if (trackRepository.getFile(resource.getUUID()) == null)
                    trackRepository.createFile(trackRepository, resource.getUUID(), resource.getFormat());

                Event event = new TrackDownloadedEvent(this, Music.this, track, resource);
                getPlugin().getBot().getEventDispatcher().execute(event);

                plugin.getLogger().fine(track.getUrlString() + ": transcode to " +
                        resource.getRepository().getClass() + " completed");
            } catch (Exception e) {
                String message = track.getUrlString() + ": problem transcoding track to repository: " +
                        resource.getRepository().getClass();

                // Attempt to delete the target resource as we didn't succeed in caching it.
                try {
                    resource.delete();
                } catch (Exception ex) {
                    // This is a problem, but suppress it in favor of the primary cause.
                    e.addSuppressed(ex);
                }

                // Caching occurs on a thread that doesn't handle exceptions -- go ahead and warn it here
                plugin.getLogger().log(Level.WARNING, message, e);

                throw new IOException(message, e);
            } finally {
                synchronized (downloads) {
                    downloads.remove(track);
                }
            }

            return resource;
        }

        private AudioPlayer createPlayer(AudioProvider provider, Track track) {
            return new TransitionedAudioPlayer(
                    AudioPlayer.Type.BLOCKING,
                    userAssociation.getUser(),
                    provider,
                    track.getLength() == null ? Double.MAX_VALUE : track.getLength(),
                    track.getLength() == null ? 3D : Math.min(track.getLength() / 4D, 3D),
                    new TransitionedAudioPlayer.Callback() {
                        @Override
                        public void onFadeIn() {
                            Event event = new TrackStartedEvent(this, Music.this, track);
                            getPlugin().getBot().getEventDispatcher().execute(event);
                        }

                        @Override
                        public void onFadeOut() {
                            // Check queue
                            Pair<UserAssociation, Track> queuedPlay;

                            while ((queuedPlay = getQueue(channel).poll()) != null) {
                                UserAssociation userAssociation = queuedPlay.getLeft();
                                Track track = queuedPlay.getRight();

                                try {
                                    Music.this.play(userAssociation, conversation, community,
                                            (builder) -> builder
                                                    .setBehavior(Play.Behavior.PASSIVE)
                                                    .setFadeOut(fadeOut) // Chain fade-out so playlists can pick back up
                                                    .setTrack(trackSelector -> trackSelector.find(track))
                                    );

                                    break;
                                } catch (IOException e) {
                                    String message = "Couldn't play queued track \"" + track.getName() + "\"";
                                    Logger.getGlobal().log(Level.WARNING, message, e);
                                    conversation.getChat().sendMessage("(" + message + ")");
                                }
                            }

                            Track nextTrack = queuedPlay != null ? queuedPlay.getRight() : null;

                            if (fadeOut != null) {
                                fadeOut.accept(track, nextTrack);
                            }

                            Event event = new TrackFadeEvent(this, Music.this, track, nextTrack);
                            getPlugin().getBot().getEventDispatcher().execute(event);
                        }

                        @Override
                        public void onFinished(double timePlayedInSeconds) {
                            double end = System.currentTimeMillis() / 1000D;

                            if (userAssociation.getUser().getType() == UserType.ANONYMOUS) return;

                            // clamp time played
                            if (track.getLength() != null)
                                timePlayedInSeconds = Math.max(
                                        0D,
                                        Math.min(timePlayedInSeconds, track.getLength())
                                );

                            double start = end - timePlayedInSeconds;
                            track.addPlayAsync(conversation, userAssociation.getUser(), start, end);

                            Event event = new TrackFinshedEvent(this, Music.this, track, timePlayedInSeconds);
                            getPlugin().getBot().getEventDispatcher().execute(event);
                        }
                    }
            );
        }
    }

    private class TrackSelector implements Play.TrackSelector {
        private final Community community;

        private TrackSelector(Community community) {
            this.community = community;
        }

        @Override
        public TrackSource.Result find(URL url) throws IllegalArgumentException {
            try {
                return Music.this.findTrack(community, url);
            } catch (IOException | TrackDownloadException e) {
                throw new IllegalArgumentException("Problem finding track " + url.toExternalForm(), e);
            }
        }

        @Override
        public TrackSource.Result find(SearchResult<Track> searchResult) throws IllegalArgumentException {
            try {
                return Music.this.findTrack(community, searchResult.getResults().stream()
                        .reduce((a, b) -> {
                            throw new IllegalArgumentException("more than 1 result found");
                        })
                        .map(Track::toURL)
                        .orElseThrow(() -> new IllegalArgumentException("no results found")));
            } catch (IOException | TrackDownloadException e) {
                throw new IllegalArgumentException("Problem finding track", e);
            }
        }

        @Override
        public TrackSource.Result findFirst(SearchResult<Track> searchResult) throws IllegalArgumentException {
            try {
                return Music.this.findTrack(community, searchResult.getResults().stream()
                        .map(Track::toURL)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("no results found")));
            } catch (IOException | TrackDownloadException e) {
                throw new IllegalArgumentException("Problem finding track", e);
            }
        }

        @Override
        public TrackSource.Result find(Track track) throws IllegalArgumentException {
            return find(track.toURL());
        }
    }

    private class PlaylistBuilder implements Playlist.Builder {
        private final Community community;
        private final Conversation conversation;
        private final AudioChannel channel;
        private final List<Playlist.Listener> listeners = new LinkedList<>();

        private UserAssociation user;
        private TrackQueue queue = null;

        private PlaylistBuilder(Community community, Conversation conversation, AudioChannel channel) {
            this.community = community;
            this.conversation = conversation;
            this.channel = channel;
        }

        @Override
        public Audio getAudio() {
            return getMusic().getAudio();
        }

        @Override
        public Music getMusic() {
            return Music.this;
        }

        @Override
        public Community getCommunity() {
            return community;
        }

        @Override
        public Conversation getConversation() {
            return conversation;
        }

        @Override
        public AudioChannel getChannel() {
            return channel;
        }

        @Override
        public UserAssociation getUser() {
            return user;
        }

        @Override
        public Playlist.Builder setUser(UserAssociation user) {
            this.user = user;
            return this;
        }

        @Override
        public Playlist.Builder setQueue(TrackQueue queue) {
            this.queue = queue;
            return this;
        }

        @Override
        public Playlist.Builder setQueue(Function<Playlist.QueueSelector, TrackQueue> function) {
            return setQueue(function.apply(new QueueSelector(community)));
        }

        @Override
        public Playlist.Builder addListener(Playlist.Listener listener) {
            listeners.add(listener);
            return this;
        }

        private Playlist create() {
            return new DefaultPlaylist(
                    getMusic(),
                    user,
                    getCommunity(),
                    getConversation(),
                    getChannel(),
                    Objects.requireNonNull(queue),
                    listeners
            );
        }
    }

    private class QueueSelector implements Playlist.QueueSelector {
        private final Community community;

        private QueueSelector(Community community) {
            this.community = community;
        }

        @Override
        public TrackQueue from(Iterable<Track> queue, boolean loop) {
            if (loop) {
                return new LoopedTrackQueue(
                        StreamSupport.stream(queue.spliterator(), false).collect(Collectors.toList())
                );
            } else {
                return new DefaultTrackQueue(Queues.newArrayDeque(queue));
            }
        }

        @Override
        public TrackQueue from(Track track, boolean loop) {
            if (loop) {
                return new LoopedTrackQueue(track);
            } else {
                return new DefaultTrackQueue(Queues.newArrayDeque(Collections.singletonList(track)));
            }
        }

        @Override
        public TrackQueue from(Search search) {
            return new SearchedTrackQueue(database, community, search);
        }
    }
}
