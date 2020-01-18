package io.manebot.plugin.music.event.playlist;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.database.model.*;
import io.manebot.plugin.music.playlist.*;

public class PlaylistTrackChangedEvent extends PlaylistEvent {
    private final Track track;
    
    public PlaylistTrackChangedEvent(Object sender, Music music, Playlist playlist, Track track) {
	super(sender, music, playlist);
	this.track = track;
    }
    
    public Track getTrack() {
	return track;
    }
}
