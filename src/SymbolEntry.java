import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;

public class SymbolEntry {
    public String name;
    public SymbolType symbolType;
    public boolean isInitialized;

    public enum SymbolType{
        VAR, TYPE, FUNC
    }

    public SymbolEntry(SymbolEntry s) {
        this.name = s.name;
        this.symbolType = s.symbolType;
    }

    public SymbolEntry(String name, SymbolType type) {
        this.name = name;
        this.symbolType = type;
    }

    public void setType(String type) {
    }

    @Override
    public int hashCode() {
        return symbolType.hashCode() + name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof SymbolEntry)) {
            return false;
        }
        SymbolEntry that = (SymbolEntry) o;
        if (this.name.equals(that.name) && this.symbolType.equals(that.symbolType)) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return name + " " + symbolType;
    }
}