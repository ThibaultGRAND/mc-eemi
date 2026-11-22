package fr.eemicraft.refuge.api;

/** Zone d'expédition. act = acte requis (1 = dès le début). icon = id d'objet du pack (refuge:...) ou null. */
public record ZoneInfo(String id, String name, int act, int minPlayers, int maxPlayers, int freeInstances, String icon) {
}
