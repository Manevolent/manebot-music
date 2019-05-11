package io.manebot.plugin.music.playlist;

import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.user.User;

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
     * Gets the current playing track.
     * @return Track instance.
     */
    Track getCurrent();

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
    User getUser();

    /**
     * Transfers ownership of the playlist to another user.
     * @param user User to transfer ownership of the playlist to.
     * @throws SecurityException if there is a security problem transferring the playlist.
     */
    void transferToUser(User user) throws SecurityException;

}
