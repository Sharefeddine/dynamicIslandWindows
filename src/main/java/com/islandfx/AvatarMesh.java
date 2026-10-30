package com.islandfx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Small OBJ subset loader for the bundled, triangulated CC0 head excerpt. */
final class AvatarMesh {
    record Data(float[] points, int[] faces) { }
    private AvatarMesh() { }
    static Data loadHead() throws IOException {
        try (InputStream input = AvatarMesh.class.getResourceAsStream("data/makehuman-head.obj")) {
            if (input == null) throw new IOException("3D head resource is missing");
            return read(input);
        }
    }
    static Data read(InputStream input) throws IOException {
        List<Float> points = new ArrayList<>();
        List<Integer> faces = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] parts = line.trim().split("\\s+");
                try {
                    if (parts.length == 4 && parts[0].equals("v")) {
                        double x = Double.parseDouble(parts[1]), y = Double.parseDouble(parts[2]), z = Double.parseDouble(parts[3]);
                        // MakeHuman's Y points up and its face points toward +Z; JavaFX's camera faces +Z.
                        points.add((float) (20 + x * 10.5));
                        points.add((float) (14 - (y - 7.2841) * 10.5));
                        points.add((float) (-(z - 1.2453) * 10.5));
                    } else if (parts.length == 4 && parts[0].equals("f")) {
                        for (int i = 1; i < 4; i++) {
                            int vertex = Integer.parseInt(parts[i]) - 1;
                            if (vertex < 0 || vertex >= points.size() / 3) throw new IOException("Invalid head mesh index");
                            faces.add(vertex); faces.add(0); // one neutral texture coordinate per vertex
                        }
                    }
                } catch (NumberFormatException invalid) { throw new IOException("Invalid head mesh", invalid); }
            }
        }
        if (points.isEmpty() || faces.isEmpty()) throw new IOException("Empty head mesh");
        float[] vertexArray = new float[points.size()];
        int[] faceArray = new int[faces.size()];
        for (int i = 0; i < points.size(); i++) vertexArray[i] = points.get(i);
        for (int i = 0; i < faces.size(); i++) faceArray[i] = faces.get(i);
        return new Data(vertexArray, faceArray);
    }
}
