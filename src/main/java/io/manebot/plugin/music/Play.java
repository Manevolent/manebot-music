package io.manebot.plugin.music;

import io.manebot.conversation.Conversation;
import io.manebot.database.search.SearchResult;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.player.AudioPlayer;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.plugin.music.repository.Repository;
import io.manebot.plugin.music.source.TrackSource;
import io.manebot.user.UserAssociation;

import java.net.URL;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class Play {
    private final Music music;
    private final Track track;
    private final AudioChannel channel;
    private final Conversation conversation;
    private final AudioPlayer player;
    private final Behavior behavior;
    private final boolean queued;
    private final Future<Repository.Resource> cacheFuture;

    Play(Music music,
         Track track,
         AudioChannel channel,
         Conversation conversation,
         AudioPlayer player,
         Behavior behavior,
         boolean queued,
         Future<Repository.Resource> cacheFuture) {
        this.music = music;
        this.track = track;
        this.channel = channel;
        this.conversation = conversation;
        this.player = player;
        this.behavior = behavior;
        this.queued = queued;
        this.cacheFuture = cacheFuture;
    }

    public Music getMusic() {
        return music;
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

    public Conversation getConversation() {
        return conversation;
    }

    public Behavior getBehavior() {
        return behavior;
    }

    public boolean wasQueued() {
        return queued;
    }

    /**
     * Gets the future associated with caching this track.
     * @return cache future.
     */
    public Future<Repository.Resource> getCacheFuture() {
        return cacheFuture;
    }

    public interface Builder {

        /**
         * Gets the user associated with this playback.
         * @return user instance.
         */
        UserAssociation getUser();

        /**
         * Gets the community associated with this playback.
         * @return community instance.
         */
        Community getCommunity();

        /**
         * Gets the conversation this play will source from.
         * @return conversation for playback.
         */
        Conversation getConversation();

        /**
         * Gets the channel this play will activate on.
         * @return planned audio channel for playback.
         */
        AudioChannel getChannel();

        /**
         * Sets the track this playback will be based on.
         * @param selector selector used to select the track to play.
         * @return Builder for continuance.
         */
        Builder setTrack(Function<TrackSelector, TrackSource.Result> selector) throws IllegalArgumentException;

        /**
         * Changes the download behavior of the track playback. When true, new downloads are allowed for the playback.
         * @param download true if downloads should be allowed, false otherwise.
         * @return Builder for continuance.
         */
        Builder setCanDownload(boolean download);

        /**
         * Sets if the Music engine should attempt to cache this track.
         * @param caching true if caching should be allowed, false otherwise.
         * @return Builder for continuance.
         */
        Builder setShouldCache(boolean caching);

        /**
         * Sets the channel ownership behavior of this attempted play.
         * @param behavior behavior to set for this play when attempting to enqueue it to the channel.
         * @return Builder for continuance.
         */
        Builder setBehavior(Behavior behavior);

        /**
         * Sets the "fade out" listener, fired when the track fades out.  This BiConsumer is used by playlists to listen
         * to track fade-out.  If/when a track enters a "fade-out" phase, this listener is fired.  If the Music engine
         * determines the Channel has a track to fade into, a Playlist is super-ceded: the second argument in the
         * BiConsumer will be set to the enqueued track, and the Playlist is expected to yield control to the Music
         * engine.  When the final track has ended in the queue chain, this method will be called.  This is because
         * over-taking enqueued tracks inherit the fade out BiConsumer when they are chained by the Music engine.  In
         * this case, the first argument of the BiConsumer will be the queued track, and the second argument will be
         * null indicating no tracks were played next.
         *
         * This method is entirely analogous and equivalent in function to the TrackFadeOutEvent.  The provided
         * BiConsumer, however, will always be called first before any events are fired in the event system.
         *
         * @param fadeOut fade out callback BiConsumer, offering two arguments: Track played, next Track playing.
         * @return Builder for continuance.
         */
        Builder setFadeOut(BiConsumer<Track, Track> fadeOut);

    }

    public interface TrackSelector {
        TrackSource.Result find(URL url) throws IllegalArgumentException;
        TrackSource.Result find(SearchResult<Track> searchResult) throws IllegalArgumentException;
        TrackSource.Result findFirst(SearchResult<Track> searchResult) throws IllegalArgumentException;
        TrackSource.Result find(Track track) throws IllegalArgumentException;
    }

    public enum Behavior {

        /**
         * Passive tracks play in tandem with other tracks, not stopping any other tracks.  This is used in playlists
         * during passive/faded transitions, or when playing simultaneous messages
         * (such as responses to a voice command)
         */
        PASSIVE,

        /**
         * Exclusive tracks stop other playing tracks and playlists if possible, and fail if it is impossible to obtain
         * sole ownership of a given channel.  This is used when playing exclusive singleton tracks not associated
         * with any playlist.
         */
        EXCLUSIVE,

        /**
         * When no other tracks or playlists are playing, queued tracks play immediately.  Otherwise, they are queued
         * on the channel to be played at the next opportunity.
         *
         * This is used almost exclusively by the ".v q" command to passively enqueue a track.
         */
        QUEUED

    }
}