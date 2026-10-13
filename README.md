# LifestealCombatLogGuard (plugin, optionnel)

Ce plugin complète le datapack : il tue **instantanément** un joueur (drop de
son stuff + perte de cœur + crédit à l'adversaire) au moment exact où il se
déconnecte alors que son timer de combat tourne encore — au lieu d'attendre
sa reconnexion comme le fait le datapack seul (voir `combat/on_rejoin.mcfunction`).

Il ne fait QUE ça : il lit/écrit les mêmes scoreboards que le datapack
(`hearts`, `combat`, `hc_transfer_ok`...), donc les deux fonctionnent
parfaitement ensemble. **Le datapack fonctionne très bien seul** (le joueur
sera puni à sa reconnexion) ; ce plugin n'ajoute que l'instantanéité.

## ⚠️ Important : je ne peux pas te fournir le .jar déjà compilé
Mon environnement d'exécution n'a pas accès au dépôt Maven de PaperMC
(`repo.papermc.io`), donc je ne peux pas compiler ce plugin moi-même ici.
Je te donne le code source complet et prêt à l'emploi — il te suffit d'une
seule commande pour obtenir le `.jar`.

## Compilation (sur ta machine, une seule fois)
Prérequis : [Maven](https://maven.apache.org/) + **JDK 21**.

```bash
cd lifesteal-combatlog-guard
mvn package
```

Le fichier compilé apparaît dans :
```
target/lifesteal-combatlog-guard.jar
```

## Installation
1. Mets le `.jar` dans le dossier `plugins/` de ton serveur Paper / Purpur /
   Pufferfish.
2. Assure-toi que le **datapack Lifesteal** est bien installé et actif sur le
   monde (le plugin en dépend : il lit ses scoreboards et appelle sa fonction
   `lifesteal:combat/killer_gain_heart`).
3. Redémarre le serveur (ou `/reload`, même si un vrai restart est préférable
   pour un plugin).

## Notes techniques
- `Attribute.GENERIC_MAX_HEALTH` est le nom historique de l'attribut de vie
  max côté API Bukkit/Paper. Sur les toutes dernières versions de Paper, cette
  constante peut apparaître comme dépréciée (au profit d'un système par
  Registry) mais reste fonctionnelle. Si un jour elle est totalement retirée,
  il faudra migrer vers `RegistryAccess`/`RegistryKey.ATTRIBUTE`.
- Le plugin ne duplique pas la détection "qui frappe qui" du datapack : il a
  son propre suivi en mémoire (via `EntityDamageByEntityEvent`), plus précis
  qu'une heuristique de proximité, mais utilisé UNIQUEMENT pour savoir qui
  créditer en cas de combat-log — la détection normale de kill reste gérée
  par le datapack.
- Aucune commande, aucune permission, aucune configuration : il s'active tout
  seul dès qu'il est chargé.
