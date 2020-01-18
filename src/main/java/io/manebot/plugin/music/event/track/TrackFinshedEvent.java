package io.manebot.plugin.music.event.track;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.database.model.*;

public class TrackFinshedEvent extends TrackEvent {
    private final double duration;
    
    public TrackFinshedEvent(Object sender, Music music, Track track, double duration) {
	super(sender, music, track);
	this.duration = duration;
    }
    
    public double getDuration() {
	return duration;
    }
}
