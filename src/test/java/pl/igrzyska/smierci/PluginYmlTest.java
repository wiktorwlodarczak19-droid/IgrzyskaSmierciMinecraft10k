package pl.igrzyska.smierci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Pilnuje spójności plugin.yml z kodem (klasa główna, wersja API, komendy). */
class PluginYmlTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> wczytaj() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(in, "Brak pliku plugin.yml w zasobach");
            return (Map<String, Object>) new Yaml().load(in);
        }
    }

    @Test
    void klasaGlownaIstniejeIJestPluginem() throws Exception {
        Map<String, Object> yml = wczytaj();
        String main = (String) yml.get("main");
        assertEquals("pl.igrzyska.smierci.IgrzyskaSmierci", main);
        Class<?> c = Class.forName(main, false, getClass().getClassLoader());
        assertTrue(JavaPlugin.class.isAssignableFrom(c), "Klasa główna musi dziedziczyć po JavaPlugin");
    }

    @Test
    void wersjaApiTo121() throws Exception {
        assertEquals("1.21", String.valueOf(wczytaj().get("api-version")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void wszystkieKomendyZadeklarowane() throws Exception {
        Map<String, Object> komendy = (Map<String, Object>) wczytaj().get("commands");
        assertNotNull(komendy);
        assertTrue(komendy.keySet().containsAll(
                Set.of("zaloguj", "playeradd", "playerremove", "spawndlanowychgraczy", "spawndlagraczy")),
                "Brakuje którejś komendy w plugin.yml");
    }
}
