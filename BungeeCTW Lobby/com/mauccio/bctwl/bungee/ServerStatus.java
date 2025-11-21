package com.mauccio.bctwl.bungee;

public enum ServerStatus {
    ACTIVE_GAME((short)13, "En Juego y Activo"),       // Lana verde
    CHANGING_MAP((short)4, "Cambiando mapa, Activo"),  // Lana amarilla
    INACTIVE((short)14, "Inactivo");                   // Lana roja

    private final short colorData;
    private final String description;

    ServerStatus(short colorData, String description) {
        this.colorData = colorData;
        this.description = description;
    }

    public short getColorData() {
        return colorData;
    }

    public String getDescription() {
        return description;
    }
}

