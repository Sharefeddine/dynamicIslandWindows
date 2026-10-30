package com.islandfx;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AvatarMeshTest {
    @Test void bundledAnatomicalHeadIsARealTriangulatedMesh() throws Exception {
        AvatarMesh.Data head = AvatarMesh.loadHead();
        assertEquals(4244 * 3, head.points().length);
        assertEquals(8440 * 6, head.faces().length);
        for (int i = 0; i < head.faces().length; i += 2) {
            assertTrue(head.faces()[i] >= 0 && head.faces()[i] < head.points().length / 3);
            assertEquals(0, head.faces()[i + 1]);
        }
    }
    @Test void rejectsInvalidTriangleIndexes() {
        String invalid = "v 0 7 1\nf 1 2 3\n";
        assertThrows(java.io.IOException.class, () -> AvatarMesh.read(new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))));
    }
}
