# Skins des zombies du pack Dernier Refuge

Générés par `gen_zombies.py` (fonction `ajouter(files, jar_path)`, à brancher dans `gen_refuge.py`).
Aperçus : `../apercus/zombies.png` (chaque type de face et de dos, puis l'élite) et
`../apercus/zombies_textures.png` (textures brutes, pour contrôler la mise en page UV).
`python gen_zombies.py` écrit `test_zombies.zip` (pack.mcmeta d'EemicraftPack-b8b58bca) et le passe à
`verifier_pack.py --vanilla <26.3.jar> --vanilla-overrides` : 0 erreur (24 avertissements « texture d'entité »,
attendus : ce sont des textures d'équipement, pas d'objets). **Rien n'a encore été vu en jeu.**

Famille commune : peau gris-vert, coulures noires de la pluie (traces verticales qui partent du haut),
vêtements de civils déchirés, sang sombre. Chaque type a une couleur dominante lisible de loin.

## Méthode

1. **Textures de base des entités** (le pack n'est chargé que dans les mondes du mode, donc sans effet ailleurs).
   Un EntityType utilisé par un seul type porte tout son look :
   | Fichier remplacé (`assets/minecraft/textures/entity/...`) | Type |
   |---|---|
   | `zombie/zombie.png` | corps commun + tenue du **Rôdeur** (chemise bleue, jean, chaussures) ; sert de dessous aux 5 autres types en zombie |
   | `zombie/husk.png` | **Cracheur** (peau vert maladif, pustules, bave d'acide, débardeur) |
   | `zombie/drowned.png` + `drowned_outer_layer.png` | **Noyé** (peau bleuie, yeux cyan, marinière, caban, bonnet, algues) |
   | `zombie_villager/zombie_villager.png` | **Hurleur** : tête de villageois, le « nez » est retexturé en mâchoire béante (gorge noire, dents), cheveux longs (calque chapeau), grande blouse blanche jusqu'aux genoux, pieds nus |
   | `zombie_villager/type/*.png`, `profession/*.png`, `profession_level/*.png` (adultes) | rendues transparentes (mêmes tailles que le vanilla) |
   Les textures bébé ne sont pas touchées (le plugin fait `setAdult()`).
2. **Tenues** pour les types qui partagent `zombie` : équipement avec composant `equippable` et `asset_id`
   `refuge:zombie_<type>` (élite : `refuge:zombie_<type>_elite`). Format vérifié dans le jar 26.3
   (`assets/minecraft/equipment/iron.json`) :
   - `assets/refuge/equipment/zombie_<id>.json` : `{"layers": {"humanoid": [{"texture": "refuge:zombie_<id>"}], "humanoid_leggings": [...]}}`
   - `assets/refuge/textures/entity/equipment/humanoid/zombie_<id>.png` (64×32 : casque = tête, plastron = corps + bras, bottes = bas des jambes)
   - `assets/refuge/textures/entity/equipment/humanoid_leggings/zombie_<id>.png` (64×32 : taille + jambes)
   Le rendu d'armure est en « cutout » : les pixels transparents laissent voir le corps (mains, visage sous la capuche…).
   Vérifié au bytecode 26.3 : `HumanoidArmorLayer.shouldRender(ItemStack, EquipmentSlot)` = composant EQUIPPABLE avec un
   `assetId` et le bon emplacement ; `LivingEntityRenderer` ne dessine pas l'objet porté sur la tête dans ce cas.
3. **Élites** : même tenue + yeux dorés (casque, ligne des yeux) + brassard doré sur les deux bras (plastron).
   Le Hurleur élite n'a que le brassard : la tête d'armure du villageois zombie n'a pas la forme de sa tête (8×10).

| Type | Base | Pièces (normal) | Pièces (élite) | Identité |
|---|---|---|---|---|
| Rôdeur | zombie | aucune | casque, plastron | chemise bleue, jean |
| Coureur | zombie | casque, plastron, jambières, bottes | idem | sweat à capuche rouge à bandes blanches, griffures, jogging gris, baskets |
| Brute | zombie | casque, plastron, jambières, bottes | idem | casque de chantier jaune, salopette orange à bande réfléchissante, bras nus (la peau est sur la couche d'armure, gonflée d'un pixel : bras plus gros), chaussures de sécurité |
| Hurleur | zombie_villager | aucune | plastron | cheveux longs noirs, bouche béante, blouse blanche |
| Cracheur | husk | aucune | casque, plastron | vert acide, pustules jaunes |
| Gonflé | zombie | casque, plastron, jambières | idem | peau violacée tendue, veines vert lumineux, débardeur trop petit, short |
| Noyé | drowned | aucune | casque, plastron | bonnet rouge, caban marine, marinière, algues |
| Rampant | zombie | casque, plastron, jambières | idem | presque noir : haillons, cheveux filasse, yeux pâles |
| Blindé | zombie | casque, plastron, jambières, bottes | idem | tenue bleu nuit, gilet pare-balles, casque à visière, gants, rangers |

## Code pour le plugin (à mettre dans `Zombies.setup`, Zombies.java non modifié ici)

API vérifiée au javap sur paper-api 26.3.build.218 : `Equippable.equippable(EquipmentSlot)` → `Builder.assetId(Key)`,
`ItemStack.setData(DataComponentType.Valued<T>, DataComponentBuilder<T>)`, `ZombieVillager.setVillagerProfession`,
`Villager.Profession.NONE`, `Villager.Type.PLAINS`.

```java
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.EquipmentSlot;
import java.util.Locale;

/** Pièce de tenue invisible ailleurs que sur le zombie : papier (aucune armure, aucun modèle porté). */
private static ItemStack tenue(Type t, boolean elite, EquipmentSlot slot) {
    ItemStack it = ItemStack.of(Material.PAPER);
    String id = "zombie_" + t.name().toLowerCase(Locale.ROOT) + (elite ? "_elite" : "");
    it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).assetId(Key.key("refuge", id)));
    return it;
}

private static final Map<Type, List<EquipmentSlot>> PIECES = Map.of(
    Type.RODEUR, List.of(), Type.HURLEUR, List.of(), Type.CRACHEUR, List.of(), Type.NOYE, List.of(),
    Type.COUREUR, List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET),
    Type.BRUTE,   List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET),
    Type.BLINDE,  List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET),
    Type.GONFLE,  List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS),
    Type.RAMPANT, List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS));

private static void habiller(Mob m, Type t, boolean elite) {
    EntityEquipment eq = m.getEquipment();
    Set<EquipmentSlot> slots = new LinkedHashSet<>(PIECES.get(t));
    if (elite) {                       // yeux dorés (casque) + brassard doré (plastron)
        if (t != Type.HURLEUR) slots.add(EquipmentSlot.HEAD);
        slots.add(EquipmentSlot.CHEST);
    }
    for (EquipmentSlot s : slots) {
        eq.setItem(s, tenue(t, elite, s));
        eq.setDropChance(s, 0f);
    }
    if (m instanceof ZombieVillager zv) {   // Hurleur : aucune surcouche de métier (et pas de calque « hat » masqué)
        zv.setVillagerProfession(Villager.Profession.NONE);
        zv.setVillagerType(Villager.Type.PLAINS);
    }
}
```

À changer dans `setup` : retirer l'armure de cuir verte du Gonflé et l'armure de fer du Blindé (remplacées par la tenue)
et donner au Blindé son armure par attribut (le set de fer valait 15 points) : `attr(m, Attribute.ARMOR, 15)`.
Le papier ne donne aucun point d'armure ; un Blindé à `0` d'armure deviendrait aussi fragile qu'un Rôdeur.

## À valider en jeu (pack Refuge chargé)

- Rendu des tenues sur un zombie : couches bien alignées, transparences (mains et visage du Coureur et de la Brute visibles), couleur du papier jamais visible.
- Le Hurleur : mâchoire (cube du nez) et cheveux du calque chapeau ; vérifier que `setVillagerProfession(NONE)` passe bien (sinon le calque chapeau peut être masqué par la métadonnée d'un métier).
- Brute : taille ×1,4 + bras épaissis par la couche d'armure ; la peau d'armure est dessinée avec la teinte d'armure (pas d'ombrage d'entité différent attendu, à regarder).
- Rampant en pose « nage » : il est presque noir, lisible surtout par ses yeux pâles ; à voir sous une table.
- Gonflé (taille ×1,15) : les veines ne brillent pas réellement (une texture d'armure n'est pas émissive).
- Élites : yeux dorés sur la couche du casque, donc un pixel devant le visage.
- Noyé : la couche extérieure vanilla est dessinée seulement si le client la gère comme en vanilla (rien de spécial à faire).
