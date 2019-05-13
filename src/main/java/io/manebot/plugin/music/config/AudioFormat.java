package io.manebot.plugin.music.config;

import com.github.manevolent.ffmpeg4j.FFmpeg;
import com.github.manevolent.ffmpeg4j.FFmpegException;
import com.google.gson.annotations.Expose;

public final class AudioFormat {
    @Expose
    private int sample_rate;

    @Expose
    private int channels;

    public int getSampleRate() {
        return this.sample_rate;
    }

    public int getChannels() {
        return this.channels;
    }

    @Override
    public String toString() {
        return Integer.toString(this.sample_rate) + "Hz, " + Integer.toString(this.channels) + "ch";
    }

    public com.github.manevolent.ffmpeg4j.AudioFormat toFFmpeg() throws FFmpegException {
        return new com.github.manevolent.ffmpeg4j.AudioFormat(
                sample_rate,
                channels,
                FFmpeg.guessFFMpegChannelLayout(channels)
        );
    }
}