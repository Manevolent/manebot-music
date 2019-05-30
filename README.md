# Manebot Music
This is the official music bot/audio player for Manebot. Supports all platforms using a proper, standard implementation of the the `audio` plugin: https://github.com/Manevolent/manebot-audio

Please note that this plugin uses `youtube-dl` (https://rg3.github.io/youtube-dl/) to stream (or download) tracks, so either:
* Use the provided, official Manebot Docker image
* Install youtube-dl into your environment

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
playlist start tag
playlist start "videos with this in the title" -tag
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

<img src="https://raw.githubusercontent.com/Manevolent/manebot-music/master/pipeline.png">
