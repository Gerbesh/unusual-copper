# Project workflow

- The user performs in-game verification. Do not launch Minecraft clients, GameTest servers, automated in-game smoke profiles, or the user's live server for verification unless the user explicitly requests that run. Build, compilation, JUnit and static checks are allowed.
- When the user requests deployment to the client and server, preserve the previous JARs, verify the installed checksums, and report the installed version. Deployment alone is not a request to launch the game or server.
- Investigate live-server shutdowns from logs first. Distinguish the initial shutdown trigger from errors emitted while mods are shutting down; do not claim a root cause without evidence.
