package io.manebot.plugin.music.event.track;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.database.model.*;
import io.manebot.plugin.music.repository.*;

public class TrackDownloadedEvent extends TrackEvent {
    private final Repository.Resource resource;
    
    public TrackDownloadedEvent(Object sender, Music music, Track track, Repository.Resource resource) {
	super(sender, music, track);
	
	this.resource = resource;
    }
    
    public Repository.Resource getResource() {
	return resource;
    }
}
