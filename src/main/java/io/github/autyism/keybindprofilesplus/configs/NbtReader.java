package io.github.autyism.keybindprofilesplus.configs;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads the game's NBT format (plain, gzip or zlib) without Minecraft's classes, so it also works
 * before the game is loaded. It only walks the data: every named tag and every text value is
 * reported to a visitor. Used to recognise login data in .nbt settings files and to tell a real NBT
 * file from a broken one.
 */
public final class NbtReader {
    private static final int MAX_DEPTH = 256;
    private static final int MAX_ELEMENTS = 2_000_000;

    /** Called for every tag: its name (empty inside lists) and, for text tags, the text (otherwise null). */
    public interface Visitor {
        void tag(String name, String text);
    }

    private final DataInputStream in;
    private final Visitor visitor;
    private int elements;

    private NbtReader(InputStream in, Visitor visitor) {
        this.in = new DataInputStream(in);
        this.visitor = visitor;
    }

    /** Walks the file; false when it is not readable NBT. */
    public static boolean walk(byte[] data, Visitor visitor) {
        try (InputStream raw = open(data)) {
            NbtReader reader = new NbtReader(raw, visitor);
            int type = reader.in.readUnsignedByte();
            if (type != 10 && type != 9) {
                return false;
            }
            reader.readString();
            reader.payload(type, 0);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static InputStream open(byte[] data) throws IOException {
        if (data.length >= 2 && (data[0] & 0xff) == 0x1f && (data[1] & 0xff) == 0x8b) {
            return new GZIPInputStream(new ByteArrayInputStream(data));
        }
        if (data.length >= 2 && (data[0] & 0xff) == 0x78 && (((data[0] & 0xff) << 8) | (data[1] & 0xff)) % 31 == 0) {
            return new InflaterInputStream(new ByteArrayInputStream(data));
        }
        return new ByteArrayInputStream(data);
    }

    private String readString() throws IOException {
        int length = in.readUnsignedShort();
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("truncated");
        }
        // Java's modified UTF-8 differs from UTF-8 only for characters nobody puts in settings.
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void payload(int type, int depth) throws IOException {
        if (depth > MAX_DEPTH || ++elements > MAX_ELEMENTS) {
            throw new IOException("too deep or too large");
        }
        switch (type) {
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3, 5 -> in.skipNBytes(4);
            case 4, 6 -> in.skipNBytes(8);
            case 7 -> skipArray(1);
            case 8 -> visitor.tag("", readString());
            case 9 -> {
                int elementType = in.readUnsignedByte();
                int count = in.readInt();
                if (count < 0 || (count > 0 && (elementType == 0 || elementType > 12))) {
                    throw new IOException("bad list");
                }
                for (int i = 0; i < count; i++) {
                    payload(elementType, depth + 1);
                }
            }
            case 10 -> {
                while (true) {
                    int childType = in.readUnsignedByte();
                    if (childType == 0) {
                        break;
                    }
                    if (childType > 12) {
                        throw new IOException("bad tag type " + childType);
                    }
                    String name = readString();
                    if (childType == 8) {
                        visitor.tag(name, readString());
                    } else {
                        visitor.tag(name, null);
                        payload(childType, depth + 1);
                    }
                }
            }
            case 11 -> skipArray(4);
            case 12 -> skipArray(8);
            default -> throw new IOException("bad tag type " + type);
        }
    }

    private void skipArray(int elementSize) throws IOException {
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("bad array");
        }
        in.skipNBytes((long) count * elementSize);
    }
}
