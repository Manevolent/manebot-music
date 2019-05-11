package io.manebot.plugin.music.playlist;

import io.manebot.plugin.music.database.model.Track;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Represents a basic queue of tracks.
 */
public interface TrackQueue extends Iterator<Track> {

    /**
     * Finds the next track in the playlist.
     * @return next track.
     * @throws NoSuchElementException if no track is available.
     */
    @Override /* Iterator */
    Track next() throws NoSuchElementException;

    /**
     * Finds if the playlist has another track.
     * @return true if another track is available, false otherwise.
     */
    @Override /* Iterator */
    boolean hasNext();

    /**
     * Peeks at the next videos in the playlist queue.
     * @return immutable Track collection following the current track.
     */
    Collection<Track> peek();

}
