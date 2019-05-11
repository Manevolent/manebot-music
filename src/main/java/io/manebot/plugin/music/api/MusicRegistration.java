package io.manebot.plugin.music.api;

import io.manebot.plugin.Plugin;

import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.TrackRepository;
import io.manebot.plugin.music.repository.Repository;
import io.manebot.plugin.music.track.TrackSource;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public interface MusicRegistration {

    /**
     * Gets the Audio instance associated with this registration.
     * @return Audio instance.
     */
    Music getMusic();

    /**
     * Gets the plugin associated with this registration.
     * @return Plugin instance.
     */
    Plugin getPlugin();

    /**
     * Gets an immutable collection of track sources registered to this plugin's registration.
     * @return Collection of TrackSources.
     */
    Collection<TrackSource> getTrackSources();

    /**
     * Gets an immutable collection of repository classes registered to this plugin's registration.
     * @return Collection of Repository classes.
     */
    Map<Class<? extends Repository>, Function<TrackRepository, Repository>> getRepositoryConstructors();

    interface Builder {

        /**
         * Gets the audio instance.
         * @return audio instance.
         */
        Music getMusic();

        /**
         * Gets the platform for this audio registration.
         * @return Platform instance.
         */
        Plugin getPlugin();

        /**
         * Registers a track source into the Music instance.
         * @param trackSource TrackSource to register.
         * @return Builder instance for continuance.
         */
        Builder registerTrackSource(TrackSource trackSource);

        /**
         * Registers a repository class into the Music instance.
         * @param clazz class to register.
         * @param function function used to construct a new instance of a repository
         * @return Builder instance for continuance.
         */
        Builder registerRepository(
                Class<? extends Repository> clazz,
                Function<TrackRepository, Repository> function
        );

    }
}
