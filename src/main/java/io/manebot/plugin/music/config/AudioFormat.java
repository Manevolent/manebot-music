package io.manebot.plugin.music.config;

import com.github.manevolent.ffmpeg4j.FFmpeg;
import com.github.manevolent.ffmpeg4j.FFmpegException;
import com.google.gson.annotations.Expose;

public final class AudioFormat {
    public AudioFormat(int sample_rate, int channels) {
        this.sample_rate = sample_rate;
        this.channels = channels;
    }
    
    public AudioFormat() {
    
    }
    
    public int sample_rate;
    public int channels;

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
    
    public javax.sound.sampled.AudioFormat toJava() throws FFmpegException {
        return new javax.sound.sampled.AudioFormat(
                        javax.sound.sampled.AudioFormat.Encoding.PCM_FLOAT,
                        getSampleRate(),
                        32,
                        channels,
                        4 * channels,
                        getSampleRate(),
                        false
        );
    }
    
    public static AudioFormat from(javax.sound.sampled.AudioFormat audioFormat) {
        return new AudioFormat((int) audioFormat.getSampleRate(), audioFormat.getChannels());
    }
}