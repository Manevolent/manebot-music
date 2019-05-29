# Manebot Music
This is the official music bot/audio player for Manebot. Supports all platforms using a proper, standard implementation of the the `audio` plugin: https://github.com/Manevolent/manebot-audio

## Commands

### track

The `track` command is used to manipluate tracks (e.g. YouTube videos, internet radio streams, twitch.tv livestreams)

```
track play http://youtu.be/something
```

### stop

Stops your track or playlist, if you have the right to do so.

### next/skip

Skips to the next track in a playlist

### playlist

Starts a playlist using a *query*, and manages a running playlist.

```
playlist
playlist info
playlist start tag
playlist start "videos with this in the title" -tag
```

## Track queries

Tracks can be queried using long, well-formatted query strings. Internally, these queries are converted into SQL with the Hibernate criteria API.

<img src="https://raw.githubusercontent.com/Manevolent/manebot-music/master/track_search_help_1.png">
<img src="https://raw.githubusercontent.com/Manevolent/manebot-music/master/track_search_help_2.png">
