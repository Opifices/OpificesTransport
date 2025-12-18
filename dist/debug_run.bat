@echo off
echo Starting ModernTorrentClient in DEBUG mode...
echo Logs will be saved to debug_log.txt
java -javaagent:replay-agent.jar --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -jar ModernTorrentClient.jar > debug_log.txt 2>&1
echo Application closed. Check debug_log.txt for errors.
pause
