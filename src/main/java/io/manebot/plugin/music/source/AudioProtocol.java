package io.manebot.plugin.music.source;

import io.manebot.plugin.audio.mixer.input.AudioProvider;
import io.manebot.plugin.audio.mixer.output.*;
import io.manebot.plugin.audio.resample.*;
import io.manebot.plugin.music.config.*;

import java.io.*;

import java.net.*;
import java.util.Collections;

import java.util.Map;

public interface AudioProtocol {

    /**
     * Opens an audio provider from the specified URL.
     * @param uri URI to open.
     * @param format format name of the input stream (typically, the file extension of the container format).
     *               Can be null.
     * @param headers HTTP headers to use when opening the URL.
     * @param bufferSize buffer size to use when opening the URL.
     * @return AudioProvider instance.
     */
    AudioProvider openProvider(URI uri, String format, Map<String, String> headers, int bufferSize) throws IOException;

    /**
     * Opens an audio provider from the specified URL.
     * @param uri URI to open.
     * @param format format name of the input stream (typically, the file extension of the container format).
     * @return AudioProvider instance.
     */
    default AudioProvider openProvider(URI uri, String format, int bufferSize) throws IOException {
        return openProvider(uri, format, Collections.emptyMap(), bufferSize);
    }

    /**
     * Opens an audio provider from the specified URL, guessing the format as necessary.
     * @param uri URI to open.
     * @return AudioProvider instance.
     */
    default AudioProvider openProvider(URI uri, int bufferSize) throws IOException {
        return openProvider(uri, null, bufferSize);
    }

    /**
     * Opens an audio provider from the specified input stream.
     * @param inputStream input stream of encoded samples to read from.
     * @param format format name of the input stream (typically, the file extension of the container format).
     * @return AudioProvider instance.
     */
    AudioProvider openProvider(InputStream inputStream, String format, int bufferSize) throws IOException;

    /**
     * Opens an audio provider from the specified input stream, guessing the format as necessary.
     * @param inputStream input stream of encoded samples to read from.
     * @param bufferSize buffer size to use, in bytes.
     * @return AudioProvider instance
     */
    default AudioProvider openProvider(InputStream inputStream, int bufferSize) throws IOException {
        return openProvider(inputStream, null, bufferSize);
    }
    
    /**
     * Opens an audio provider from the specified input stream, guessing the format and buffer size as necessary.
     * @param inputStream input stream of encoded samples to read from.
     * @return AudioProvider instance
     */
    AudioProvider openProvider(InputStream inputStream) throws IOException;

    /**
     * Opens an audio provider from the specified container file, guessing the format as necessary.
     * @param file container file to read from.
     * @return AudioProvider instance
     */
    default AudioProvider openProvider(File file, int bufferSize) throws FileNotFoundException, IOException {
        return openProvider(new FileInputStream(file), bufferSize);
    }
    
    AudioConsumer openConsumer(OutputStream outputStream, AudioDownloadFormat format) throws IOException;
    
    Resampler openResampler(AudioFormat input, AudioFormat output, int bufferSize) throws IOException;
}