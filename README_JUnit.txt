NULL//DESCENT - JUnit 5 test suite (Java 17)

Install:
1. Extract the ZIP into the ROOT of your existing DungeonCrawlerProject.
2. This adds test/com/dungeoncrawler/*.java, run_tests.sh, and README_JUnit.txt.
   It does not replace or edit your game's existing src/ files.
3. Keep your existing lib/jline.jar and src/ directory in place.
4. From the project root, run: chmod +x run_tests.sh && ./run_tests.sh

The script downloads the JUnit Platform Console Standalone 1.10.0 JAR from Maven Central
into lib/ on its first run. An internet connection is needed once. If download fails,
you can manually put that JAR in lib/ using exactly the above filename.

Test files:
- InventoryTest.java: sorting, Comparable, inventory, HP, reset
- MechanicsTest.java: movement, combat, duplicate rewards, firewall traps
- VisibilityTest.java: recursion-based visibility, explored memory, walls
- SaveLoadTest.java: state roundtrip, monster HP, save validation and corruption
- TestMaps.java: deterministic test fixtures (not itself a test)

Tests don't modify bgm.wav, savegame.txt, or live game source. SaveLoadTest uses
JUnit's isolated @TempDir feature.

Scope of verification: automated tests cover accessible domain methods. The private
GameEngine.useItem menu implementation is not called directly; interactive menu controls
should still be checked manually. Interactive JLine controls and audio cannot be verified
by pure unit tests.
