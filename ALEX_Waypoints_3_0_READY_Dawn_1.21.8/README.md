# ALEX WAYPOINTS 3.0 — Dawn Client / Minecraft 1.21.8

Questa cartella è già organizzata per essere caricata **direttamente nella radice** di un repository GitHub. Non creare una cartella contenitore aggiuntiva.

## Compilazione automatica su GitHub

1. Estrai lo ZIP.
2. Nel repository GitHub esistente, carica i contenuti della cartella estratta nella pagina principale (radice): `.github`, `fabric-mod`, `overlay`, `README.md`.
3. Conferma con **Commit changes**.
4. Il workflow parte automaticamente al push su `main`. Non devi modificare `build.yml`.
5. Al termine, apri Actions e scarica i due artifact.

## Artifact prodotti

- `ALEX-Waypoints-Fabric-Mod`: file JAR del mod Fabric.
- `ALEX-Waypoints-Desktop`: programma EXE Windows.

## Comandi in gioco

- `/alexwp add Casa` — salva la posizione corrente con nome Casa.
- `/alexwp addpos Casa 100 70 200` — salva coordinate precise.
- `/alexwp list` — mostra i waypoint.
- `/alexwp remove Casa` — elimina un waypoint.

Il mod scrive `alex-waypoints-live.json` nella cartella `config` del profilo Fabric in uso. Il programma desktop cerca il file nei profili Dawn sotto `%APPDATA%\.dawn\profiles`.

## Nota importante

Il companion desktop mostra una cattura della finestra Minecraft e disegna un indicatore proiettato usando yaw/pitch e un FOV stimato di 70°. La posizione del marker può richiedere calibrazione e non equivale a un marker renderizzato dal motore grafico di Minecraft. Il progetto è predisposto per GitHub Actions, ma la compilazione deve essere verificata eseguendo il workflow nel repository.
