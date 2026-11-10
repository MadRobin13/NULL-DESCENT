# NULL//DESCENT

A single-player, turn-based cyberpunk dungeon crawler for Java 17+.
Descend through 10 procedurally generated sectors, fight corrupted programs, and defeat the System Overlord to access the mainframe.

## Requirements

- Java Development Kit 17 or later
- An ANSI/Unicode-capable interactive terminal (Windows Terminal, macOS Terminal, or a modern Linux terminal)
- The included `lib/jline.jar` dependency
- At least 80 columns x 24 rows of terminal space; 100 x 55 enables the expanded layout

Run the following commands **from the project root**.

### Windows PowerShell

```powershell
New-Item -ItemType Directory -Force bin | Out-Null
$files = (Get-ChildItem src -Recurse -Filter *.java).FullName
javac --release 17 -cp "lib/*" -d bin $files
if ($LASTEXITCODE -ne 0) { throw "Compilation failed" }
java -cp "bin;lib/*" com.dungeoncrawler.Main
```

### macOS / Linux (Bash or Zsh)

```bash
mkdir -p bin
find src -name '*.java' -print0 | xargs -0 javac --release 17 -cp 'lib/*' -d bin
java -cp 'bin:lib/*' com.dungeoncrawler.Main
```

## Playing

WASD or arrow keys to move; bump enemies to attack. E interacts with stairs or the final mainframe. I opens the inventory, H the help screen, T the title/story, K saves, L loads, R restarts, F toggles jumpscare flashes, and Q quits. Inventory: 1-5 to use an item, N/P to switch pages. During gameplay, 1/2 sorts the inventory by name/value. Scrolling the mouse or touchpad should not move the player in compatible terminal environments.

On terminals smaller than 100 x 55, the full introduction appears in two pages before gameplay. Enter advances the story, and T opens it again. The compact gameplay layout requires at least 80 x 24.

The program uses an alternate terminal screen and attempts to restore your original terminal state when it exits normally or after Ctrl+C. A forced process kill cannot guarantee cleanup. On Windows use Windows Terminal for the most consistent Unicode rendering.

Audio and image assets (`bgm.wav`, `jumpscare.WAV`, `jumpscare1.png`, `jumpscare2.png`) are expected in the project root. If audio fails, gameplay remains available without sound.

## Tests

On macOS/Linux or Git Bash, run:

```bash
bash run_tests.sh
```

The runner compiles against Java 17 and executes the JUnit 5 suite. If the test JAR isn't installed locally, the first test run attempts to download it from Maven Central. The 74-test baseline covers game state, map generation, visibility, save/load, inventory, rendering, keyboard escape sequences, and asset failures. Automated tests are not a substitute for a native Windows gameplay check.
