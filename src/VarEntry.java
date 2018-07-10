import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Queue;
import java.util.regex.Pattern;

public class VarEntry extends SymbolEntry {
    public boolean isAnotherType;
    public String dataType;

    public boolean isInt;

    public boolean isArray;

    public boolean isRecord;
    ArrayList<RecordEntry> recordFields;


    private class RecordEntry {
        private String name;
        private boolean isInt;

        private RecordEntry(String name, boolean isInt) {
            this.name = name;
            this.isInt = isInt;
        }
    }

    public VarEntry(String name, String typeName, TypeEntry type) {
        super(name, SymbolType.VAR);
        this.setType(typeName);
        if (type != null) {
            this.isInt = type.isInt;
            this.isArray = type.isArray;
            this.isRecord = type.isRecord;
            if (isRecord) {
                recordFields = new ArrayList<>(type.recordNames.size());
                for (int i = 0; i < type.recordNames.size(); ++i) {
                    recordFields.add(new RecordEntry(type.recordNames.get(i), type.isIntFields.get(i)));
                }
            }
        }
    }

    @Override
    public void setType(String type) {
        dataType = type;
        if (type.equals("int")) {
            isInt = true;
        } else if (type.equals("float")) {
            isInt = false;
        } else {
            this.isAnotherType = true;
        }
    }

    @Override
    public String toString() {
        return name + " | " + symbolType + " | " + dataType;
    }
}