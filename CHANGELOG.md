# Changelog

## 1.0.2 (versionCode 102)
- Thinner lines and points on the changeset maps, like OSMCha.
- Settings: the "Allow installation" card disappears as soon as you come back with the permission granted.

## 1.0.1 (versionCode 101)
- Area editor: a new area opens where you are (last known location) instead of Rome.
- "My location" button on the area map; it asks for location only when tapped.
- Drawing a new area by hand: the rectangle stays fixed on the screen and you move the map under it; the corners resize it.

## 1.0.0 (versionCode 100)
- Watch areas drawn on a map, with their size in km² and a warning for large areas.
- Geometric check of every changeset: only those that really touch the area count; the others sit behind the "Also unverified" filter.
- Changeset mini-map: new (green), modified (orange), deleted (dashed red); tap an element for its changed tags. Full-screen map with legend.
- Modified elements keep their previous shape (dashed grey) under the new one; moved nodes show their old position.
- Background check (WorkManager) with one notification per area; interval from 15 min to 6 h.
- Settings in ParkMap style: theme, colours (blue or Material You), language (it/en), filter, map cache.
- Italian and English; no proprietary services, no trackers.
- Search box in the list (user, comment, editor, id); "Only my areas" filter off = global search by OSM user name or changeset id.
- Opens openstreetmap.org/changeset/ID links from other apps and browsers.
- OSMCha-style flags (many changes, no comment, mass deletion, new mapper, previously blocked, suspect user) and trusted/suspect user lists.
- Filters in a bottom sheet: flagged only, hide trusted, size, date, editor, hashtag, type of change (from the changeset counts).
- Changeset detail: relations, full list of changed elements with tag diffs, history and "Edit in iD" links, discussion.
- OSM login (OAuth2 PKCE, no secret in the app) to comment on changesets; token encrypted with the Android Keystore.
- Author card with profile numbers and "Recent activity" (HDYC-like summary of the last 100 changesets).
- Watch OSM users anywhere in the world, not only areas.
- Area editor: search municipalities and places with Nominatim; an area can follow the real boundary, and verification uses it.
- Redesigned list, detail, areas and area editor; footprint glyph showing where a changeset falls in the area.
- Database migrations keep existing areas (versions 2 to 5).
- "Load more" at the end of the list fetches older changesets; the verification budget now counts only real downloads.
- Element history inside the app, with the differences between versions and links to each changeset.
- Maps zoom up to 21; they open at most at zoom 19, where OSM tiles exist, and enlarge those beyond it (zoom 29 was unreadable).
- Relative times ("4 hours ago") follow the app language instead of the system language.
- New icon: a way with a moved node and its previous position; the notification icon matches it.
- Updates from GitHub releases inside the app: daily check at startup (can be turned off), download with progress, install only if signed like the installed app.
- Code formatted with ktfmt through Spotless; repeated error handling merged into one helper.
- Tap the number of blocks in the author card to read each block: dates, who blocked, full reason.
- Users screen (icon in the list bar): watched, suspect and trusted users; tap a name for their changesets, remove with the cross.
- Suspect and trusted exclude each other; the chips are grey when off and coloured when on.
- The list keeps its scroll position when you come back from a changeset.
- Notifications count only changesets closed since the previous check, not the older backlog being verified.
