# SylvETA

Paper 26.3 plugin for metro ETA boards displayed on filled maps.

## Build

Requires Java 25 and Maven. Build with `mvn package`, then place `target/sylveta-1.0.0.jar` in the server's `plugins` directory and restart.

## Lines and board images

On startup, the plugin creates `plugins/SylvETA/line numbers/`. Put line-number PNGs in that folder, then register each file with `/eta line create <id> <file.png>`. The ID is used by board commands, and the specified PNG is scaled to fit in the top-left corner. Use a transparent PNG for best results.

## Commands

- `/eta create <id> <minutes> <destination...> <line>` creates a two-map-wide board and gives both maps to the player. The line must be registered and have a matching PNG. Hold the left map in your main hand and right-click the leftmost block of a vertical wall; the plugin places both item frames automatically. Keep both maps in your inventory.
- `/eta set <destination...> <number>` updates the uniquely matching destination's countdown; the number is interpreted as minutes.
- `/eta set <id> <minutes> <destination...>` replaces a board's destinations and initializes each countdown.
- `/eta set <id> <destination-number> <minutes>` changes only that destination's countdown when destination names are duplicated or you prefer an index.
- `/eta arrived <id> <destination-name|destination-number>` marks only the selected destination as `Arrived`.
- `/eta get <id>` gives a player duplicate left and right maps that stay synced with the board.
- `/eta infotext <line|global> <text>` sets a scrolling ticker for one registered line or every board. Line-specific and global messages appear together when both are set.
- Infotext accepts Bukkit color codes with an ampersand prefix, for example `/eta infotext global &aNext train &farrives soon`.
- `/eta infotext disable <line>` clears that line's text and blanks its ticker, including any global text. `/eta infotext disable global` clears all infotext and blanks every ticker.
- `/eta list` lists all boards.
- `/eta delete <id>` removes a board; maps already placed will stop updating.
- `/eta line create <id> <file.png>` registers a line using a PNG from the `line numbers` folder.

All commands can be run from command blocks without `sylveta.admin`. For map delivery, add a target: `/eta create <id> <minutes> <destination...> <line> --player <player|@p>` and `/eta get <id> <player|@p>`.

ETAs decrease once per minute and display `Arriving` at zero until reset. Ticker text scrolls left every three seconds. Map redraws update the existing map views without replacing item-frame contents or playing sounds. Board data, line registrations, infotext, and both map IDs persist through server restarts. Board management commands require `sylveta.admin`, granted to operators by default; `/eta get` is available to all players. Names may contain only letters, numbers, underscores, and hyphens.

Separate destinations with `|`, for example `Olympia | Westlake`. Two destinations stay stacked on the board; three or more rotate through pairs every 10 seconds. Each destination has an independent countdown, initialized from the minutes supplied to `/eta create`; use `/eta set <destination> <number>` to update a unique destination in minutes. If a destination name occurs more than once, use `/eta set <id> <destination-number> <minutes>`. `/eta list` shows the 1-based destination numbers.
