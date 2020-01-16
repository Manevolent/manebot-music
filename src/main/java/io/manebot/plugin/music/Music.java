package io.manebot.plugin.music;

import com.github.manevolent.ffmpeg4j.*;

import com.github.manevolent.ffmpeg4j.filter.audio.*;
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
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.MusicManager;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.plugin.music.playlist.*;
import io.manebot.plugin.music.repository.FileRepository;
import io.manebot.plugin.music.repository.NullRepository;
import io.manebot.plugin.music.repository.Repository;
import io.manebot.plugin.music.source.*;
import io.manebot.security.Permission;
import io.manebot.user.User;
import io.manebot.user.UserAssociation;
import io.manebot.user.UserType;
import org.apache.commons.io.*;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Music implements PluginReference {
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
                                    "exists for repository " + resource.getRepository().getClass().getName() + ".");
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
                playlist.getConversation().getChat().sendFormattedMessage(
                        textBuilder -> textBuilder.append("(Playing \"" + track.getName() + "\")")
                );
            }

            @Override
            public void onTransferred(Playlist playlist, UserAssociation a, UserAssociation b) {
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
                if (playlist.getUser() != null)
                    playlist.getConversation().getChat().sendFormattedMessage(
                            textBuilder ->
                                    textBuilder.append(
                                            playlist.getUser().getUser().getDisplayName() + "'s",
                                            EnumSet.of(TextStyle.BOLD)
                                    ).append(" playlist has ended.")
                    );

                playlists.remove(playlist.getChannel(), playlist);
            }
        });

        Playlist playlist = builder.create();

        try (AudioChannel.Ownership ownership = channel.obtainChannel(userAssociation)) {
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
            throws IllegalArgumentException, IOException, FFmpegException {
        Community community = getCommunity(conversation);
        if (community == null)
            throw new IllegalArgumentException("There is no music community associated with this conversation.");

        return play(user, conversation, community, consumer);
    }

    public Play play(UserAssociation user, Conversation conversation, Community community,
                     Consumer<Play.Builder> consumer)
            throws IllegalArgumentException, IOException, FFmpegException {
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
        private Consumer<Track> fadeOut = (track) -> {};

        /**
         * If the track should be cached in the repository it is retrieved on.
         * This is usually the repository associated with the community, which is in turn associated with the conversation where the download is taking place.
         * This is effectively the main flag that allows track caching in the bot.
         */
        private boolean caching = true;

        /**
         * If the track can be downloaded from its remote URI as part of the retrieval process.
         */
        private boolean downloading = true;

        /**
         * If the play event should be exclusive of the audio channel (i.e. stop other playing tracks).
         */
        private boolean exclusive = true;

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
            this.result = selector.apply(new TrackSelector(getCommunity()));
            return this;
        }

        @Override
        public Play.Builder setDownloading(boolean downloading) {
            this.downloading = downloading;
            return this;
        }

        @Override
        public Play.Builder setCaching(boolean caching) {
            this.caching = caching;
            return this;
        }

        @Override
        public Play.Builder setExclusive(boolean exclusive) {
            this.exclusive = exclusive;
            return this;
        }

        @Override
        public Play.Builder setFadeOut(Consumer<Track> fadeOut) {
            this.fadeOut = fadeOut;
            return this;
        }

        private Play play() throws IOException {
            Objects.requireNonNull(userAssociation);
            Objects.requireNonNull(result);

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
            if (result.isLocal() && resource.exists())
                provider = protocol.openProvider(resource.openRead());
            else if (!downloading)
                throw new IllegalStateException("cannot stream track: streaming/downloading was not allowed");
            else {
                provider = result.openProvider(protocol);

                // Attempt to cache the track as well.
                if (caching && resource.canWrite() && (track.getLength() != null && track.getLength() > 0) && cacheExecutor != null) {
                    Callable<Repository.Resource> callable = () -> {
                        try {
                            int bufferSize = 1024;
                            
                            plugin.getLogger().info(track.getUrlString() + ": transcoding to " + resource.getRepository().getClass() + "...");
                            
                            try (AudioProvider cachedAudioProvider = result.openProvider(protocol)) {
                                AudioDownloadFormat downloadFormat = resource.getRepository().getDownloadFormat();
                                try (Resampler resampler = protocol.openResampler(AudioFormat.from(cachedAudioProvider.getFormat()),
                                                downloadFormat.getAudioFormat(), bufferSize)) {
                                    try (AudioConsumer cachedAudioConsumer = protocol.openConsumer(resource.openWrite(), downloadFormat)) {
                                        float[] in_buffer = new float[bufferSize];
                                        float[] out_buffer = new float[(int) Math.ceil(bufferSize * (1 / resampler.getScale()))];
                                        int len;
                                        try {
                                            while ((len = cachedAudioProvider.read(in_buffer, 0, in_buffer.length)) > 0) {
                                                //len = resampler.resample(in_buffer, len, out_buffer, out_buffer.length);
                                                cachedAudioConsumer.write(in_buffer, len);
                                                Arrays.fill(in_buffer, 0F);
                                            }
                                        } catch (EOFException ignored) {
                                            // ignore, this is normal
                                        }
                                        //len = resampler.flush(out_buffer, out_buffer.length);
                                        //if (len > 0)
                                        //    cachedAudioConsumer.write(out_buffer, len);
                                    }
                                }
                            }
                            
                            plugin.getLogger().info(track.getUrlString() + ": transcode to " + resource.getRepository().getClass() + " completed");
                        } catch (Exception e) {
                            String message = track.getUrlString() + ": problem transcoding track to repository: " + resource.getRepository().getClass();

                            plugin.getLogger().log(Level.WARNING, message, e);
                            
                            try {
                                resource.delete();
                            } catch (Exception ex) {
                                e.addSuppressed(ex);
                            }
                            
                            throw new IOException(message, e);
                        } finally {
                            synchronized (downloads) {
                                downloads.remove(track);
                            }
                        }

                        return resource;
                    };

                    synchronized (downloads) {
                        if (!downloads.containsKey(track)) {
                            downloads.put(track, cacheExecutor.submit(callable));
                        }
                    }
                }
            }

            try {
                // resample on an as-needed basis
                if (provider.getChannels() != getChannel().getMixer().getAudioChannels() ||
                        provider.getSampleRate() != getChannel().getMixer().getAudioSampleRate())
                    provider = new ResampledAudioProvider(
                            provider,
                            getChannel().getMixer().getBufferSize(),
                            resamplerFactory.create(
                                    provider.getFormat(),
                                    getChannel().getMixer().getAudioFormat(),
                                    getChannel().getMixer().getBufferSize()
                            )
                    );

                AudioPlayer player = new TransitionedAudioPlayer(
                        AudioPlayer.Type.BLOCKING,
                        userAssociation.getUser(),
                        provider,
                        track.getLength() == null ? Double.MAX_VALUE : track.getLength(),
                        track.getLength() == null ? 3D : Math.min(track.getLength() / 4D, 3D),
                        new TransitionedAudioPlayer.Callback() {
                            @Override
                            public void onFadeIn() {
                                // do nothing
                            }

                            @Override
                            public void onFadeOut() {
                                fadeOut.accept(track);
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
                            }
                        }
                );

                final Play play = new Play(Music.this, track, channel, conversation, player);

                try (AudioChannel.Ownership ownership = channel.obtainChannel(userAssociation)) {
                    if (exclusive) stop(userAssociation, channel);

                    if (channel.addPlayer(player)) {
                        playingTracks.put(channel, play);
                        player.getFuture().thenRun(() -> playingTracks.remove(channel, play));
                    } else throw new IllegalStateException();
                }

                return play;
            } catch (Throwable e) {
                // prevent leak:
                try {
                    provider.close();
                } catch (Exception ex) {
                    // ignore
                }

                throw new IOException("Problem playing track", e);
            }
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
        public TrackSource.Result find(SearchResult<Track> searchResult)
                throws IllegalStateException, NoSuchElementException {
            try {
                return Music.this.findTrack(community, searchResult.getResults().stream()
                        .reduce((a, b) -> {
                            throw new IllegalStateException("more than 1 result found");
                        })
                        .map(Track::toURL)
                        .orElseThrow(() -> new NoSuchElementException("no results found")));
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
