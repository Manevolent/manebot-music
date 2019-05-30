package io.manebot.plugin.music.playlist;

import io.manebot.conversation.Conversation;
import io.manebot.database.search.Search;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.player.AudioPlayer;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.user.User;
import io.manebot.user.UserAssociation;

import java.util.Collection;
import java.util.function.Function;

/**
 * Represents a running playlist on an AudioChannel for a specific User.
 */
public interface Playlist extends TrackQueue {

    /**
     * Gets the audio channel this playlist is associated with.
     * @return AudioChannel instance.
     */
    AudioChannel getChannel();

    /**
     * Gets the conversation this playlist is associated with.
     * @return Conversation instance.
     */
    Conversation getConversation();

    /**
     * Gets the community this playlist is associated with.
     * @return Community instance.
     */
    Community getCommunity();

    /**
     * Gets the current playing track.
     * @return Track instance.
     */
    Track getCurrent();

    /**
     * Gets the queue for this playlist.
     * @return queue instance.
     */
    TrackQueue getQueue();

    /**
     * Gets a collection of audio players currently associated with this playlist.
     * @return audio player collection.
     */
    Collection<AudioPlayer> getPlayers();

    /**
     * Finds if the playlist is running.
     * @return true if the playlist is running, false otherwise.
     */
    boolean isRunning();

    /**
     * Sets the playlist's state
     * @param running running
     * @return true if the state was updated, false otherwise.
     */
    boolean setRunning(boolean running);

    /**
     * Gets the user instance associated with owning this playlist.
     * @return User
     */
    UserAssociation getUser();

    /**
     * Transfers ownership of the playlist to another user.
     * @param user User to transfer ownership of the playlist to.
     * @throws SecurityException if there is a security problem transferring the playlist.
     */
    void transferToUser(UserAssociation user) throws SecurityException;

    interface Builder {
        Audio getAudio();
        Music getMusic();
        Community getCommunity();
        Conversation getConversation();
        AudioChannel getChannel();
        UserAssociation getUser();

        Builder setUser(UserAssociation user);
        Builder setQueue(TrackQueue queue);
        Builder setQueue(Function<QueueSelector, TrackQueue> function);
        Builder addListener(Listener listener);
    }

    interface QueueSelector {
        TrackQueue from(Iterable<Track> queue, boolean loop);
        TrackQueue from(Track queue, boolean loop);
        TrackQueue from(Search search);
    }

    interface Listener {
        default void onStarted(Playlist playlist) {}
        default void onTrackSkipped(Playlist playlist, Track track) {}
        default void onTrackChanged(Playlist playlist, Track track) {}
        default void onTransferred(Playlist playlist, UserAssociation a, UserAssociation b) {}
        default void onStopped(Playlist playlist) {}
    }

}
