package io.manebot.plugin.music.event.track;

import io.manebot.plugin.music.*;
import io.manebot.plugin.music.database.model.*;

public class TrackStartedEvent extends TrackEvent {
    public TrackStartedEvent(Object sender, Music music, Track track) {
	super(sender, music, track);
    }
}
