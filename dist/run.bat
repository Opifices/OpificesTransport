@echo off
echo Starting OpificesTransport (Optimized + Adaptive heuristics + Replay4J)...
java -javaagent:replay-agent.jar --add-modules jdk.incubator.vector --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED -jar ModernTorrentClient.jar
pause
