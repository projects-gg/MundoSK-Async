# MundoSK-Async
Revisited version of MundoSK's async feature for the modern Skript versions.

## Examples of usage:
You can't access the locals variables in sync section and effect which created before their creation.
```
command /dispatchmeasync:
  trigger:
    set {_sender} to sender
    async:
      send "Hello %{_sender}%!"
      sync:
        send "Sync section!"
```
```
command /dispatchmeasynceffect:
  trigger:
    send "This is not async!"
    async
    send "This is async!"
    sync
    send "Welcome back to sync!"
```

## Special thanks:
- **Tlatoani**: Creator of MundoSK
- **nanodn**: Forking MundoSK as MundoSK-Async
- **TPGamesNL**: Skript 2.6+ compatibility

## Self-build:
Run the following command in the root directory:
```
./gradlew build
```
The output will extracted to `./build/libs` folder.
