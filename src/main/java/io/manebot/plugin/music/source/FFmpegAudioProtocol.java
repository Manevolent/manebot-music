package io.manebot.plugin.music.source;

import com.github.manevolent.ffmpeg4j.*;
import com.github.manevolent.ffmpeg4j.AudioFormat;
import com.github.manevolent.ffmpeg4j.output.*;
import com.github.manevolent.ffmpeg4j.stream.output.*;
import com.github.manevolent.ffmpeg4j.stream.source.FFmpegSourceStream;
import io.manebot.plugin.audio.mixer.input.AudioProvider;
import io.manebot.plugin.audio.mixer.input.FFmpegAudioProvider;
import io.manebot.plugin.audio.mixer.output.*;
import io.manebot.plugin.audio.resample.*;
import io.manebot.plugin.music.config.*;
import org.bytedeco.javacpp.avformat;

import java.io.*;

import java.net.*;
import java.util.*;

public class FFmpegAudioProtocol implements AudioProtocol {
    private final double bufferSeconds;

    public FFmpegAudioProtocol(double bufferSeconds) {
        this.bufferSeconds = bufferSeconds;
    }

    private AudioProvider openProvider(FFmpegIO ioState, String format) throws FFmpegException {
        // wrap the IOState.  this would call avformat_alloc_context
        FFmpegInput input = new FFmpegInput(ioState);

        FFmpegSourceStream sourceStream = null;

        try {
            // open the source stream
            if (format != null)
                sourceStream = input.open(format);
            else
                // autodetect
                // source: https://www.ffmpeg.org/doxygen/3.3/group__lavf__decoding.html#ga31d601155e9035d5b0e7efedc894ee49
                //  fmt	If non-NULL, this parameter forces a specific input format. Otherwise the format is autodetected.
                sourceStream = input.open((avformat.AVInputFormat) null);

            // scan the source stream's audio substream(s), select an appropriate one, then return a provider around it.
            return FFmpegAudioProvider.open(sourceStream, bufferSeconds);
        } catch (FFmpegException ex) {
            // free pointers
            if (sourceStream != null)
                try {
                    sourceStream.close();
                } catch (Exception e) {
                    // do nothing
                }
            else
                try {
                    input.close();
                } catch (Exception e) {
                    // do nothing
                }

            throw ex;
        }
    }

    @Override
    public AudioProvider openProvider(URI uri, String format, Map<String, String> headers, int bufferSize) throws IOException {
        try {
            return openProvider(FFmpegIO.openNativeUrlInput(uri.toASCIIString()), format);
        } catch (FFmpegException e) {
            throw new IOException(e);
        }
    }

    @Override
    public AudioProvider openProvider(InputStream inputStream, String format, int bufferSize) throws IOException {
        try {
            return openProvider(FFmpegIO.openInputStream(inputStream, FFmpegIO.DEFAULT_BUFFER_SIZE), format);
        } catch (FFmpegException e) {
            throw new IOException(e);
        }
    }
    
    @Override
    public AudioProvider openProvider(InputStream inputStream) throws IOException {
        return openProvider(inputStream, FFmpegIO.DEFAULT_BUFFER_SIZE);
    }
    
    @Override
    public AudioConsumer openConsumer(OutputStream outputStream, AudioDownloadFormat format) throws IOException {
        AudioFormat audioFormat;
        try {
            audioFormat = new AudioFormat(
                            format.getAudioFormat().getSampleRate(),
                            format.getAudioFormat().getChannels(),
                            FFmpeg.guessFFMpegChannelLayout(format.getAudioFormat().getChannels())
            );
        } catch (FFmpegException e) {
            throw new IOException("Problem constructing audio format", e);
        }
        
        FFmpegIO io;
    
        try {
            io = FFmpegIO.openOutputStream(outputStream, FFmpegIO.DEFAULT_BUFFER_SIZE);
        } catch (FFmpegException e) {
            throw new IOException("Problem opening FFmpeg output", e);
        }
        
        FFmpegTargetStream targetStream;
        try {
           targetStream = new FFmpegTargetStream(format.getContainerFormat(), io, new FFmpegTargetStream.FFmpegNativeOutput());
        } catch (FFmpegException e) {
            try {
                io.close();
            } catch (Exception ex) {
                e.addSuppressed(ex);
            }
            
            throw new IOException("Problem opening FFmpeg target stream", e);
        }
    
        AudioTargetSubstream substream;
        try {
            substream = targetStream.registerAudioSubstream(format.getAudioCodec(), audioFormat,
                            Collections.singletonMap("b", Integer.toString(format.getAudioBitrate())));
        } catch (FFmpegException ex) {
            try {
                targetStream.close();
            } catch (Exception e) {
                ex.addSuppressed(e);
            }
    
            throw new IOException("Problem opening audio substream", ex);
        }
    
        try {
            targetStream.writeHeader();
        } catch (Exception ex) {
            try {
                targetStream.close();
            } catch (Exception e) {
                ex.addSuppressed(e);
            }
        
            throw new IOException("Problem writing stream header", ex);
        }
    
        return new AudioConsumer() {
            @Override
            public void write(float[] buffer, int len) {
                double time = (double)len / ((double)format.getAudioFormat().getSampleRate() * (double)format.getAudioFormat().getChannels());
                
                try {
                    substream.write(new AudioFrame(0D, 0D, time, buffer, len, audioFormat));
                } catch (IOException e) {
                    throw new RuntimeException("Problem writing audio frame", e);
                }
            }
    
            @Override
            public void close() throws Exception {
                targetStream.close();
            }
        };
    }
    
    @Override
    public Resampler openResampler(io.manebot.plugin.music.config.AudioFormat input, io.manebot.plugin.music.config.AudioFormat output, int bufferSize)
                    throws IOException {
        try {
            return new FFmpegResampler(input.toJava(), output.toJava(), bufferSize);
        } catch (FFmpegException e) {
            throw new IOException(e);
        }
    }
}
