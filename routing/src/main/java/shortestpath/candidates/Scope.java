package shortestpath.candidates;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import shortestpath.WorldPointUtil;

/**
 * Map boxes the candidates leave out, each with a reason ({@link #RESOURCE}): instances, copies of
 * areas and other places a transport is not the answer. A connector, shortcut, or the unreached end
 * a connector leads to, inside a box is counted as out of scope instead of listed.
 */
public final class Scope {
    public static final String RESOURCE = "/candidates/candidate_scope.tsv";

    private final List<Box> boxes;

    /** A box of the map, inclusive. Plane -1 means every plane. */
    public static final class Box {
        public final int x1, y1, x2, y2, plane;
        public final String reason;

        Box(int x1, int y1, int x2, int y2, int plane, String reason) {
            this.x1 = Math.min(x1, x2); this.x2 = Math.max(x1, x2);
            this.y1 = Math.min(y1, y2); this.y2 = Math.max(y1, y2);
            this.plane = plane;
            this.reason = reason;
        }

        boolean contains(int x, int y, int p) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && (plane < 0 || plane == p);
        }
    }

    public Scope(List<Box> boxes) {
        this.boxes = List.copyOf(boxes);
    }

    public static Scope none() {
        return new Scope(List.of());
    }

    /** Reads {@code x1 y1 x2 y2 plane|* <tab> reason} lines; {@code #} starts a comment. */
    public static Scope read(InputStream stream) {
        List<Box> boxes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t", 2);
                String[] numbers = cells[0].trim().split("\\s+");
                if (numbers.length != 5 || cells.length < 2 || cells[1].isBlank()) {
                    throw new IllegalArgumentException("Bad scope line: " + line);
                }
                boxes.add(new Box(Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]),
                    Integer.parseInt(numbers[2]), Integer.parseInt(numbers[3]),
                    numbers[4].equals("*") ? -1 : Integer.parseInt(numbers[4]), cells[1].trim()));
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return new Scope(boxes);
    }

    public static Scope bundled() {
        try (InputStream stream = Objects.requireNonNull(Scope.class.getResourceAsStream(RESOURCE), RESOURCE)) {
            return read(stream);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public boolean excludes(int x, int y, int plane) {
        for (Box box : boxes) if (box.contains(x, y, plane)) return true;
        return false;
    }

    public boolean excludes(int packedTile) {
        return excludes(WorldPointUtil.unpackWorldX(packedTile), WorldPointUtil.unpackWorldY(packedTile),
            WorldPointUtil.unpackWorldPlane(packedTile));
    }
}
