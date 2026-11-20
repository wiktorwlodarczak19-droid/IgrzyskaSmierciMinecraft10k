# Igrzyska śmierci Minecraft 10k

Plugin Spigot/Paper 1.21.11 z własną whitelistą i logowaniem nowych graczy na ID.

## Komendy
| Komenda | Kto | Opis |
|---|---|---|
| `/spawndlanowychgraczy` | operator | miejsce, w którym czekają niezalogowani |
| `/spawndlagraczy` | operator | miejsce, do którego trafia gracz po zalogowaniu |
| `/playeradd <nick>` | operator | dodaje do whitelisty i generuje 3 ID |
| `/playerremove <nick>` | operator | usuwa z whitelisty |
| `/zaloguj <id zaproszenia> <id gracza> <id sesji>` | gracz | jedyna komenda działająca przed zalogowaniem |

## Jak działa
Niezalogowany gracz jest oślepiony, maksymalnie spowolniony, cofany na spawn przy każdej próbie ruchu,
nie widzi czatu i może użyć tylko `/zaloguj`. Po zalogowaniu trafia na spawn graczy.

## Pobieranie
Gotowy `.jar` znajdziesz w zakładce **Actions** (artefakt z ostatniego builda) albo w **Releases**.

## Budowanie lokalnie
```
mvn package
```
Plik pojawi się w `target/`.

## Konfiguracja (`config.yml`)
- `whitelist` - włącza/wyłącza własną whitelistę
- `ops-pomijaja-logowanie` - OP nie musi się logować
- `wymagaj-logowania-przy-kazdym-wejsciu` - logowanie raz albo przy każdym wejściu
- `max-prob-logowania` - limit błędnych prób
