# Manebot Music
This is the official music bot/audio player for Manebot. Supports all platforms using a proper, standard implementation of the the `audio` plugin: https://github.com/Manevolent/manebot-audio

Please note that this plugin uses `youtube-dl` (https://rg3.github.io/youtube-dl/) to stream (or download) tracks, so either:
* Use the provided, official Manebot Docker image
* Install youtube-dl into your environment

## Highlights

### Features

* Supports many sites through the use of **youtube-dl**
* Supports virtually any container format (i.e. mp4, mov) via FFmpeg, as well as virtually any audio codec it does (i.e. aac, mp3)
* Supports HLS livestreams (i.e. all those YouTube LIVE music stations, or your favourite live-streamer)
* Uses the Manebot audio system (https://github.com/Manevolent/manebot-audio): cross-fading, low-CPU, high throughput audio playback and mixing on supported platforms (Teamspeak and Discord) via their respective Manebot plugins
* Repository storage system to cache tracks offline for faster access
* Complete playlist system designed around a powerful track search engine
* Independent track metadata isolated per designated community (i.e. Discord guilds), such as a like/dislike system, tags, and play counts.

### Installation

Installing the Manebot Music plugin is easy.  Just run the following commands:

```
plugin install music
plugin enable music
```

## Commands

### track

The `track` command is used to manipluate tracks (e.g. YouTube videos, internet radio streams, twitch.tv livestreams)

```
track info
track info http://youtu.be/something

track play http://youtu.be/something
track play "videos with this in the title"

track search "videos with this in the title"
track search "videos with this in the title" page:2
track search

track tag music
track tag unwanted
track untag unwanted
```

Consider making **aliases** for these commands using the `alias` command itself:

`alias search track search` = `search "videos with this in the title" page:2`<br/>
`alias track info i` = `i`

### stop

Stops your track or playlist, if you have the right to do so.

### skip

Skips to the next track in a playlist

### playlist

Starts a playlist using a *query*, and manages a running playlist.

```
playlist
playlist info
playlist start music
playlist start "non-music title" -music
```

## Track queries

Tracks can be queried using long, well-formatted query strings. Internally, these queries are converted into SQL with the Hibernate criteria API.

<img src="https://raw.githubusercontent.com/Manevolent/manebot-music/master/track_search_help_1.png">
<img src="https://raw.githubusercontent.com/Manevolent/manebot-music/master/track_search_help_2.png">

## Communities

<img src="https://raw.githubusercontent.com/Manevolent/manebot-music/master/communities.png">

**Music communities** in the `music` plugin are separate from *Communities* in Manebot, but they interact. This is so you can have multiple clusters of *Manebot* communities, connected individually to many *music* communities.  Or, connect them all to the same one!  It's up to you. As a refresher, a *Manebot* community is something like a Discord guild, or a Teamspeak server. Manebot automatically divides your guilds, servers, and MUCs into their own communities and assigns them identifiers unique to the platform they are associated with. You can use these identifiers to associate a Manebot community to its own music community; check out `music community` (`help music community`) for more information.

The primary purpose of music communities is to isolate track metadata, like titles, likes, plays, and so forth. This also helps isolate the suggestion/playlist engine from other communities, providing what each community wants more of when they shuffle their tracks.

## Repositories

<img src="https://raw.githubusercontent.com/Manevolent/manebot-music/master/repositories.png">

**Repositories** can associate to a music community to provide a backing store for *finite* tracks (sorry, Twitch.tv livestreams can't be stored). Planned repository storage targets are basic filesystem and NAS/NFS.

**By default**, the *nul* repository is used; this means that, while Track metadata (titles, and such) are kept in the Manebot database, no audio information (samples) are stored locally.

You can also use leverage the repository heirarchy to replicate your precious track data across sources, as well, in case you were worried about losing your *memories*.

## Under the hood

### Playback

<p align="center"><img src="https://raw.githubusercontent.com/Manevolent/manebot-music/master/pipeline.png"></p>

* First, a **source bitstream** is acquired using the FFmpeg API bundled with the `media` plugin, `FFmpeg4j`.  `FFmpeg4j` uses its own bundled native C libraries to connect to a `URL` or `InputStream` directly in-process.
* The bitstream is demuxed into several *substreams*, which are individually evaluated. All video substreams are discarded at this point, to save on the CPU time needed to decode only needed audio information. An ideal audio substream is selected; most videos/tracks/files only have one audio stream, but if there are multiple the primary stream is selected.
* The substream is decoded, and at this point enters the Manebot audio engine.  Decoded samples are stored in a 10-second *buffer*, so livestreams have an opportunity to "read-ahead" and not stutter (kind-of like the YouTube and Twitch players would). This buffer is updated on its own thread to reduce blocking time on a mixer, therefore eliminating stutter on the mixer's sinks (i.e. your ears!). Samples at this point forward are unspooled from the aforementioned buffer at the sample-rate set by the Mixer where the track/video will be playing.   This is done in `FFmpegAudioProvider` of the `audio` plugin.
* If necessary, the samples are piped next into a `ResampledAudioProvider` unique to the prior provider. This provider will resample the incoming samples real-time and provide those samples to the next consumer in the chain.
* A `TransitionedAudioPlayer` is used to cross-fade (fade-in, fade-out) the track smoothly, so that you get a nice transition on playlists. Per-user volume modification happens here. As `TransitionedAudioPlayer` is also a `MixerChannel`, it is connected directly to the mixer where the track/video is to be played.
* The mixer will take care of retrieving samples from the stream ahead of it as necessary; it has its own built-in timer switch, as do its sinks (i.e. Teamspeak or Discord).
