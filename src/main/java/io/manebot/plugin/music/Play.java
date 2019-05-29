package io.manebot.plugin.music;

import io.manebot.conversation.Conversation;
import io.manebot.database.search.SearchResult;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.player.AudioPlayer;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.plugin.music.source.TrackSource;
import io.manebot.user.UserAssociation;

import java.net.URL;
import java.util.function.Consumer;
import java.util.function.Function;

public class Play {
    private final Music music;
    private final Track track;
    private final AudioChannel channel;
    private final Conversation conversation;
    private final AudioPlayer player;

    Play(Music music,
         Track track,
         AudioChannel channel,
         Conversation conversation,
         AudioPlayer player) {
        this.music = music;
        this.track = track;
        this.channel = channel;
        this.conversation = conversation;
        this.player = player;
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
         * Changed the download behavior of the track playback. When true, new downloads are allowed for the playback.
         * @param caching true if downloads should be allowed, false otherwise.
         * @return Builder for continuance.
         */
        Builder setDownloading(boolean caching);

        /**
         * Changed the caching behavior of the track playback. When true, caching is allowed for the playback.
         * @param caching true if downloads should be allowed, false otherwise.
         * @return Builder for continuance.
         */
        Builder setCaching(boolean caching);

        /**
         * When exclusive is set, no other tracks are allowed to be playing on the system.
         * @param exclusive true if exclusive mode should be enabled, false otherwise.
         * @return Builder for continuance.
         */
        Builder setExclusive(boolean exclusive);

        /**
         * Sets the "fade out" listener, fired when the track fades out.
         * @param fadeOut fade out callback.
         * @return Builder for continuance.
         */
        Builder setFadeOut(Consumer<Track> fadeOut);

    }

    public interface TrackSelector {
        TrackSource.Result find(URL url) throws IllegalArgumentException;
        TrackSource.Result find(SearchResult<Track> searchResult) throws IllegalArgumentException;
        TrackSource.Result find(Track track) throws IllegalArgumentException;
    }
}