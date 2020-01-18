package io.manebot.plugin.music.event.track;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.database.model.*;
import io.manebot.plugin.music.event.*;

public abstract class TrackEvent extends MusicEvent {
    private final Track track;
    
    protected TrackEvent(Object sender, Music music, Track track) {
	super(sender, music);
	
	this.track = track;
    }
    
    public Track getTrack() {
	return track;
    }
}
