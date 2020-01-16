package io.manebot.plugin.music.config;

public final class AudioDownloadFormat {
    public String container_format;
    public String codec;
    public int bitrate;
    public AudioFormat audioFormat;

    public String getContainerFormat() {
        return container_format;
    }

    public String getAudioCodec() {
        return codec;
    }

    public int getAudioBitrate() {
        return bitrate;
    }

    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    @Override
    public String toString() {
        return audioFormat.toString() + " format=" + container_format + " codec=" + codec + " bitrate=" + bitrate;
    }
}