package io.manebot.plugin.music.source;

import com.github.manevolent.ffmpeg4j.FFmpegException;
import com.github.manevolent.ffmpeg4j.FFmpegIO;
import com.github.manevolent.ffmpeg4j.FFmpegInput;
import com.github.manevolent.ffmpeg4j.stream.source.FFmpegSourceStream;
import io.manebot.plugin.audio.mixer.input.AudioProvider;
import io.manebot.plugin.audio.mixer.input.FFmpegAudioProvider;
import org.bytedeco.javacpp.avformat;

import java.io.IOException;
import java.io.InputStream;

import java.util.Map;

public class FFmpegAudioProtocol implements AudioProtocol {
    private final double bufferSeconds;

    public FFmpegAudioProtocol(double bufferSeconds) {
        this.bufferSeconds = bufferSeconds;
    }

    private AudioProvider open(FFmpegIO ioState, String format) throws FFmpegException {
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
    public AudioProvider open(String url, String format, Map<String, String> headers /*TODO ignored*/)
            throws IOException {
        try {
            return open(FFmpegIO.openNativeUrlInput(url), format);
        } catch (FFmpegException e) {
            throw new IOException(e);
        }
    }

    @Override
    public AudioProvider open(InputStream inputStream, String format) throws IOException {
        try {
            return open(FFmpegIO.openInputStream(inputStream, FFmpegIO.DEFAULT_BUFFER_SIZE), format);
        } catch (FFmpegException e) {
            throw new IOException(e);
        }
    }
}
