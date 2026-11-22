# Dernier Refuge – bugs et retouches à faire (grosse repasse quand la liste sera complète)

Liste tenue au fil des tests de Thibault. Ne pas corriger au fil de l'eau sauf demande.

| Date | Où | Constat | Piste |
|---|---|---|---|
| 13/11/2026 | Armes, bouteille incendiaire | Les zombies sont attirés par la bouteille incendiaire comme par la fusée d'appât | Le bruit de l'impact (verre cassé / feu) passe sans doute par le même appel que l'appât dans `Armes.onThrownHit` : vérifier le rayon émis et que seul l'appât attire durablement |
| 13/11/2026 | `/refuge zone verifier` | « 2809 conteneurs dont le bloc ne correspond pas au type noté » : faux positif, la vérification compare le type FRANÇAIS de `bourgade_containers.yml` (tonneau, four…) au nom anglais du bloc. Le remplissage, lui, gère bien le français. Restent ~30 vrais écarts (3 conteneurs hors bornes en y 10-15, ~30 positions sans conteneur après conversion) | Traduire les types dans la vérification ; régénérer la liste des conteneurs depuis le monde converti |
