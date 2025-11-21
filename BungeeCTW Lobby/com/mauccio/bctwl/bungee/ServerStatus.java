package com.mauccio.bctwl.bungee;

public enum ServerStatus {
    ACTIVE_GAME((short)13, "Active"),       // GREEN WOOL
    CHANGING_MAP((short)4, "Changing Map"),  // YELLOW WOOL
    INACTIVE((short)14, "Inactive");                   // RED WOOL

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


