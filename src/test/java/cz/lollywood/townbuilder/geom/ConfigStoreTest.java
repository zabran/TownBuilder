package cz.lollywood.townbuilder.geom;

import cz.lollywood.townbuilder.config.ConfigStore;
import cz.lollywood.townbuilder.config.Defs;
import cz.lollywood.townbuilder.config.TagMatcher;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Random;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    @Test
    void theShippedExamplesParse() {
        ConfigStore s = new ConfigStore(Path.of("src/main/resources"), Logger.getLogger("test"));
        s.load();
        assertEquals(2, s.layouts.size());
        Defs.PlotLayout l = TagMatcher.best(s.layouts, TagMatcher.parse("City, Union ,residential"), new Random(1));
        assertNotNull(l);
        assertEquals("union_townhouse", l.id);           // the closest fit: no extra tags
        assertEquals(150, l.minPlotArea);                  // snake_case keys reach the fields
        assertArrayEquals(new int[]{1, 2}, l.storiesRange);
        assertEquals("minecraft:spruce_fence", s.fences.get(0).fillBlock);
        assertEquals(1, s.styles.get(0).roofOverhang);
        // no .schem files are shipped: every sidecar is reported and skipped
        assertTrue(s.walls.isEmpty());
        assertTrue(s.problems.stream().anyMatch(p -> p.contains("union_wall_door_3") && p.contains(".schem")));
    }
}
