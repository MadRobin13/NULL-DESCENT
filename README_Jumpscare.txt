NULL//DESCENT - CYBERPUNK HORROR JUMPSCARE

Add or replace these files under the project root:

REPLACE
src/com/dungeoncrawler/GameEngine.java
src/com/dungeoncrawler/engine/MusicPlayer.java

ADD
src/com/dungeoncrawler/engine/CyberpunkSpriteGenerator.java
src/com/dungeoncrawler/engine/JumpscareManager.java
src/com/dungeoncrawler/JumpscarePreview.java

ASSETS
jumpscare1.png
jumpscare2.png

AUDIO (not included)
Place a short, original or properly licensed, PCM-compatible jumpscare.wav in the
project root, next to bgm.wav. The animation still runs without it.

Build and preview from the project root:
mkdir -p bin
javac --release 17 -cp "lib/*" -d bin $(find src -name "*.java")
java -cp "bin:lib/*" com.dungeoncrawler.JumpscarePreview --no-flash
java -cp "bin:lib/*" com.dungeoncrawler.JumpscarePreview

Run the game:
java -cp "bin:lib/*" com.dungeoncrawler.Main

Regenerate image assets (optional):
java -cp "bin:lib/*" com.dungeoncrawler.engine.CyberpunkSpriteGenerator

During gameplay, press F to enable/disable flashes. Monster-caused death alone
plays the animation. Trap/cache deaths do not. Music resumes after R/reloading
an alive character.

These changes do not modify any save file or JUnit test source. Run ./run_tests.sh
to verify the existing 29 tests on your Mac.

WARNING: with flashes enabled this animation contains short white-screen flashes.
Use F to disable them before testing if you are sensitive to flashing lights.
