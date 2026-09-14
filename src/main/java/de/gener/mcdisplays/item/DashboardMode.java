package de.gener.mcdisplays.item;

/**
 * The six Colony Dashboard display modes, mirroring the tabs from the
 * Colony Dashboard mod by Bludeuwedd.
 */
public enum DashboardMode {
    CITIZENS("citizens"),
    BUILDERS("builders"),
    SICK("sick"),
    DEATHS("deaths"),
    STATISTICS("statistics"),
    WAREHOUSE("warehouse");

    private final String id;

    DashboardMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public DashboardMode next() {
        DashboardMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static DashboardMode fromId(String id) {
        for (DashboardMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return CITIZENS;
    }
}
