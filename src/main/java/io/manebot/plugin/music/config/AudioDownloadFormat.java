package io.manebot.plugin.music.config;

import com.google.gson.annotations.Expose;

public final class AudioDownloadFormat {
    @Expose
    private String container_format;

    @Expose
    private String codec;

    @Expose
    private int bitrate;

    @Expose
    private AudioFormat audioFormat;

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

    public void setContainerFormat(String containerFormat) {
        this.container_format = containerFormat;
    }

    public void setAudioCodec(String audioCodec) {
        this.codec = audioCodec;
    }

    public void setAudioBitrate(int audioBitrate) {
        this.bitrate = audioBitrate;
    }

    public void setAudioFormat(AudioFormat audioFormat) {
        this.audioFormat = audioFormat;
    }

    @Override
    public String toString() {
        return audioFormat.toString() + " format=" + container_format + " codec=" + codec + " bitrate=" + bitrate;
    }
}