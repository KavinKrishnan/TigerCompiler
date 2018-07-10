import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;

public class SymbolTable {
    public LinkedList<HashMap<SymbolEntry, SymbolEntry>> table;
    private String prefix;
    private String s = "";

    public SymbolTable() {
        table = new LinkedList<>();
        prefix = " ";
        //addLevel();
    }

    public void addLevel() {
        table.addFirst(new HashMap<>());
        //System.out.println(prefix + "Symbol Table: entering new scope.");
        prefix = prefix + " ";
    }

    public void removeLevel() {
        //System.out.println(prefix + "Table for current scope:");
        //System.out.println(prefix + table.getFirst().values().toString());
        s += prefix + table.getFirst().values().toString() + "\n";
        prefix = prefix.substring(0, prefix.length() - 1);
        //System.out.println(prefix + "Symbol Table: leaving current scope.");
        table.removeFirst();
    }

    public void printTable() {
        System.out.println("Symbol table: \n" + s);
    }

    public void addEntry(SymbolEntry entry) {
        table.getFirst().put(new SymbolEntry(entry), entry);
    }

    public boolean contains(String name, SymbolEntry.SymbolType type) {
        SymbolEntry entry = new SymbolEntry(name, type);
        for (HashMap<SymbolEntry, SymbolEntry> map : table) {
            if (map.containsKey(entry)) {
                return true;
            }
        }
        return false;
    }

    public SymbolEntry get(String name, SymbolEntry.SymbolType type) {
        SymbolEntry entry = new SymbolEntry(name, type);
        for (HashMap<SymbolEntry, SymbolEntry> map : table) {
            if (map.containsKey(entry)) {
                return map.get(entry);
            }
        }
        return null;
    }

    @Override
    public String toString() {
        String s = "";
        for (HashMap<SymbolEntry, SymbolEntry> t : table) {
            s += t.values().toString() + "\n";
        }
        return s;
    }

}