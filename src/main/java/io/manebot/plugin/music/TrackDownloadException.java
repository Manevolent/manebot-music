package io.manebot.plugin.music;

public class TrackDownloadException extends Exception {
    public TrackDownloadException(String message) {
        super(message);
    }
    
    public TrackDownloadException(Throwable cause) {
	super(cause);
    }
    
    public TrackDownloadException(String message, Throwable cause) {
	super(message, cause);
    }
}
