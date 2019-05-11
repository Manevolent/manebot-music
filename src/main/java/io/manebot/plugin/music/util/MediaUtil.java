package io.manebot.plugin.music.util;

import com.github.manevolent.ffmpeg4j.*;
import com.github.manevolent.ffmpeg4j.source.AudioSourceSubstream;
import com.github.manevolent.ffmpeg4j.source.MediaSourceSubstream;
import com.github.manevolent.ffmpeg4j.source.VideoSourceSubstream;
import com.github.manevolent.ffmpeg4j.stream.source.FFmpegSourceStream;
import io.manebot.lambda.ThrowingFunction;
import org.bytedeco.javacpp.avformat;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class MediaUtil {

    /**
     * Gets the duration of a  file (represented by inputStream) in seconds.
     * @param sourceSubstream media source substream to read.
     * @return remaining rational duration of the source, in seconds.
     * @throws NullPointerException if the sourceSubstream argument is null
     * @throws IOException if there is a problem interpreting or decoding the media file.
     */
    public static double getDuration(MediaSourceSubstream sourceSubstream) throws IOException {
        if (sourceSubstream == null)
            throw new NullPointerException("sourceSubstream");

        if (!sourceSubstream.isDecoding())
            throw new IllegalStateException("not decoding");

        double position = 0D;
        MediaFrame frame;

        try {
            while ((frame = (MediaFrame) sourceSubstream.next()) != null)
                position = Math.max(frame.getPosition(), position);
        } catch (EOFException e) {
            // do nothing, this is expected behavior for FFmpeg4j
        }

        return position;
    }

    /**
     * Gets the duration of a media stream (represented by inputStream) in seconds.
     * @param inputStream media stream to read
     * @param formatName container format of the media stream
     * @param substreamFunction function used to select the desired substream from the file container.
     * @return real rational duration of the input, in seconds.
     * @throws FFmpegException if there is a problem interpreting or reading the media container or substream.
     */
    public static double getDuration(InputStream inputStream,
                                                 String formatName,
                                                 ThrowingFunction<FFmpegSourceStream, MediaSourceSubstream, FFmpegException>
                                             substreamFunction)
            throws FFmpegException {
        FFmpegIO input;
        try {
            input = FFmpegIO.openInputStream(inputStream, FFmpegIO.DEFAULT_BUFFER_SIZE);
        } catch (Exception ex) {
            throw new FFmpegException(ex);
        }

        // Open input using FFmpegIO wrapped native I/O instance
        avformat.AVInputFormat inputFormat = FFmpeg.getInputFormatByName(formatName);
        FFmpegSourceStream sourceStream = new FFmpegInput(input).open(inputFormat);

        try {
            // Get primary media substream
            MediaSourceSubstream selectedSubstream = substreamFunction.apply(sourceStream);

            // Improve efficiency by not decoding other substreams
            for (MediaSourceSubstream substream : sourceStream.registerStreams()) {
                if (substream != selectedSubstream) substream.setDecoding(false);
            }

            // Get duration using lower-level function
            return getDuration(selectedSubstream);
        } catch (Exception ex) {
            throw new FFmpegException(ex);
        } finally {
            try {
                sourceStream.close();
            } catch (Exception e) {
                // do nothing
            }

            try {
                input.close();
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    /**
     * Gets the duration of the specified input stream in seconds
     * @param inputStream media container file to read
     * @param formatName format of the media file
     * @return real rational duration of the input, in seconds.
     * @throws FFmpegException
     */
    public static double getPrimaryAudioDuration(InputStream inputStream, String formatName) throws FFmpegException {
        return getDuration(inputStream, formatName, (sourceStream) ->
                sourceStream.registerStreams().stream()
                        .filter(x -> x instanceof AudioSourceSubstream)
                        .findFirst()
                        .orElse(null)
        );
    }

    /**
     * Gets the duration of the specified input stream in seconds
     * @param inputStream media container file to read
     * @param formatName format of the media file
     * @return real rational duration of the input, in seconds.
     * @throws FFmpegException
     */
    public static double getPrimaryVideoDuration(InputStream inputStream, String formatName) throws FFmpegException {
        return getDuration(inputStream, formatName, (sourceStream) ->
                sourceStream.registerStreams().stream()
                        .filter(x -> x instanceof VideoSourceSubstream)
                        .findFirst()
                        .orElse(null)
        );
    }
}
