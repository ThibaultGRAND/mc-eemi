package fr.eemicraft.abysse;

import net.kyori.adventure.title.TitlePart;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Strates 4 (La Mer de Cristal) et 5 (Le Néant) : dangers d'ambiance, paliers d'armure (armure « ordinaire »
 * percée selon la strate), bonus de panoplie, élixirs, et la fin actuelle du contenu (gardien de la strate 5).
 */
final class Profondeurs {

    private static final Random RANDOM = new Random();
    /** Fin de protection (ms) donnée par l'Élixir de silence (strate 4) et le Sérum de gravité (strate 5). */
    static final Map<UUID, Long> SILENCE = new HashMap<>(), GRAVITY = new HashMap<>();

    private Profondeurs() {
    }

    // ------------------------------------------------------------------ paliers d'armure

    /**
     * Palier d'une pièce d'armure : 0 vanilla, 1 bronze / masque (et toute autre pièce de l'Abysse), 2 acier,
     * 3 abyssale ou plastron réfrigéré, 4 cristal, 5 Néant.
     */
    static int armorTier(String id) {
        if (id == null) return 0;
        if (id.endsWith("_neant")) return 5;
        if (id.endsWith("_cristal")) return 4;
        if (id.endsWith("_abyssal") || id.endsWith("_abyssales") || id.equals("plastron_refrigere")) return 3;
        if (id.endsWith("_acier") || id.equals("casque_filtrant")) return 2;
        return 1;
    }

    /** Palier d'armure attendu dans une strate : en dessous, les coups des monstres traversent la pièce. */
    static int armorNeed(int strata) {
        return strata >= 5 ? 5 : strata == 4 ? 4 : strata == 3 ? 1 : 0;
    }

    /**
     * Supplément de dégâts des monstres de cette strate : +15 % par pièce en dessous du palier attendu, +30 % si elle a
     * deux paliers (ou plus) de retard. Strate 3 : seules les pièces vanilla sont percées (comme en 2.3.0).
     */
    static double pierce(int strata, ItemStack[] armor) {
        int need = armorNeed(strata);
        double extra = 0;
        for (ItemStack it : armor) {
            if (it == null || it.getType().isAir()) continue;
            int gap = need - armorTier(Items.id(it));
            if (gap > 0) extra += 0.15 * Math.min(2, gap);
        }
        return extra;
    }

    static String pierceText(int strata) {
        return switch (strata) {
            case 4 -> "§dLes éclats de cristal traversent ton armure... §7(panoplie de cristal)";
            case 5 -> "§5Le Néant traverse ton armure... §7(panoplie du Néant)";
            default -> "§6Le feu des forges traverse ton armure ordinaire... §7(armure d'acier ou abyssale)";
        };
    }

    static int countTier(Player p, int tier) {
        int n = 0;
        for (ItemStack it : p.getInventory().getArmorContents()) {
            String id = Items.id(it);
            if (id != null && armorTier(id) >= tier) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ chaque seconde, dans une strate

    /** Bonus des panoplies de cristal et du Néant, et dangers des strates 4 et 5. */
    static void environment(EemiAbysse plugin, Player p, Gen.Strata s) {
        int cristal = countTier(p, 4), neant = countTier(p, 5);
        if (cristal >= 4) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, neant >= 4 ? 1 : 0, true, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, true));
        }
        if (s.n == 4) resonance(p, cristal);
        else if (s.n == 5) vide(p, neant);
    }

    /**
     * Résonance de la Mer de Cristal : toutes les 8 s, 1,5 cœur et Fatigue de minage I (10 s), sauf avec l'Élixir de
     * silence, le Heaume de cristal, ou 4 pièces de cristal (ou du Néant).
     */
    private static void resonance(Player p, int cristal) {
        if (RANDOM.nextInt(3) == 0) p.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 3, 6, 3, 6, 0.01);
        if (p.getTicksLived() / 20 % 8 != 0) return;
        if (SILENCE.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        String helmet = Items.id(p.getInventory().getHelmet());
        if ("heaume_cristal".equals(helmet) || "casque_neant".equals(helmet) || cristal >= 4) return;
        p.damage(3);
        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 0));
        p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1, 0.6f);
        p.sendActionBar(Txt.c("§dLes cristaux résonnent dans ton crâne... §7(Élixir de silence, Heaume de cristal)"));
    }

    /**
     * Aspiration du Néant : toutes les 10 s, Lévitation 1,5 s, Ténèbres 4 s et 1 cœur, sauf avec le Sérum de gravité,
     * les Bottes du Néant, ou 4 pièces du Néant.
     */
    private static void vide(Player p, int neant) {
        if (RANDOM.nextInt(3) == 0) p.spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 10, 5, 3, 5, 0.02);
        if (p.getTicksLived() / 20 % 10 != 0) return;
        if (GRAVITY.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        if ("bottes_neant".equals(Items.id(p.getInventory().getBoots())) || neant >= 4) return;
        p.damage(2);
        p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0));
        p.playSound(p, Sound.BLOCK_PORTAL_TRAVEL, 0.3f, 1.8f);
        p.sendActionBar(Txt.c("§5Le vide t'aspire... §7(Sérum de gravité, Bottes du Néant)"));
    }

    /** Boissons des strates 4 et 5 (Cuisine). Renvoie vrai si l'objet en était une. */
    static boolean drink(Player p, String id) {
        switch (id) {
            case "elixir_silence" -> {
                SILENCE.put(p.getUniqueId(), System.currentTimeMillis() + 4 * 60_000L);
                p.removePotionEffect(PotionEffectType.MINING_FATIGUE);
                p.sendActionBar(Txt.c("§dLa résonance ne te touche plus pendant 4 min."));
                return true;
            }
            case "serum_gravite" -> {
                GRAVITY.put(p.getUniqueId(), System.currentTimeMillis() + 4 * 60_000L);
                p.removePotionEffect(PotionEffectType.LEVITATION);
                p.sendActionBar(Txt.c("§5Le vide ne t'aspire plus pendant 4 min."));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Vol de vie des lames de cristal et du Néant (l'épée abyssale est gérée à part). */
    static double lifeSteal(String weapon) {
        if ("lame_neant".equals(weapon)) return 0.15;
        if ("epee_cristal".equals(weapon)) return 0.10;
        return 0;
    }

    /** Butin en plus des tonneaux des campements des strates 4 et 5. */
    static void barrelLoot(Gen.Strata s, java.util.List<ItemStack> loot, Random r) {
        if (s.n == 4) {
            loot.add(Items.make("cristal_resonant", 2 + r.nextInt(4)));
            if (r.nextInt(3) == 0) loot.add(Items.make("elixir_silence", 1));
        } else if (s.n == 5) {
            loot.add(Items.make("essence_vide", 1 + r.nextInt(3)));
            if (r.nextInt(3) == 0) loot.add(Items.make("serum_gravite", 1));
        }
    }

    // ------------------------------------------------------------------ fin actuelle du contenu

    /** Le Veilleur du Néant est tombé : récompense de fin (la première fois) et message de fin. */
    static void finale(EemiAbysse plugin, Player p, boolean first) {
        if (!first) {
            p.sendMessage(Txt.PREFIX + "§7Le Veilleur du Néant reviendra toujours. Toi aussi, visiblement.");
            return;
        }
        double gift = EemiAbysse.gain(20000);
        plugin.store.company(p).money += gift;
        for (ItemStack rest : p.getInventory().addItem(Items.make("medaille_varenne", 1)).values()) p.getWorld().dropItem(p.getLocation(), rest);
        p.sendTitlePart(TitlePart.TITLE, Txt.c("§5§lLE FOND DE L'ABYSSE"));
        p.sendTitlePart(TitlePart.SUBTITLE, Txt.c("§7Varenne repose enfin"));
        p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 0.8f);
        p.sendMessage("");
        p.sendMessage(Txt.PREFIX + "§5§lTu as atteint le fond de l'Abysse... pour l'instant.");
        p.sendMessage(Txt.PREFIX + "§7Varenne, ou ce qu'il en restait, ne veille plus. Ses carnets s'arrêtent ici.");
        p.sendMessage(Txt.PREFIX + "§7Récompense : §6Médaille de Varenne §7et " + Txt.money(gift) + "§7.");
        p.sendMessage(Txt.PREFIX + "§7Les ingénieurs creusent encore : d'autres strates viendront.");
        plugin.broadcastAbysse(Txt.PREFIX + "§5§l" + p.getName() + " §dest descendu jusqu'au fond de l'Abysse !");
    }
}
