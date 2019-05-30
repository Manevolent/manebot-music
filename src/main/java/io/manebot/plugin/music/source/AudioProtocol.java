package io.manebot.plugin.music.source;

import io.manebot.plugin.audio.mixer.input.AudioProvider;

import java.io.*;

import java.util.Collections;

import java.util.Map;

public interface AudioProtocol {

    /**
     * Opens an audio provider from the specified URL.
     * @param url URL to open.
     * @param format format name of the input stream (typically, the file extension of the container format).
     *               Can be null.
     * @param headers HTTP headers to use when opening the URL.
     * @return AudioProvider instance.
     */
    AudioProvider open(String url, String format, Map<String, String> headers) throws IOException;

    /**
     * Opens an audio provider from the specified URL.
     * @param url URL to open.
     * @param format format name of the input stream (typically, the file extension of the container format).
     * @return AudioProvider instance.
     */
    default AudioProvider open(String url, String format) throws IOException {
        return open(url, format, Collections.emptyMap());
    }

    /**
     * Opens an audio provider from the specified URL, guessing the format as necessary.
     * @param url URL to open.
     * @return AudioProvider instance.
     */
    default AudioProvider open(String url) throws IOException {
        return open(url, null);
    }

    /**
     * Opens an audio provider from the specified input stream.
     * @param inputStream input stream of encoded samples to read from.
     * @param format format name of the input stream (typically, the file extension of the container format).
     * @return AudioProvider instance.
     */
    AudioProvider open(InputStream inputStream, String format) throws IOException;

    /**
     * Opens an audio provider from the specified input stream, guessing the format as necessary.
     * @param inputStream input stream of encoded samples to read from.
     * @return AudioProvider instance
     */
    default AudioProvider open(InputStream inputStream) throws IOException {
        return open(inputStream, null);
    }

    /**
     * Opens an audio provider from the specified container file, guessing the format as necessary.
     * @param file container file to read from.
     * @return AudioProvider instance
     */
    default AudioProvider open(File file) throws FileNotFoundException, IOException {
        return open(new FileInputStream(file));
    }

}