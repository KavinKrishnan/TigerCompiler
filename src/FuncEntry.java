import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Queue;
import java.util.regex.Pattern;

public class FuncEntry extends SymbolEntry {
    public ArrayList<String> argNames;
    public ArrayList<String> argTypes;

    public boolean isAnotherType;
    public String returnType;

    public boolean isVoid;

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

    public FuncEntry(String name, String typeName, TypeEntry type) {
        super(name, SymbolType.FUNC);
        argNames = new ArrayList<>();
        argTypes = new ArrayList<>();
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
        returnType = type;
        if (type.equals("int")) {
            isInt = true;
        } else if (type.equals("float")) {
            isInt = false;
        } else if (type.equals("")) {
            isVoid = true;
            returnType = "VOID";
        } else {
            this.isAnotherType = true;
        }
    }

    public void addArg(String name, String type) {
        argNames.add(name);
        argTypes.add(type);
    }

    @Override
    public String toString() {
        String s = "";
        s += name + " | " + symbolType + " | returns: " + returnType + " | args: (";
        for (int i = 0; i < argNames.size(); ++i) {
            s += argNames.get(i) + ":" + argTypes.get(i) + ", ";
        }
        s += ")";
        return s;
    }
}